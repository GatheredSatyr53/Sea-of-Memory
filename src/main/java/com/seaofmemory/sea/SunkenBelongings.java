package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Items of players who died in the fog. They sink into the Sea of Memory instead of dropping, each death's
 * belongings kept together with the place it happened, until the Eurydice ritual brings them back there.
 */
public final class SunkenBelongings extends SavedData {
    /**
     * One death's belongings.
     *
     * @param pos where in the fog world they sank; empty for belongings saved before places were remembered
     */
    public record Cache(Optional<BlockPos> pos, List<ItemStack> items) {
        static final Codec<Cache> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("pos").forGetter(Cache::pos),
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Cache::items)
        ).apply(i, Cache::new));
    }

    private static final Codec<SunkenBelongings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Cache.CODEC.listOf()).optionalFieldOf("caches", Map.of()).forGetter(data -> data.caches),
            // Older saves kept one pile of items per player, with no place.
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf()).optionalFieldOf("belongings", Map.of()).forGetter(data -> Map.of())
    ).apply(i, SunkenBelongings::new));
    private static final SavedDataType<SunkenBelongings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "sunken_belongings"), SunkenBelongings::new, CODEC);

    private final Map<UUID, List<Cache>> caches = new HashMap<>();

    private SunkenBelongings() {
    }

    private SunkenBelongings(Map<UUID, List<Cache>> caches, Map<UUID, List<ItemStack>> legacy) {
        caches.forEach((owner, list) -> this.caches.put(owner, new ArrayList<>(list)));
        legacy.forEach((owner, items) -> this.caches.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new Cache(Optional.empty(), items)));
    }

    public static SunkenBelongings get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void sink(UUID owner, BlockPos where, Collection<ItemStack> items) {
        List<ItemStack> sunk = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                sunk.add(stack.copy());
            }
        }
        if (sunk.isEmpty()) {
            return;
        }
        caches.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new Cache(Optional.of(where.immutable()), sunk));
        setDirty();
    }

    /**
     * Takes everything the owner lost to the fog, to be brought back.
     */
    public List<Cache> raise(UUID owner) {
        List<Cache> raised = caches.remove(owner);
        if (raised == null) {
            return List.of();
        }
        setDirty();
        return raised;
    }
}
