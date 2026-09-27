package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Items of players who died in the fog. They sink into the Sea of Memory instead of dropping,
 * and are kept here until something (the Eurydice ritual) brings them back.
 */
public final class SunkenBelongings extends SavedData {
    private static final Codec<SunkenBelongings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf()).fieldOf("belongings").forGetter(data -> data.belongings)
    ).apply(i, SunkenBelongings::new));
    private static final SavedDataType<SunkenBelongings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "sunken_belongings"), SunkenBelongings::new, CODEC);

    private final Map<UUID, List<ItemStack>> belongings = new HashMap<>();

    private SunkenBelongings() {
    }

    private SunkenBelongings(Map<UUID, List<ItemStack>> belongings) {
        belongings.forEach((owner, items) -> this.belongings.put(owner, new ArrayList<>(items)));
    }

    public static SunkenBelongings get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void sink(UUID owner, Collection<ItemStack> items) {
        List<ItemStack> sunk = belongings.computeIfAbsent(owner, ignored -> new ArrayList<>());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                sunk.add(stack.copy());
            }
        }
        setDirty();
    }
}
