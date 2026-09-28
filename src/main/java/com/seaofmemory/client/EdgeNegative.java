package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.overtime.EdgePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Near the edge of the Overtime the world turns to its negative: the nearer, the deeper, until at the edge itself
 * it is fully inverted. Drawn under the HUD, so only the world turns.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class EdgeNegative {
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "overtime_edge");
    // Fraction change per client tick, so updates from the server blend in instead of jumping.
    private static final float SMOOTHING_STEP = 0.08f;

    private static float target;
    private static float previous;
    private static float current;

    private EdgeNegative() {
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(EdgePayload.TYPE, (payload, context) -> target = Mth.clamp(payload.nearness(), 0f, 1f));
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, EdgeNegative::render);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientOvertime.isActive()) {
            target = 0f;
        }
        previous = current;
        current = Mth.approach(current, target, SMOOTHING_STEP);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        target = previous = current = 0f;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float amount = Mth.lerp(deltaTracker.getGameTimeDeltaPartialTick(false), previous, current);
        if (amount <= 0f) {
            return;
        }
        // The inverting blend gives back colour * (1 - amount) + (1 - colour) * amount: a grey of this brightness
        // turns the picture that far towards its negative.
        int grey = Math.round(amount * 255);
        graphics.fill(RenderPipelines.GUI_INVERT, 0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(255, grey, grey, grey));
    }
}
