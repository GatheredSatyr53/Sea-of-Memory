package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sounds that only the freezing player hears. Played locally, so in multiplayer
 * the person next to you hears nothing.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class ColdHallucinations {
    // Cold fraction where phantom sounds start, and where the player starts hearing their own heart.
    private static final float SOUNDS_FROM = 0.5f;
    private static final float HEARTBEAT_FROM = 0.75f;
    // Chance per tick at full cold; roughly one phantom sound every ten seconds.
    private static final float MAX_CHANCE = 1f / 200f;
    private static final int FOOTSTEPS = 3;
    private static final int FOOTSTEP_GAP = 7;

    private static int heartbeatCooldown;
    // Phantom footsteps are played one by one, walking up behind the player.
    private static int footstepsLeft;
    private static int footstepDelay;
    private static SoundEvent footstepSound;
    private static Vec3 footstepFrom;
    private static Vec3 footstepStep;

    private ColdHallucinations() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) {
            footstepsLeft = 0;
            return;
        }
        float cold = ColdOverlay.smoothed(1f);
        RandomSource random = player.getRandom();

        tickFootsteps(level);

        if (cold >= HEARTBEAT_FROM && --heartbeatCooldown <= 0) {
            // Beats faster and louder the colder it gets.
            float intensity = (cold - HEARTBEAT_FROM) / (1f - HEARTBEAT_FROM);
            heartbeatCooldown = Mth.floor(Mth.lerp(intensity, 30, 16));
            playNear(level, player.position(), SoundEvents.WARDEN_HEARTBEAT, 0.4f + 0.4f * intensity, 1f, random);
        }

        if (cold < SOUNDS_FROM || footstepsLeft > 0) {
            return;
        }
        float chance = MAX_CHANCE * (cold - SOUNDS_FROM) / (1f - SOUNDS_FROM);
        if (random.nextFloat() >= chance) {
            return;
        }
        switch (random.nextInt(4)) {
            case 0 -> startFootsteps(player, random);
            case 1 -> playNear(level, behind(player, 4 + random.nextInt(4)), SoundEvents.WOODEN_DOOR_OPEN, 0.6f, 0.8f, random);
            case 2 -> playNear(level, player.getEyePosition(), SoundEvents.PLAYER_BREATH, 0.5f, 0.7f, random);
            default -> playNear(level, behind(player, 8), SoundEvents.AMBIENT_CAVE.value(), 0.7f, 1f, random);
        }
    }

    private static void startFootsteps(LocalPlayer player, RandomSource random) {
        SoundEvent[] surfaces = {SoundEvents.SNOW_STEP, SoundEvents.GRAVEL_STEP, SoundEvents.WOOD_STEP, SoundEvents.STONE_STEP};
        footstepSound = surfaces[random.nextInt(surfaces.length)];
        footstepFrom = behind(player, 7);
        footstepStep = player.position().subtract(footstepFrom).normalize();
        footstepsLeft = FOOTSTEPS;
        footstepDelay = 0;
    }

    private static void tickFootsteps(ClientLevel level) {
        if (footstepsLeft <= 0 || --footstepDelay > 0) {
            return;
        }
        playNear(level, footstepFrom, footstepSound, 0.5f, 0.9f, level.getRandom());
        footstepFrom = footstepFrom.add(footstepStep);
        footstepDelay = FOOTSTEP_GAP;
        footstepsLeft--;
    }

    private static Vec3 behind(LocalPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        return player.position().subtract(flat.normalize().scale(distance));
    }

    private static void playNear(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch, RandomSource random) {
        level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, pitch + (random.nextFloat() - 0.5f) * 0.1f, false);
    }
}
