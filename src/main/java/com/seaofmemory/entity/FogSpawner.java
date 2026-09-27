package com.seaofmemory.entity;

import java.util.Map;
import java.util.function.Predicate;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.fog.CognitiveFog;
import com.seaofmemory.sea.FogWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Brings projections out of the fog and makes them listen.
 * <ul>
 * <li>Snow people rise around players standing in critical fog, and anywhere in the fog world.</li>
 * <li>Rarely, in the fog world only and only inside a village, a plush hare comes out of the fog instead.</li>
 * <li>Loud noises draw every snow person within earshot, as Walt learned: a gun kills one and calls a hundred.</li>
 * </ul>
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogSpawner {
    private static final int INTERVAL = 40;
    private static final int SNOW_PEOPLE_CAP = 8;
    private static final double CAP_RADIUS = 48;
    private static final int SPAWN_ATTEMPTS = 2;
    // Roughly one hare per ten minutes spent near a village in the fog world, never two close together.
    // Only there: its fight is played out in silhouettes, and those only show in the fog reality.
    // And only in villages: it is a child's toy, it turns up where children used to live.
    private static final int HARE_CHANCE = 300;
    private static final int HARE_ATTEMPTS = 12;
    private static final double HARE_EXCLUSION_RADIUS = 128;
    // They come out of the dark, never out of a lit spot.
    private static final int MAX_SPAWN_LIGHT = 7;

    // How far, in blocks, snow people hear each kind of noise.
    private static final Map<ResourceKey<GameEvent>, Double> LOUDNESS = Map.of(
            GameEvent.EXPLODE.key(), 64.0,
            GameEvent.LIGHTNING_STRIKE.key(), 64.0,
            GameEvent.INSTRUMENT_PLAY.key(), 48.0,
            GameEvent.SHRIEK.key(), 48.0,
            GameEvent.PROJECTILE_SHOOT.key(), 32.0,
            GameEvent.NOTE_BLOCK_PLAY.key(), 24.0,
            GameEvent.BLOCK_DESTROY.key(), 10.0);

    private FogSpawner() {
    }

    @SubscribeEvent
    static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Double radius = event.getVanillaEvent().unwrapKey().map(LOUDNESS::get).orElse(null);
        if (radius == null) {
            return;
        }
        Vec3 pos = event.getEventPosition();
        BlockPos source = BlockPos.containing(pos);
        for (SnowPerson snowPerson : level.getEntitiesOfClass(SnowPerson.class, AABB.ofSize(pos, radius * 2, radius * 2, radius * 2))) {
            snowPerson.lure(source);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if ((level.dimension() == Level.OVERWORLD || FogWorld.is(level)) && level.getDifficulty() != Difficulty.PEACEFUL) {
                for (ServerPlayer player : level.players()) {
                    if (!player.isSpectator()) {
                        spawnAround(level, player);
                    }
                }
            }
        }
    }

    private static void spawnAround(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(CAP_RADIUS);
        int nearby = level.getEntitiesOfClass(SnowPerson.class, area).size();
        for (int i = nearby; i < Math.min(nearby + SPAWN_ATTEMPTS, SNOW_PEOPLE_CAP); i++) {
            trySpawn(level, player, ModEntities.SNOW_PERSON.get(), 16, 32, pos -> true);
        }
        if (FogWorld.is(level) && player.getRandom().nextInt(HARE_CHANCE) == 0
                && level.getEntitiesOfClass(PlushHare.class, player.getBoundingBox().inflate(HARE_EXCLUSION_RADIUS)).isEmpty()) {
            for (int i = 0; i < HARE_ATTEMPTS; i++) {
                if (trySpawn(level, player, ModEntities.PLUSH_HARE.get(), 24, 40, pos -> inVillage(level, pos))) {
                    break;
                }
            }
        }
    }

    /**
     * On one of a village's houses, streets or squares. The fog world shares the real world's seed,
     * so its villages stand where the real ones do.
     */
    private static boolean inVillage(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
    }

    private static boolean trySpawn(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int minDistance, int maxDistance,
            Predicate<BlockPos> where) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        double distance = Mth.nextDouble(player.getRandom(), minDistance, maxDistance);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return false;
        }
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        boolean foggy = FogWorld.is(level) || CognitiveFog.isCritical(CognitiveFog.densityAt(level, pos));
        // The heightmap counts water as solid, so pos is the air above it: the surface itself is the block below.
        boolean onWater = !level.getFluidState(pos.below()).isEmpty();
        if (!foggy || level.getBrightness(LightLayer.BLOCK, pos) > MAX_SPAWN_LIGHT || onWater || !where.test(pos)) {
            return false;
        }
        Mob mob = type.spawn(level, pos, EntitySpawnReason.EVENT);
        if (mob != null) {
            // Moulded out of the fog in front of whoever is watching.
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 20, 0.4, mob.getBbHeight() / 3, 0.4, 0.01);
        }
        return mob != null;
    }
}
