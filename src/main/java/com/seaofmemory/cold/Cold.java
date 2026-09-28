package com.seaofmemory.cold;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * "Холод" — the cold that seeps into a player's chest in the fog, from 0 to {@link #MAX}.
 * Server-authoritative and synced only to its owner: nobody else knows how close you are to lying down.
 * Not copied on death, so a respawned player starts warm.
 */
public final class Cold {
    public static final float MAX = 100f;
    // Blocks that warm a player standing next to them. Blocks with a "lit" property only count while lit.
    public static final TagKey<Block> WARMTH_SOURCES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "warmth_sources"));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeaOfMemory.MODID);

    public static final Supplier<AttachmentType<Float>> COLD = ATTACHMENT_TYPES.register("cold", () -> AttachmentType.builder(() -> 0f)
            .serialize(Codec.FLOAT.fieldOf("cold"), cold -> cold > 0f)
            .sync((holder, to) -> holder == to, ByteBufCodecs.FLOAT)
            .build());

    // What the Eurydice catalyst leaves behind: cold that never goes away, not even with death.
    public static final Supplier<AttachmentType<Float>> FLOOR = ATTACHMENT_TYPES.register("cold_floor", () -> AttachmentType.builder(() -> 0f)
            .serialize(Codec.FLOAT.fieldOf("floor"), floor -> floor > 0f)
            .copyOnDeath()
            .build());
    // Enough to make every day heavier, never enough to freeze a player to death by itself.
    public static final float MAX_FLOOR = 80f;

    private Cold() {
    }

    /**
     * The level the player's cold can never drop below.
     */
    public static float floor(Player player) {
        return player.getData(FLOOR.get());
    }

    public static void raiseFloor(Player player, float amount) {
        player.setData(FLOOR.get(), Math.min(MAX_FLOOR, floor(player) + amount));
        set(player, Math.max(get(player), floor(player)));
    }

    public static float get(Player player) {
        return player.getData(COLD.get());
    }

    public static void set(Player player, float cold) {
        cold = Mth.clamp(cold, 0f, MAX);
        if (cold != get(player)) {
            player.setData(COLD.get(), cold);
        }
    }

    /**
     * Cold as a fraction of the maximum, for rendering.
     */
    public static float fraction(Player player) {
        return get(player) / MAX;
    }
}
