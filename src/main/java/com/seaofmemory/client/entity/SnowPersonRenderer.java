package com.seaofmemory.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.SnowPerson;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class SnowPersonRenderer extends MobRenderer<SnowPerson, SnowPersonRenderState, SnowPersonModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "textures/entity/snow_person.png");

    public SnowPersonRenderer(EntityRendererProvider.Context context) {
        super(context, new SnowPersonModel(context.bakeLayer(ModEntityRenderers.SNOW_PERSON)), 0.5f);
    }

    @Override
    public SnowPersonRenderState createRenderState() {
        return new SnowPersonRenderState();
    }

    @Override
    public void extractRenderState(SnowPerson snowPerson, SnowPersonRenderState state, float partialTicks) {
        super.extractRenderState(snowPerson, state, partialTicks);
        state.aggressive = snowPerson.isAggressive();
        state.seed = snowPerson.getId();
    }

    @Override
    protected void scale(SnowPersonRenderState state, PoseStack poseStack) {
        // A little too tall and too thin to pass for a person.
        poseStack.scale(0.95f, 1.08f, 0.95f);
    }

    @Override
    public Identifier getTextureLocation(SnowPersonRenderState state) {
        return TEXTURE;
    }
}
