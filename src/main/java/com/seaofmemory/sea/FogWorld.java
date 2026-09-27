package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.List;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The fog itself: a separate dimension that copies the real world where people were lost in it.
 * Nobody leaves it on their own. The only ways out are a fog beacon or death, and the dead lose
 * everything they carried to the Sea of Memory.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogWorld {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "fog"));

    // Set while this class moves a player between worlds, so the travel block below lets it through.
    private static boolean transit;

    private FogWorld() {
    }

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }

    /**
     * Pulls the player into the fog at the same horizontal position.
     */
    public static boolean absorb(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel fog = server.getLevel(KEY);
        if (fog == null || is(player.level())) {
            return false;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        // The anchor has to exist before the chunks are generated, or the player lands in open sea.
        MemoryAnchors.add(server, x, z);
        return teleport(player, fog, surface(fog, x, z, Heightmap.Types.MOTION_BLOCKING));
    }

    /**
     * Brings the player back to the real world, standing next to the given position.
     */
    public static boolean release(ServerPlayer player, BlockPos near) {
        ServerLevel overworld = player.level().getServer().overworld();
        return teleport(player, overworld, surface(overworld, near.getX(), near.getZ(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
    }

    /**
     * Top of the column, generating the chunk first if needed.
     * Level#getHeight would silently answer with the bottom of the world for a chunk that is not loaded yet.
     */
    private static Vec3 surface(ServerLevel level, int x, int z, Heightmap.Types type) {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        if (is(level)) {
            // Whatever people built here has to be in place before we decide where the player stands.
            MemoryImprint.imprintNow(level.getServer(), level, chunk);
        }
        int y = chunk.getHeight(type, x & 15, z & 15) + 1;
        if (y <= level.getMinY()) {
            // An empty column (nothing to stand on at all): better above the sea than in the void.
            y = level.getSeaLevel() + 1;
        }
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static boolean teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
        transit = true;
        try {
            return player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING)) != null;
        } finally {
            transit = false;
        }
    }

    @SubscribeEvent
    static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        // Portals, end gateways, anything: the fog does not let go.
        if (!transit && is(event.getEntity().level())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !is(player.level())) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            items.add(drop.getItem());
        }
        SunkenBelongings.get(player.level().getServer()).sink(player.getUUID(), items);
        event.setCanceled(true);
    }
}
