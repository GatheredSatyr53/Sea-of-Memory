package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Places where someone was absorbed into the fog. The fog world is an intact copy of the real world
 * around them and dissolves into the sea further away (see {@link MemoryDensityFunction}).
 * <p>
 * An anchor must be added before the fog world generates the chunks around it: terrain is fixed once generated.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class MemoryAnchors extends SavedData {
    // A new anchor this close to an existing one adds nothing: the area is already remembered.
    private static final int MIN_SPACING = 48;

    private static final Codec<MemoryAnchors> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.listOf().fieldOf("anchors").forGetter(data -> data.anchors)
    ).apply(i, MemoryAnchors::new));
    private static final SavedDataType<MemoryAnchors> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "memory_anchors"), MemoryAnchors::new, CODEC);

    // Read by world generation threads; replaced as a whole, never mutated.
    private static volatile long[] snapshot = new long[0];

    private final List<Long> anchors;

    private MemoryAnchors() {
        this(List.of());
    }

    private MemoryAnchors(List<Long> anchors) {
        this.anchors = new ArrayList<>(anchors);
    }

    static long[] snapshot() {
        return snapshot;
    }

    static int anchorX(long anchor) {
        return (int) (anchor >> 32);
    }

    static int anchorZ(long anchor) {
        return (int) anchor;
    }

    public static void add(MinecraftServer server, int x, int z) {
        MemoryAnchors data = get(server);
        for (long anchor : data.anchors) {
            double dx = x - anchorX(anchor);
            double dz = z - anchorZ(anchor);
            if (dx * dx + dz * dz < MIN_SPACING * MIN_SPACING) {
                return;
            }
        }
        data.anchors.add(((long) x << 32) | (z & 0xFFFFFFFFL));
        data.setDirty();
        publish(data);
    }

    private static MemoryAnchors get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static void publish(MemoryAnchors data) {
        snapshot = data.anchors.stream().mapToLong(Long::longValue).toArray();
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        publish(get(event.getServer()));
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        snapshot = new long[0];
    }
}
