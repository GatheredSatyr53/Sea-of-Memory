package com.seaofmemory.client.entity;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.ModEntities;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class ModEntityRenderers {
    public static final ModelLayerLocation SNOW_PERSON = layer("snow_person");
    public static final ModelLayerLocation PLUSH_HARE = layer("plush_hare");

    private ModEntityRenderers() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, name), "main");
    }

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SNOW_PERSON, SnowPersonModel::createBodyLayer);
        event.registerLayerDefinition(PLUSH_HARE, PlushHareModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SNOW_PERSON.get(), SnowPersonRenderer::new);
        event.registerEntityRenderer(ModEntities.PLUSH_HARE.get(), PlushHareRenderer::new);
    }
}
