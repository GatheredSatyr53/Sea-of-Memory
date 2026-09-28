package com.seaofmemory.fog;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.overtime.Overtime;
import com.seaofmemory.sea.FogWorld;

import net.minecraft.core.BlockPos;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Cognitive fog density, stored per chunk in the range [0, 1] and synced to every client tracking the chunk.
 * Above {@link #CRITICAL} the ideal and the material swap places and projections can leave the fog.
 */
public final class CognitiveFog {
    public static final float CRITICAL = 0.75f;
    // Inside the fog world the fog is everywhere, thick but just below critical.
    public static final float FOG_WORLD_DENSITY = 0.6f;
    // During the Overtime the fog itself does not change: reality turns cognitive, and everything that answers to
    // the fog feels it this much denser. The shift is gone the moment the Overtime ends, so nothing lingers after it.
    public static final float OVERTIME_SHIFT = 0.3f;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeaOfMemory.MODID);

    // Clear chunks carry no attachment at all: reads go through getExistingDataOrNull() so nothing gets default-created and synced.
    public static final Supplier<AttachmentType<Float>> DENSITY = ATTACHMENT_TYPES.register("fog_density", () -> AttachmentType.builder(() -> 0f)
            .serialize(Codec.FLOAT.fieldOf("density"), density -> density > 0f)
            .sync(ByteBufCodecs.FLOAT)
            .build());

    private CognitiveFog() {
    }

    public static float getDensity(ChunkAccess chunk) {
        Float density = chunk.getExistingDataOrNull(DENSITY.get());
        return density == null ? 0f : density;
    }

    /**
     * Fog density as everything in the game feels it at a position, in any level: the fog world's own,
     * and during the Overtime the real world's fog shifted by {@link #OVERTIME_SHIFT}.
     */
    public static float densityAt(Level level, BlockPos pos) {
        if (FogWorld.is(level)) {
            return FOG_WORLD_DENSITY;
        }
        float density = getDensity(level.getChunkAt(pos));
        return Overtime.isActive(level) ? Math.min(1f, density + OVERTIME_SHIFT) : density;
    }

    public static void setDensity(ChunkAccess chunk, float density) {
        density = Mth.clamp(density, 0f, 1f);
        if (density > 0f) {
            chunk.setData(DENSITY.get(), density);
        } else if (chunk.hasData(DENSITY.get())) {
            chunk.removeData(DENSITY.get());
        }
    }

    public static boolean isCritical(float density) {
        return density >= CRITICAL;
    }
}
