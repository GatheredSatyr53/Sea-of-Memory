package com.seaofmemory.sea;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * "Эвридика": pour transmogrifier concentrate into hot water in a closed room and the fog comes for you, and it
 * remembers what you lost to it. Whoever it takes soon after finds their sunken belongings waiting in chests
 * where they died in the fog. The fog does not stay in the room: it seeps into the chunks around it.
 */
public final class EurydiceRitual {
    // How long after the ritual the fog still carries your memories when it takes you.
    private static final long CALLING_TICKS = 5 * 60 * 20;
    private static final int SEEP_RADIUS = 1;
    private static final int CHEST_SIZE = 27;
    private static final int MAX_CLIMB = 24;

    // Server lifetime only: a ritual interrupted by a restart simply has to be done again.
    private static final Map<UUID, Long> CALLING = new HashMap<>();

    private EurydiceRitual() {
    }

    /**
     * Fills the chunks around the bath with critical fog and lets it know whose memories to carry.
     */
    public static void begin(ServerLevel level, BlockPos bath, ServerPlayer player) {
        ChunkPos center = ChunkPos.containing(bath);
        for (int dx = -SEEP_RADIUS; dx <= SEEP_RADIUS; dx++) {
            for (int dz = -SEEP_RADIUS; dz <= SEEP_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
                if (chunk != null) {
                    CognitiveFog.setDensity(chunk, 1f);
                }
            }
        }
        CALLING.put(player.getUUID(), level.getGameTime() + CALLING_TICKS);
    }

    /**
     * Called when the fog takes a player. If they are still being called, their belongings rise where they sank.
     */
    static void onAbsorbed(ServerPlayer player, ServerLevel fog) {
        Long until = CALLING.remove(player.getUUID());
        if (until == null || fog.getGameTime() > until) {
            return;
        }
        List<SunkenBelongings.Cache> caches = SunkenBelongings.get(fog.getServer()).raise(player.getUUID());
        if (caches.isEmpty()) {
            player.sendSystemMessage(Component.translatable("seaofmemory.eurydice.nothing"));
            return;
        }
        for (SunkenBelongings.Cache cache : caches) {
            // Belongings from before places were remembered wait where the fog brought you instead.
            BlockPos where = raise(fog, cache.pos().orElse(player.blockPosition()), cache.items());
            player.sendSystemMessage(Component.translatable("seaofmemory.eurydice.waiting", where.getX(), where.getY(), where.getZ()));
        }
    }

    /**
     * Puts the items in chests at the place, stacked one on another as needed, and returns where the first stands.
     */
    private static BlockPos raise(ServerLevel fog, BlockPos place, List<ItemStack> items) {
        LevelChunk chunk = fog.getChunk(SectionPos.blockToSectionCoord(place.getX()), SectionPos.blockToSectionCoord(place.getZ()));
        // Imprint first: imprinting later would treat the chests as someone's building and overwrite them.
        MemoryImprint.imprintBlocking(fog.getServer(), fog, chunk);
        BlockPos base = findSpot(fog, place);
        for (int chest = 0; chest * CHEST_SIZE < items.size(); chest++) {
            BlockPos pos = base.above(chest);
            fog.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            if (fog.getBlockEntity(pos) instanceof ChestBlockEntity container) {
                for (int slot = 0; slot < CHEST_SIZE && chest * CHEST_SIZE + slot < items.size(); slot++) {
                    container.setItem(slot, items.get(chest * CHEST_SIZE + slot));
                }
            }
        }
        return base;
    }

    /**
     * The place itself if there is room, otherwise the first free space above it; never below or above the world.
     */
    private static BlockPos findSpot(ServerLevel fog, BlockPos place) {
        int y = Math.max(fog.getMinY() + 1, Math.min(place.getY(), fog.getMaxY() - 4));
        BlockPos pos = new BlockPos(place.getX(), y, place.getZ());
        for (int climbed = 0; climbed < MAX_CLIMB; climbed++) {
            if (fog.getBlockState(pos).canBeReplaced()) {
                return pos;
            }
            pos = pos.above();
        }
        return pos;
    }
}
