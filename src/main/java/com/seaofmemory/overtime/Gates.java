package com.seaofmemory.overtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.ModEntities;
import com.seaofmemory.entity.SnowPerson;
import com.seaofmemory.sea.FogWorld;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The Gates from "Овертайм". In a settlement by a pond ({@link GateVillage}) stands the ruin of an old bridge. During the Overtime
 * it pulls itself together: the deck closes over the water, detransmogrifier lamps stand along its rails, and a black
 * clock rises over its left shore pillar. Out of the fog, wave after wave, the projections come across.
 * The lamps are dark; lit, they slow the crowd down, but wake the frozen around them too.
 * <p>
 * Every wave driven back without one of them reaching the clock leaves something at the clock. Every one that does
 * reach it costs the Gates some of their strength and goes on to hunt the frozen villagers; when the strength runs
 * out the Gates fall. When they fall, or when the world breathes out, it is all a ruin again.
 * <p>
 * The ruins are generated with the settlement; the Gates take note of them when a player first comes near.
 * While the Gates stand, nothing of them can be broken or carried off.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class Gates extends SavedData {
    private static final int SCAN_INTERVAL = 200;
    private static final int PORTAL_INTERVAL = 4;
    // How far ahead along the bridge a snow person of a wave is sent each second.
    private static final double STEP = 7;
    // How far around each player, in chunks, settlements are looked for.
    private static final int SCAN_CHUNKS = 8;
    // The first wave comes soon after the Gates rise, then one every half a minute.
    private static final int FIRST_WAVE_DELAY = 200;
    private static final int WAVE_INTERVAL = 600;
    private static final int MAX_WAVE_SIZE = 8;
    private static final int STRENGTH = 5;
    // Reached the clock: the last step stops up to two blocks short of the deck by it, which is two blocks from the clock.
    static final double BREACH_DISTANCE = 3.5;
    private static final double STATUE_SEARCH = 64;
    private static final ResourceKey<LootTable> WAVE_REWARD = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "gameplay/gate_wave"));

    /**
     * A block that the whole Gates replaced, to put back when they turn to ruin again.
     */
    private record Replaced(BlockPos pos, BlockState state) {
        static final Codec<Replaced> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Replaced::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Replaced::state)
        ).apply(i, Replaced::new));
    }

    private static final class Wave {
        static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("index").forGetter(w -> w.index),
                UUIDUtil.CODEC.listOf().fieldOf("ghosts").forGetter(w -> w.ghosts),
                Codec.INT.optionalFieldOf("size", 0).forGetter(w -> w.size),
                Codec.INT.optionalFieldOf("killed", 0).forGetter(w -> w.killed),
                Codec.INT.optionalFieldOf("to_come", 0).forGetter(w -> w.toCome)
        ).apply(i, Wave::new));

        final int index;
        // Those still out there.
        final List<UUID> ghosts;
        // How many came out, and how many of them were killed: the reward goes by the share.
        final int size;
        int killed;
        // Still in the fog: they come out of the arch one at a time.
        int toCome;

        Wave(int index, List<UUID> ghosts, int size, int killed, int toCome) {
            this.index = index;
            this.ghosts = new ArrayList<>(ghosts);
            this.size = Math.max(size, ghosts.size());
            this.killed = killed;
            this.toCome = toCome;
        }
    }

    /**
     * The Gates standing whole, during the Overtime.
     */
    private static final class Run {
        static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("strength").forGetter(r -> r.strength),
                Codec.INT.fieldOf("wave").forGetter(r -> r.wave),
                Codec.LONG.fieldOf("next_wave_at").forGetter(r -> r.nextWaveAt),
                Wave.CODEC.listOf().fieldOf("waves").forGetter(r -> r.waves),
                Replaced.CODEC.listOf().fieldOf("replaced").forGetter(r -> r.replaced)
        ).apply(i, Run::new));

        int strength;
        int wave;
        long nextWaveAt;
        final List<Wave> waves;
        final List<Replaced> replaced;

        Run(int strength, int wave, long nextWaveAt, List<Wave> waves, List<Replaced> replaced) {
            this.strength = strength;
            this.wave = wave;
            this.nextWaveAt = nextWaveAt;
            this.waves = new ArrayList<>(waves);
            this.replaced = new ArrayList<>(replaced);
        }
    }

    private static final class Site {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
                BlockPos.CODEC.fieldOf("clock").forGetter(s -> s.clock),
                BlockPos.CODEC.fieldOf("far_end").forGetter(s -> s.farEnd),
                GateLayout.Part.CODEC.listOf().fieldOf("parts").forGetter(s -> s.parts),
                Run.CODEC.optionalFieldOf("run").forGetter(s -> Optional.ofNullable(s.run)),
                Codec.BOOL.optionalFieldOf("fallen", false).forGetter(s -> s.fallen)
        ).apply(i, Site::new));

        // The settlement's shore point, which tells one settlement from another.
        final BlockPos origin;
        final BlockPos clock;
        final BlockPos farEnd;
        final List<GateLayout.Part> parts;
        Run run;

        /**
         * The middle of the deck by the clock: where the waves are headed, and where the Gates leave what they give.
         */
        BlockPos beforeClock() {
            return clock.relative(out().getClockWise(), 2);
        }

        Direction out() {
            return Direction.getApproximateNearest(farEnd.getX() - clock.getX(), 0, farEnd.getZ() - clock.getZ());
        }

        /**
         * How far a position is past the arch (negative: on the bridge's side) and how far to the side of its middle.
         */
        double along(Vec3 pos) {
            Direction out = out();
            return (pos.x - (farEnd.getX() + 0.5)) * out.getStepX() + (pos.z - (farEnd.getZ() + 0.5)) * out.getStepZ();
        }

        double across(Vec3 pos) {
            Direction side = out().getClockWise();
            return (pos.x - (farEnd.getX() + 0.5)) * side.getStepX() + (pos.z - (farEnd.getZ() + 0.5)) * side.getStepZ();
        }

        /**
         * Gone through the arch, out into the fog.
         */
        boolean throughArch(Entity entity) {
            Vec3 pos = entity.position();
            return along(pos) > 0.5 && along(pos) < 3 && Math.abs(across(pos)) < GateLayout.ARCH_HALF_WIDTH
                    && pos.y >= farEnd.getY() - 1 && pos.y <= farEnd.getY() + GateLayout.ARCH_HEIGHT;
        }
        // Fell during this Overtime: it stays a ruin until the next one.
        boolean fallen;

        Site(BlockPos origin, BlockPos clock, BlockPos farEnd, List<GateLayout.Part> parts, Optional<Run> run, boolean fallen) {
            this.origin = origin;
            this.clock = clock;
            this.farEnd = farEnd;
            this.parts = List.copyOf(parts);
            this.run = run.orElse(null);
            this.fallen = fallen;
        }
    }

    private static final Codec<Gates> CODEC = RecordCodecBuilder.create(i -> i.group(
            Site.CODEC.listOf().fieldOf("sites").forGetter(g -> g.sites)
    ).apply(i, Gates::new));
    private static final SavedDataType<Gates> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "gates"), Gates::new, CODEC);

    private final List<Site> sites;
    private Gates() {
        this(List.of());
    }

    private Gates(List<Site> sites) {
        this.sites = new ArrayList<>(sites);
    }

    private static Gates get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Every tick of the real world: finds new ruins now and then, and runs the Gates during the Overtime.
     */
    static void tick(ServerLevel overworld, boolean overtime) {
        Gates data = get(overworld.getServer());
        long time = overworld.getGameTime();
        if (time % SCAN_INTERVAL == 0) {
            data.findSettlements(overworld);
        }
        if (overtime && time % PORTAL_INTERVAL == 0) {
            for (Site site : data.sites) {
                if (site.run != null && overworld.isLoaded(site.farEnd)) {
                    portal(overworld, site);
                }
            }
        }
        if (overtime && time % 20 == 0) {
            for (Site site : List.copyOf(data.sites)) {
                if (!overworld.isLoaded(site.clock)) {
                    continue;
                }
                if (site.run == null && !site.fallen) {
                    data.makeWhole(overworld, site);
                }
                if (site.run != null) {
                    data.hold(overworld, site);
                }
            }
            data.setDirty();
        }
    }

    // ---- The ruins ----

    /**
     * Takes note of the Gates in settlements that have generated around the players. Their ruins are already in the
     * world; this is only so the Gates know where they are when the Overtime comes.
     */
    private void findSettlements(ServerLevel level) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(GateVillage.KEY);
        if (structure == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            ChunkPos center = ChunkPos.containing(player.blockPosition());
            for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
                for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
                    StructureStart start = chunk == null ? null : chunk.getStartForStructure(structure);
                    if (start == null || !start.isValid()) {
                        continue;
                    }
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece instanceof GateVillage.Piece village) {
                            note(level, village.layout());
                        }
                    }
                }
            }
        }
    }

    private void note(ServerLevel level, GateLayout layout) {
        BlockPos origin = layout.at(0, 0, 0);
        if (sites.stream().anyMatch(s -> s.origin.equals(origin))) {
            return;
        }
        Site site = new Site(origin, layout.clock(), layout.farEnd(), layout.parts(), Optional.empty(), false);
        sites.add(site);
        setDirty();
        SeaOfMemory.LOGGER.info("Gates by the settlement at {}; the clock stands at {}", origin, site.clock);
    }

    /**
     * Raises the Gates of one settlement at once, as the Overtime would. For the game tests.
     */
    static void raise(ServerLevel level, GateLayout layout) {
        Gates data = get(level.getServer());
        data.note(level, layout);
        BlockPos origin = layout.at(0, 0, 0);
        data.sites.stream().filter(s -> s.origin.equals(origin) && s.run == null).findFirst().ifPresent(site -> data.makeWhole(level, site));
    }

    /**
     * Sends a snow person on across the bridge, as the Gates do with their waves every second. For the game tests.
     */
    static void drive(ServerLevel level, GateLayout layout, SnowPerson ghost) {
        BlockPos origin = layout.at(0, 0, 0);
        get(level.getServer()).sites.stream().filter(s -> s.origin.equals(origin)).findFirst().ifPresent(site -> {
            if (ghost.getTarget() == null) {
                ghost.march(nextStep(site, ghost));
            }
        });
    }

    /**
     * Turns the Gates of one settlement back into the ruin, as the end of the Overtime would. For the game tests.
     */
    static void lower(ServerLevel level, GateLayout layout) {
        BlockPos origin = layout.at(0, 0, 0);
        get(level.getServer()).sites.stream().filter(s -> s.origin.equals(origin)).findFirst().ifPresent(site -> ruin(level, site));
    }

    // ---- The Gates made whole ----

    private void makeWhole(ServerLevel level, Site site) {
        List<Replaced> replaced = new ArrayList<>();
        for (GateLayout.Part part : site.parts) {
            BlockState current = level.getBlockState(part.pos());
            if (current.equals(part.whole())) {
                continue;
            }
            // Only water, air and the ruin's own blocks give way; never anything people built.
            boolean yields = current.canBeReplaced() || current.getFluidState().is(FluidTags.WATER)
                    || part.ruin().map(current::equals).orElse(false);
            if (yields) {
                replaced.add(new Replaced(part.pos(), current));
                level.setBlock(part.pos(), part.whole(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        // All of it stands at once; then rails, panes and fences join up with what is now next to them.
        for (Replaced r : replaced) {
            BlockState state = level.getBlockState(r.pos());
            BlockState joined = Block.updateFromNeighbourShapes(state, level, r.pos());
            if (joined != state) {
                level.setBlock(r.pos(), joined, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        for (Replaced r : replaced) {
            BlockState state = level.getBlockState(r.pos());
            state.updateNeighbourShapes(level, r.pos(), Block.UPDATE_ALL);
            level.updateNeighborsAt(r.pos(), state.getBlock());
        }
        site.run = new Run(STRENGTH, 0, level.getGameTime() + FIRST_WAVE_DELAY, List.of(), replaced);
        level.playSound(null, site.clock, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2f, 0.5f);
    }

    /**
     * Puts back what the whole Gates replaced: the ruin, water and air.
     */
    private static void ruin(ServerLevel level, Site site) {
        if (site.run == null) {
            return;
        }
        // All of it at once, with no updates in between: otherwise a lantern whose deck has already turned back into water
        // would lose its footing and fall off as an item. The Gates do not come apart; they were never there.
        for (Replaced replaced : site.run.replaced) {
            level.setBlock(replaced.pos(), replaced.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        // Then the world around catches up: water flows, whatever leaned on the bridge feels it gone.
        for (Replaced replaced : site.run.replaced) {
            BlockState state = level.getBlockState(replaced.pos());
            state.updateNeighbourShapes(level, replaced.pos(), Block.UPDATE_ALL);
            level.updateNeighborsAt(replaced.pos(), state.getBlock());
            if (!state.getFluidState().isEmpty()) {
                level.scheduleTick(replaced.pos(), state.getFluidState().getType(), state.getFluidState().getType().getTickDelay(level));
            }
        }
        site.run = null;
        level.playSound(null, site.clock, SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
    }

    private void hold(ServerLevel level, Site site) {
        Run run = site.run;
        if (level.getGameTime() >= run.nextWaveAt) {
            sendWave(level, site);
        }
        Vec3 clock = Vec3.atCenterOf(site.clock);
        for (Wave wave : List.copyOf(run.waves)) {
            if (wave.toCome > 0) {
                comeOut(level, site, wave);
            }
            for (UUID id : List.copyOf(wave.ghosts)) {
                Entity entity = level.getEntity(id);
                if (!(entity instanceof SnowPerson ghost) || !ghost.isAlive()) {
                    wave.ghosts.remove(id);
                } else if (ghost.position().distanceTo(clock) <= BREACH_DISTANCE) {
                    wave.ghosts.remove(id);
                    breach(level, run, ghost, site);
                    if (run.strength <= 0) {
                        fall(level, site);
                        return;
                    }
                } else if (ghost.getTarget() == null) {
                    // Across the bridge, towards the clock, a few steps at a time.
                    ghost.march(nextStep(site, ghost));
                }
            }
            if (wave.ghosts.isEmpty() && wave.toCome <= 0) {
                run.waves.remove(wave);
                reward(level, site, wave);
            }
        }
    }

    /**
     * Where a snow person of a wave heads next: a spot on the middle of the deck a few steps nearer the clock than it is,
     * and at last the deck by the clock. Short hops along the bridge, not one long way to the clock itself: the clock is
     * a solid block, there is no path onto it, and one that finds no path only mills about in the arch.
     */
    private static BlockPos nextStep(Site site, SnowPerson ghost) {
        // Each keeps to a lane of its own across the deck and has a stride of its own, so the crowd straggles
        // instead of marching abreast in step.
        UUID id = ghost.getUUID();
        int lane = Math.floorMod(id.getMostSignificantBits(), 3) - 1;
        double stride = STEP + Math.floorMod(id.getLeastSignificantBits(), 4) + ghost.getRandom().nextDouble() * 2;
        Direction out = site.out();
        Direction side = out.getClockWise();
        BlockPos goal = site.beforeClock().relative(side, lane);
        double next = site.along(ghost.position()) - stride;
        if (next <= site.along(Vec3.atCenterOf(goal))) {
            return goal;
        }
        return BlockPos.containing(site.farEnd.getX() + 0.5 + out.getStepX() * next + side.getStepX() * lane, site.farEnd.getY(),
                site.farEnd.getZ() + 0.5 + out.getStepZ() * next + side.getStepZ() * lane);
    }

    /**
     * The arch leads into the fog: whoever goes through it is taken there. It is the only way back for the waves too;
     * one that fell off the bridge is still in the world, and still after the clock.
     */
    private static void portal(ServerLevel level, Site site) {
        AABB around = new AABB(site.farEnd).inflate(GateLayout.ARCH_HALF_WIDTH + 3, GateLayout.ARCH_HEIGHT + 1, GateLayout.ARCH_HALF_WIDTH + 3);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around, site::throughArch)) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (site.run != null) {
                for (Wave wave : site.run.waves) {
                    wave.ghosts.remove(entity.getUUID());
                }
            }
            intoFog(level, entity);
        }
    }

    private static void intoFog(ServerLevel level, Entity entity) {
        level.playSound(null, entity.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1f, 0.6f);
        if (entity instanceof SnowPerson ghost) {
            ghost.setTarget(null);
        }
        FogWorld.absorb(entity);
    }

    private static void sendWave(ServerLevel level, Site site) {
        Run run = site.run;
        int size = Math.min(2 + run.wave / 2, MAX_WAVE_SIZE);
        // They come out one at a time, a second apart (see comeOut).
        Wave wave = new Wave(run.wave, List.of(), size, 0, size);
        run.waves.add(wave);
        // Something very heavy, dropped somewhere in the fog.
        level.playSound(null, site.farEnd, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.3f);
        run.wave++;
        run.nextWaveAt = level.getGameTime() + WAVE_INTERVAL;
    }

    /**
     * The next of a wave steps out of the arch, somewhere across its width, as out of a wall of fog.
     */
    private static void comeOut(ServerLevel level, Site site, Wave wave) {
        wave.toCome--;
        int across = level.getRandom().nextInt(3) - 1;
        BlockPos at = site.farEnd.relative(site.out().getOpposite()).relative(site.out().getClockWise(), across);
        SnowPerson ghost = ModEntities.SNOW_PERSON.get().spawn(level, at, EntitySpawnReason.EVENT);
        if (ghost == null) {
            return;
        }
        ghost.setPersistenceRequired();
        ghost.march(nextStep(site, ghost));
        wave.ghosts.add(ghost.getUUID());
        level.sendParticles(ParticleTypes.CLOUD, ghost.getX(), ghost.getY() + 1.2, ghost.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
    }

    /**
     * One got through. It costs the Gates their strength and goes after the frozen.
     */
    private static void breach(ServerLevel level, Run run, SnowPerson ghost, Site site) {
        // Through: no longer one of a crowd on the march, free to go after whom it likes.
        ghost.stopMarching();
        run.strength--;
        level.playSound(null, site.clock, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
        level.sendParticles(ParticleTypes.SOUL, site.clock.getX() + 0.5, site.clock.getY() + 1.5, site.clock.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
        level.getEntitiesOfClass(Villager.class, ghost.getBoundingBox().inflate(STATUE_SEARCH), FrozenMobs::isFrozen).stream()
                .min(Comparator.comparingDouble(ghost::distanceToSqr))
                .ifPresent(ghost::setTarget);
    }

    /**
     * When a wave is over, something is left at the clock for every one of it that was killed: the whole reward for a
     * wave killed to the last, a share of it for the rest; more for later waves. Those that reached the clock or went
     * back into the fog count for nothing.
     */
    private static void reward(ServerLevel level, Site site, Wave wave) {
        if (wave.killed <= 0 || wave.size <= 0) {
            return;
        }
        float share = Math.min(1f, wave.killed / (float) wave.size);
        Vec3 at = Vec3.atBottomCenterOf(site.beforeClock()).add(0, 0.2, 0);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(WAVE_REWARD);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, at).create(LootContextParamSets.CHEST);
        float rolls = (1 + wave.index / 3) * share;
        int whole = (int) rolls;
        // The part of a roll left over is a chance at one more.
        if (level.getRandom().nextFloat() < rolls - whole) {
            whole++;
        }
        for (int roll = 0; roll < whole; roll++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                // Laid on the deck, not flung about: over the rails it would go into the water.
                ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0.1, 0);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
        ExperienceOrb.award(level, at, Math.max(1, Math.round((5 + wave.index * 3) * share)));
        level.playSound(null, site.clock, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1.2f);
    }

    /**
     * Counts a snow person of a wave killed. Watched here rather than on the Gates' own rounds, which come once
     * a second: by then the body may be gone, and it could not be told from one that simply went away.
     */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SnowPerson ghost) || !(ghost.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Gates data = get(level.getServer());
        for (Site site : data.sites) {
            if (site.run == null) {
                continue;
            }
            for (Wave wave : site.run.waves) {
                if (wave.ghosts.remove(ghost.getUUID())) {
                    wave.killed++;
                    data.setDirty();
                    return;
                }
            }
        }
    }

    private void fall(ServerLevel level, Site site) {
        ruin(level, site);
        site.fallen = true;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(site.clock).inflate(64))) {
            player.sendOverlayMessage(Component.translatable("seaofmemory.gates.fallen"));
        }
        SeaOfMemory.LOGGER.info("The Gates at {} fell", site.clock);
    }

    /**
     * The world breathes out: whatever stood whole is a ruin again, and the fallen may rise next time.
     */
    static void onOvertimeEnd(ServerLevel level) {
        Gates data = get(level.getServer());
        for (Site site : data.sites) {
            if (site.run != null) {
                for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(site.clock).inflate(64))) {
                    player.sendOverlayMessage(Component.translatable("seaofmemory.gates.held"));
                }
            }
            ruin(level, site);
            site.fallen = false;
        }
        data.setDirty();
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Site site : get(level.getServer()).sites) {
            if (site.run != null && site.run.replaced.stream().anyMatch(r -> r.pos().equals(pos))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * What the clock tells whoever asks it during the Overtime.
     */
    static Component status(ServerLevel level, BlockPos clock) {
        for (Site site : get(level.getServer()).sites) {
            if (site.clock.equals(clock) && site.run != null) {
                long seconds = Math.max(0, site.run.nextWaveAt - level.getGameTime()) / 20;
                return Component.translatable("seaofmemory.gates.status", seconds, site.run.strength, STRENGTH);
            }
        }
        return Component.translatable("seaofmemory.gates.silent");
    }
}
