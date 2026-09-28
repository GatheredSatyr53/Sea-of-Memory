package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.fog.CognitiveFog;
import com.seaofmemory.sea.Absorption;
import com.seaofmemory.sea.FogWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Draws the cognitive fog: pulls the fog planes in and washes the fog colour towards a milky grey.
 * Density is blended between neighbouring chunk centres and smoothed over time so chunk borders never show.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class CognitiveFogRenderer {
    // Visibility in blocks at full density.
    private static final float MIN_VISIBILITY = 6f;
    // Density change per client tick; 0 to 1 takes about five seconds.
    private static final float SMOOTHING_STEP = 0.01f;
    // How much of the original colour survives at full density.
    private static final float COLOR_BLEND = 0.85f;

    private static ClientLevel trackedLevel;
    private static float previousDensity;
    private static float currentDensity;

    private CognitiveFogRenderer() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            // New world or dimension: start clear instead of fading from stale fog.
            trackedLevel = level;
            previousDensity = currentDensity = 0f;
        }
        if (level == null || minecraft.player == null) {
            return;
        }
        previousDensity = currentDensity;
        float target = FogWorld.is(level) ? CognitiveFog.FOG_WORLD_DENSITY : sampleDensity(level, minecraft.player.position());
        if (ClientOvertime.isActive() && level.dimension() == Level.OVERWORLD) {
            target = Math.min(1f, target + CognitiveFog.OVERTIME_SHIFT);
        }
        // Being pulled into the fog closes it in completely.
        target += (1f - target) * Absorption.fraction(minecraft.player);
        currentDensity = Mth.approach(currentDensity, target, SMOOTHING_STEP);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC) {
            return;
        }
        float strength = strength((float) event.getPartialTick());
        if (strength <= 0f) {
            return;
        }
        FogData fog = event.getFogData();
        float end = Mth.lerp(strength, fog.environmentalEnd, MIN_VISIBILITY);
        fog.environmentalStart = Mth.lerp(strength, fog.environmentalStart, 0f);
        fog.environmentalEnd = end;
        fog.skyEnd = Math.min(fog.skyEnd, end);
        fog.cloudEnd = Math.min(fog.cloudEnd, end);
    }

    @SubscribeEvent
    static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float strength = strength((float) event.getPartialTick()) * COLOR_BLEND;
        if (strength <= 0f) {
            return;
        }
        // Keep the brightness of the scene (the fog is dark at night) but drain the colour out of it.
        float luminance = 0.3f * event.getRed() + 0.59f * event.getGreen() + 0.11f * event.getBlue();
        event.setRed(Mth.lerp(strength, event.getRed(), luminance + 0.02f));
        event.setGreen(Mth.lerp(strength, event.getGreen(), luminance + 0.04f));
        event.setBlue(Mth.lerp(strength, event.getBlue(), luminance + 0.06f));
    }

    /**
     * Visual strength of the fog. Eased so that moderate density is already clearly visible.
     */
    private static float strength(float partialTick) {
        float density = Mth.lerp(partialTick, previousDensity, currentDensity);
        float clear = 1f - density;
        return 1f - clear * clear * clear;
    }

    /**
     * Bilinear blend of the densities of the four chunk centres around the position.
     */
    private static float sampleDensity(ClientLevel level, Vec3 pos) {
        double u = (pos.x - 8) / 16;
        double v = (pos.z - 8) / 16;
        int x0 = Mth.floor(u);
        int z0 = Mth.floor(v);
        float fx = (float) (u - x0);
        float fz = (float) (v - z0);
        float top = Mth.lerp(fx, densityAt(level, x0, z0), densityAt(level, x0 + 1, z0));
        float bottom = Mth.lerp(fx, densityAt(level, x0, z0 + 1), densityAt(level, x0 + 1, z0 + 1));
        return Mth.lerp(fz, top, bottom);
    }

    private static float densityAt(ClientLevel level, int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunk == null ? 0f : CognitiveFog.getDensity(chunk);
    }
}
