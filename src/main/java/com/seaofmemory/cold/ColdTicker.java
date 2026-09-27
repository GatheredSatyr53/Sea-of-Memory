package com.seaofmemory.cold;

import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.SnowPerson;
import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Once a second, moves each player's cold towards a target level that depends on where they are.
 * <ul>
 * <li>Fog sets the level: nothing in thin fog, rising steeply towards critical density.
 * Cold biomes make it worse, warm ones soften it; darkness and being alone make it a little worse.</li>
 * <li>Snow people that can see the player add to it, whatever the fog.</li>
 * <li>Warmth sources, bright light and company (the threads between people) take it back down.</li>
 * </ul>
 * Out of the fog and away from snow people the target is zero, so cold fades by itself.
 * At high cold the player slows down; at the maximum they freeze.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class ColdTicker {
    private static final int INTERVAL = 20;

    // Fog below this density does not chill at all; at full density it alone can take cold to FOG_COLD.
    private static final float FOG_THRESHOLD = 0.3f;
    private static final float FOG_COLD = 70f;
    // Biome temperature where fog chills normally (plains); colder biomes chill more, warmer ones less.
    private static final float NEUTRAL_TEMPERATURE = 0.8f;
    private static final float TEMPERATURE_SENSITIVITY = 0.6f;
    private static final float MIN_TEMPERATURE_FACTOR = 0.5f;
    private static final float MAX_TEMPERATURE_FACTOR = 1.6f;
    private static final float DARKNESS_FACTOR = 1.2f;
    private static final float LONELINESS_FACTOR = 1.15f;

    private static final double SNOW_PERSON_RANGE = 12;
    private static final float COLD_PER_SNOW_PERSON = 10f;
    private static final float MAX_SNOW_PEOPLE_COLD = 40f;

    private static final float WARMTH_RELIEF = 40f;
    private static final float LIGHT_RELIEF = 15f;
    private static final float COMPANY_RELIEF = 10f;

    // Per-second speed of the drift towards the target: it creeps in slowly and lets go a bit faster.
    private static final float RISE_SPEED = 1f;
    private static final float FALL_SPEED = 1.5f;

    private static final int DARK_LIGHT_LEVEL = 7;
    private static final int BRIGHT_BLOCK_LIGHT = 12;
    private static final int WARMTH_RADIUS = 3;
    private static final double COMPANY_RADIUS = 8;

    // Effect thresholds.
    private static final float NUMB = 60f;
    private static final float FREEZING = 85f;

    private ColdTicker() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            Cold.set(player, 0f);
            return;
        }

        ServerLevel level = player.level();
        float cold = Cold.get(player);
        float target = target(level, player).target();
        float speed = target > cold ? RISE_SPEED * (float) Config.COLD_RISE_MULTIPLIER.getAsDouble() : FALL_SPEED;
        Cold.set(player, Mth.approach(cold, target, speed));
        applyEffects(level, player, Cold.get(player));
    }

    /**
     * What the player's surroundings add up to, and the cold level they pull the player towards.
     *
     * @param fogCold          cold from the fog alone, after the biome, darkness and loneliness
     * @param snowPeopleCold   cold from snow people watching
     * @param target           the resulting level, from 0 to {@link Cold#MAX}
     */
    record Target(float density, float temperatureFactor, boolean dark, boolean alone, float fogCold,
                  int watchingSnowPeople, float snowPeopleCold, boolean warmth, boolean bright, float target) {
    }

    static Target target(ServerLevel level, ServerPlayer player) {
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        boolean alone = !hasCompany(level, player);
        boolean dark = level.getMaxLocalRawBrightness(eyes) < DARK_LIGHT_LEVEL;
        boolean warmth = nearWarmth(level, player.blockPosition());
        boolean bright = level.getBrightness(LightLayer.BLOCK, eyes) >= BRIGHT_BLOCK_LIGHT;

        float density = CognitiveFog.densityAt(level, eyes);
        float temperatureFactor = temperatureFactor(level, eyes);
        float fog = Mth.clamp((density - FOG_THRESHOLD) / (1f - FOG_THRESHOLD), 0f, 1f);
        float fogCold = fog * fog * (3f - 2f * fog) * FOG_COLD * temperatureFactor;
        if (dark) {
            fogCold *= DARKNESS_FACTOR;
        }
        if (alone) {
            fogCold *= LONELINESS_FACTOR;
        }

        int watching = watchingSnowPeople(level, player);
        float snowPeopleCold = Math.min(watching * COLD_PER_SNOW_PERSON, MAX_SNOW_PEOPLE_COLD);

        float target = fogCold + snowPeopleCold;
        if (warmth) {
            target -= WARMTH_RELIEF;
        }
        if (bright) {
            target -= LIGHT_RELIEF;
        }
        if (!alone) {
            target -= COMPANY_RELIEF;
        }
        return new Target(density, temperatureFactor, dark, alone, fogCold, watching, snowPeopleCold, warmth, bright,
                Mth.clamp(target, 0f, Cold.MAX));
    }

    /**
     * How much the biome sharpens the fog's chill. Anywhere cold enough to snow counts as cold, whatever the biome.
     */
    private static float temperatureFactor(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float temperature = biome.getBaseTemperature();
        if (biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
            temperature = Math.min(temperature, 0.1f);
        }
        float factor = 1f + (NEUTRAL_TEMPERATURE - temperature) * TEMPERATURE_SENSITIVITY;
        return Mth.clamp(factor, MIN_TEMPERATURE_FACTOR, MAX_TEMPERATURE_FACTOR);
    }

    /**
     * Being watched by their blank faces is enough to feel it.
     */
    private static int watchingSnowPeople(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(SNOW_PERSON_RANGE);
        return level.getEntitiesOfClass(SnowPerson.class, area, snowPerson -> snowPerson.isAlive() && snowPerson.hasLineOfSight(player)).size();
    }

    private static boolean nearWarmth(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-WARMTH_RADIUS, -WARMTH_RADIUS, -WARMTH_RADIUS), center.offset(WARMTH_RADIUS, WARMTH_RADIUS, WARMTH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Cold.WARMTH_SOURCES) && state.getValueOrElse(BlockStateProperties.LIT, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Someone to hold on to: another living player, the player's own pet, or a villager.
     */
    private static boolean hasCompany(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(COMPANY_RADIUS);
        return !level.getEntitiesOfClass(Player.class, area, other -> other != player && other.isAlive() && !other.isSpectator()).isEmpty()
                || !level.getEntitiesOfClass(TamableAnimal.class, area, pet -> pet.isAlive() && pet.isOwnedBy(player)).isEmpty()
                || !level.getEntitiesOfClass(Villager.class, area, Villager::isAlive).isEmpty();
    }

    private static void applyEffects(ServerLevel level, ServerPlayer player, float cold) {
        if (cold >= NUMB) {
            // Hidden effects: the player should feel heavy, not read about it in the inventory.
            int amplifier = cold >= FREEZING ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, INTERVAL * 2, amplifier, true, false, false));
            if (cold >= FREEZING) {
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, INTERVAL * 2, 0, true, false, false));
            }
        }
        if (cold >= Cold.MAX && Config.COLD_FREEZE_DAMAGE.getAsBoolean()) {
            player.hurtServer(level, level.damageSources().freeze(), 1f);
        }
    }
}
