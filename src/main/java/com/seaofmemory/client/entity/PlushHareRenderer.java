package com.seaofmemory.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.PlushHare;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class PlushHareRenderer extends MobRenderer<PlushHare, PlushHareRenderState, PlushHareModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "textures/entity/plush_hare.png");
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "textures/entity/plush_hare_eyes.png");
    // The toy is modelled at child size and blown up to something bigger than a person.
    private static final float SCALE = 1.5f;

    public PlushHareRenderer(EntityRendererProvider.Context context) {
        super(context, new PlushHareModel(context.bakeLayer(ModEntityRenderers.PLUSH_HARE)), 0.9f);
        addLayer(new SocketLayer(this));
    }

    @Override
    public PlushHareRenderState createRenderState() {
        return new PlushHareRenderState();
    }

    @Override
    public void extractRenderState(PlushHare hare, PlushHareRenderState state, float partialTicks) {
        super.extractRenderState(hare, state, partialTicks);
        state.transformed = hare.isTransformed();
    }

    @Override
    protected void scale(PlushHareRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(PlushHareRenderState state) {
        return TEXTURE;
    }

    /**
     * The empty eye socket glowing red once the hare has torn open.
     */
    private static final class SocketLayer extends EyesLayer<PlushHareRenderState, PlushHareModel> {
        private static final RenderType GLOW = RenderTypes.eyes(EYES);

        SocketLayer(RenderLayerParent<PlushHareRenderState, PlushHareModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PlushHareRenderState state, float yRot, float xRot) {
            if (state.transformed) {
                super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
            }
        }

        @Override
        public RenderType renderType() {
            return GLOW;
        }
    }
}
