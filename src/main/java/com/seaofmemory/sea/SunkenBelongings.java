package com.seaofmemory.sea;

import java.util.ArrayList;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Belongings that sank in the fog before deaths left memory graves. Only read from older saves:
 * the next time the fog takes their owner, they rise as graves where they sank, and the store empties.
 */
public final class SunkenBelongings extends SavedData {
    /**
     * One death's belongings.
     *
     * @param pos where in the fog world they sank; empty for the oldest saves, which did not remember it
     */
    private record Cache(Optional<BlockPos> pos, List<ItemStack> items) {
        static final Codec<Cache> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("pos").forGetter(Cache::pos),
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Cache::items)
        ).apply(i, Cache::new));
    }

    private static final Codec<SunkenBelongings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Cache.CODEC.listOf()).optionalFieldOf("caches", Map.of()).forGetter(data -> data.caches),
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

    /**
     * Raises the player's old sunken belongings as memory graves, now that the fog has taken them.
     */
    static void raiseOld(ServerPlayer player, ServerLevel fog) {
        SunkenBelongings data = fog.getServer().getDataStorage().computeIfAbsent(TYPE);
        List<Cache> old = data.caches.remove(player.getUUID());
        if (old == null) {
            return;
        }
        data.setDirty();
        for (Cache cache : old) {
            BlockPos grave = MemoryGraveBlock.place(fog, cache.pos().orElse(player.blockPosition()), cache.items());
            player.sendSystemMessage(Component.translatable("seaofmemory.grave.sank", grave.getX(), grave.getY(), grave.getZ()));
        }
    }
}
