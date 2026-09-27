package com.seaofmemory.entity;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(SeaOfMemory.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SnowPerson>> SNOW_PERSON = ENTITY_TYPES.registerEntityType(
            "snow_person", SnowPerson::new, MobCategory.MONSTER, builder -> builder
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.74f)
                    .clientTrackingRange(8)
                    .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<PlushHare>> PLUSH_HARE = ENTITY_TYPES.registerEntityType(
            "plush_hare", PlushHare::new, MobCategory.MONSTER, builder -> builder
                    .sized(1.4f, 2.0f)
                    .eyeHeight(1.65f)
                    .clientTrackingRange(10)
                    .notInPeaceful());

    public static final DeferredItem<SpawnEggItem> SNOW_PERSON_SPAWN_EGG = SeaOfMemory.ITEMS.registerItem(
            "snow_person_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(SNOW_PERSON.get()));
    public static final DeferredItem<SpawnEggItem> PLUSH_HARE_SPAWN_EGG = SeaOfMemory.ITEMS.registerItem(
            "plush_hare_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(PLUSH_HARE.get()));
    // The hare's one remaining eye, torn off its face.
    public static final DeferredItem<Item> BUTTON_EYE = SeaOfMemory.ITEMS.registerSimpleItem("button_eye");

    private ModEntities() {
    }

    @SubscribeEvent
    static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(SNOW_PERSON.get(), SnowPerson.createAttributes().build());
        event.put(PLUSH_HARE.get(), PlushHare.createAttributes().build());
    }
}
