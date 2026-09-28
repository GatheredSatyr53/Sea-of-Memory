package com.seaofmemory.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.overtime.FrozenMobs;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Draws a mob frozen by the Overtime as an ice statue: its own model again, just over it, in translucent cracked ice,
 * with every animation held still.
 * Added to every living entity renderer, so a frozen zombie or cow looks as frozen as a villager.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class FrozenLayer extends RenderLayer<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
    private static final ContextKey<Boolean> FROZEN = new ContextKey<>(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "frozen"));
    private static final RenderType ICE = RenderTypes.entityTranslucent(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "textures/entity/frozen.png"));

    private FrozenLayer(RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, LivingEntityRenderState state, float yRot, float xRot) {
        if (!Boolean.TRUE.equals(state.getRenderData(FROZEN)) || state.isInvisible) {
            return;
        }
        collector.order(1).submitModel(getParentModel(), state, poseStack, ICE, lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0f), -1, null, state.outlineColor, null);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        // Applies to every subclass, so to every living entity renderer. The raw class needs its type spelled out.
        Class<? extends EntityRenderer<? extends LivingEntity, ? extends LivingEntityRenderState>> living = (Class) LivingEntityRenderer.class;
        event.<LivingEntity, LivingEntityRenderState>registerEntityModifier(living, (entity, state) -> {
            boolean frozen = FrozenMobs.isFrozen(entity);
            state.setRenderData(FROZEN, frozen);
            if (frozen) {
                // Every idle motion, walk cycle and keyframe animation runs off these; held still, the pose sets in ice.
                state.ageInTicks = 0;
                state.walkAnimationSpeed = 0;
            }
        });
    }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) {
                addTo(living);
            }
        }
    }

    // Layers are typed to their renderer's state and model; this one only needs what every living renderer has.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTo(LivingEntityRenderer renderer) {
        renderer.addLayer(new FrozenLayer(renderer));
    }
}
