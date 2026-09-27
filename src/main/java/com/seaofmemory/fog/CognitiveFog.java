package com.seaofmemory.fog;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Mth;
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
