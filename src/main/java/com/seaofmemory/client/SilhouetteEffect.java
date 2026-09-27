package com.seaofmemory.client;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.scene.SilhouettePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client side of the silhouette scene. It hard-cuts in and out, like the scene changes it imitates.
 * <p>
 * The effect is applied right after the level is drawn. The vanilla post-effect slot is no use here:
 * it runs after the hand, and the depth buffer is wiped before the hand is drawn, so the shader would
 * only see the hand's depth. Applied here, the shader sees the world's depth and the hand is drawn over it as usual.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class SilhouetteEffect {
    private static final Identifier EFFECT = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "silhouette");
    // Keeps the intermediate target alive between frames instead of allocating one every frame.
    private static final CrossFrameResourcePool RESOURCES = new CrossFrameResourcePool(3);

    // Ticks left; SilhouettePayload.UNTIL_STOPPED while on indefinitely, OFF when inactive.
    private static int remaining = SilhouettePayload.OFF;

    private SilhouetteEffect() {
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(SilhouettePayload.TYPE, (payload, context) -> remaining = payload.ticks());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            // Left the world: a scene never carries over.
            remaining = SilhouettePayload.OFF;
        } else if (remaining > 0) {
            remaining--;
        }
    }

    @SubscribeEvent
    static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        if (remaining == SilhouettePayload.OFF) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PostChain chain = minecraft.getShaderManager().getPostChain(EFFECT, LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) {
            chain.process(minecraft.gameRenderer.mainRenderTarget(), RESOURCES);
            RESOURCES.endFrame();
        }
    }
}
