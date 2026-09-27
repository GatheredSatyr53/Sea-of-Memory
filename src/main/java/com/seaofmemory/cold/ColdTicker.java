package com.seaofmemory.cold;

import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Once a second, moves each player's cold up or down.
 * <ul>
 * <li>Fog makes it rise, much faster at critical density; darkness and being alone make that worse.</li>
 * <li>Warmth sources, bright light and company (the threads between people) push it back down.</li>
 * </ul>
 * At high cold the player slows down; at the maximum they freeze.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class ColdTicker {
    private static final int INTERVAL = 20;

    // Per-second rates, in cold points.
    private static final float FOG_RATE = 0.35f;
    private static final float CRITICAL_FOG_BONUS = 0.25f;
    private static final float DARKNESS_MULTIPLIER = 1.5f;
    private static final float LONELINESS_MULTIPLIER = 1.25f;
    private static final float WARMTH_RELIEF = 2.5f;
    private static final float LIGHT_RELIEF = 0.5f;
    private static final float COMPANY_RELIEF = 0.5f;
    private static final float CLEAR_AIR_RELIEF = 0.3f;

    private static final float CLEAR_AIR_DENSITY = 0.05f;
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
        float cold = Cold.get(player) + change(level, player);
        Cold.set(player, cold);
        applyEffects(level, player, Cold.get(player));
    }

    private static float change(ServerLevel level, ServerPlayer player) {
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        float density = CognitiveFog.getDensity(level.getChunkAt(eyes));
        boolean company = hasCompany(level, player);

        float rise = density * FOG_RATE;
        if (CognitiveFog.isCritical(density)) {
            rise += CRITICAL_FOG_BONUS;
        }
        if (level.getMaxLocalRawBrightness(eyes) < DARK_LIGHT_LEVEL) {
            rise *= DARKNESS_MULTIPLIER;
        }
        if (!company) {
            rise *= LONELINESS_MULTIPLIER;
        }
        rise *= (float) Config.COLD_RISE_MULTIPLIER.getAsDouble();

        float relief = 0f;
        if (nearWarmth(level, player.blockPosition())) {
            relief += WARMTH_RELIEF;
        }
        if (level.getBrightness(LightLayer.BLOCK, eyes) >= BRIGHT_BLOCK_LIGHT) {
            relief += LIGHT_RELIEF;
        }
        if (company) {
            relief += COMPANY_RELIEF;
        }
        if (density < CLEAR_AIR_DENSITY) {
            relief += CLEAR_AIR_RELIEF;
        }
        return rise - relief;
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
