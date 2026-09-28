package com.seaofmemory.sea;

import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * "Эвридика": pour transmogrifier concentrate into hot water and the fog comes for you. It is the way to step into
 * the fog of your own will, to go after what is left there: someone lost, or your own belongings at a memory grave.
 * The fog does not stay where it was poured: it seeps into the chunks around it, and takes whoever it finds there.
 */
public final class EurydiceRitual {
    private static final int SEEP_RADIUS = 1;

    private EurydiceRitual() {
    }

    /**
     * Fills the chunk around the bath and its neighbours with critical fog.
     */
    public static void begin(ServerLevel level, BlockPos bath) {
        ChunkPos center = ChunkPos.containing(bath);
        for (int dx = -SEEP_RADIUS; dx <= SEEP_RADIUS; dx++) {
            for (int dz = -SEEP_RADIUS; dz <= SEEP_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
                if (chunk != null) {
                    CognitiveFog.setDensity(chunk, 1f);
                }
            }
        }
    }
}
