package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.sea.Absorption;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * At the end of absorption the screen goes white, then clears slowly on the other side.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class AbsorptionOverlay {
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "absorption");
    private static final float WHITEOUT_FROM = 0.6f;
    // Fast enough to follow the pull, slow enough that the whiteout lingers after the jump.
    private static final float SMOOTHING_STEP = 0.015f;
    private static final int FOG_WHITE = 0xDDE2E6;

    private static float previous;
    private static float current;

    private AbsorptionOverlay() {
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, AbsorptionOverlay::render);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        previous = current;
        current = player == null ? 0f : Mth.approach(current, Absorption.fraction(player), SMOOTHING_STEP);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float absorption = Mth.lerp(deltaTracker.getGameTimeDeltaPartialTick(false), previous, current);
        float t = Mth.clamp((absorption - WHITEOUT_FROM) / (1f - WHITEOUT_FROM), 0f, 1f);
        if (t <= 0f) {
            return;
        }
        float alpha = t * t * (3f - 2f * t);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(alpha, FOG_WHITE));
    }
}
