package com.seaofmemory.sea;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Only projections live in the fog. The fog world copies the real world's biomes, and with them its creatures;
 * none of those belong here.
 * <ul>
 * <li>No natural spawning: our own mobs come out of the fog through FogSpawner instead.</li>
 * <li>No animals at chunk generation (disable_mob_generation in the fog world's noise settings).</li>
 * <li>Anything else that turns up, from a village's villagers to a dungeon spawner's zombies or a spawn egg,
 * is turned away unless it is one of ours, or someone the fog took from the real world (see Absorption).</li>
 * </ul>
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogWorldSpawns {
    private FogWorldSpawns() {
    }

    @SubscribeEvent
    static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getLevel() instanceof Level level && FogWorld.is(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        // Server only: the server decides who exists. Entity tags never reach the client, so there
        // a lost villager looks like any other and would be refused, left walking about unseen.
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && FogWorld.is(event.getLevel()) && !isOurs(mob) && !mob.entityTags().contains(FogWorld.LOST_TAG)) {
            event.setCanceled(true);
        }
    }

    private static boolean isOurs(Mob mob) {
        return SeaOfMemory.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace());
    }
}
