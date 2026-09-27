package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.cold.Cold;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
 * The cold has no bar: frost creeps in from the edges of the screen instead.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class ColdOverlay {
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "cold");
    private static final Identifier FROST = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    // Below this much cold the frost is not drawn at all.
    private static final float VISIBLE_FROM = 0.2f;
    // Fraction change per client tick, so network updates once a second fade in instead of jumping.
    private static final float SMOOTHING_STEP = 0.01f;

    private static float previous;
    private static float current;

    private ColdOverlay() {
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, ColdOverlay::render);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        previous = current;
        current = player == null ? 0f : Mth.approach(current, Cold.fraction(player), SMOOTHING_STEP);
    }

    /**
     * Smoothed cold fraction for the current frame; shared with other client effects.
     */
    static float smoothed(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float cold = smoothed(deltaTracker.getGameTimeDeltaPartialTick(false));
        float alpha = Mth.clamp((cold - VISIBLE_FROM) / (1f - VISIBLE_FROM), 0f, 1f);
        if (alpha <= 0f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0f, 0f, width, height, width, height, ARGB.white(alpha));
    }
}
