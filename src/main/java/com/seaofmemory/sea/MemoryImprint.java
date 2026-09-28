package com.seaofmemory.sea;

import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * What people built carries over into the fog, but only what time has little power over.
 * <p>
 * The first time a fog world chunk loads, the matching real world chunk is compared with the natural terrain
 * (the fog world is generated from the same seed, so the natural state is right there). Where they differ,
 * the difference is imprinted into the fog, once and for good, provided that:
 * <ul>
 * <li>people have lived in that real chunk long enough (its inhabited time), so a night's shelter leaves no trace;</li>
 * <li>the block lasts: anything in the {@code seaofmemory:perishable} tag (lights, crops, wool, redstone...) is gone,
 * leaving an empty space behind;</li>
 * <li>the column is inside the intact part of the fog world, not where it dissolves into the sea.</li>
 * </ul>
 * Only blocks are imprinted: the fog remembers places, not the things kept in them.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class MemoryImprint {
    public static final TagKey<Block> PERISHABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "perishable"));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeaOfMemory.MODID);

    // Set on a fog world chunk once it has been compared, whether or not anything was carried over.
    public static final Supplier<AttachmentType<Boolean>> IMPRINTED = ATTACHMENT_TYPES.register("imprinted", () -> AttachmentType.builder(() -> false)
            .serialize(Codec.BOOL.fieldOf("imprinted"))
            .build());

    // Columns where the fog world's terrain is not quite the real one are left alone.
    private static final double INTACT_MEMORY = 0.999;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    // Real world chunks being read from disk, so the same read is not started twice.
    private static final LongSet READING = new LongOpenHashSet();

    private MemoryImprint() {
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && FogWorld.is(level)
                && event.getChunk() instanceof LevelChunk chunk && !chunk.getData(IMPRINTED.get())) {
            // Not right now: loading other chunks from inside a chunk load is asking for trouble.
            PENDING.add(chunk.getPos().pack());
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel fog = server.getLevel(FogWorld.KEY);
        if (fog == null) {
            PENDING.clear();
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !PENDING.isEmpty(); i++) {
            long pos = PENDING.removeFirstLong();
            LevelChunk chunk = fog.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            // An unloaded chunk is simply left unmarked: it comes back through onChunkLoad next time.
            if (chunk != null && !chunk.getData(IMPRINTED.get())) {
                imprintWhenReady(server, fog, chunk);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        READING.clear();
    }

    /**
     * Imprints the chunk right away if the real one is loaded, which it is where a player has just been absorbed:
     * the place is already whole when they arrive instead of changing around them.
     */
    static void imprintNow(MinecraftServer server, ServerLevel fog, LevelChunk chunk) {
        if (!chunk.getData(IMPRINTED.get())) {
            imprintWhenReady(server, fog, chunk);
        }
    }

    /**
     * Imprints the chunk right now, loading the real one if it has to. For when something is about to be put
     * into the chunk that a later imprint would take for someone's building and overwrite.
     */
    static void imprintBlocking(MinecraftServer server, ServerLevel fog, LevelChunk chunk) {
        if (!chunk.getData(IMPRINTED.get())) {
            imprint(fog, chunk, server.overworld().getChunk(chunk.getPos().x(), chunk.getPos().z()));
        }
    }

    private static void imprintWhenReady(MinecraftServer server, ServerLevel fog, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ServerLevel overworld = server.overworld();
        LevelChunk real = overworld.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (real != null) {
            imprint(fog, chunk, real);
            return;
        }
        // Not loaded: look at what is saved on disk first, so a chunk nobody ever generated is not generated
        // just to find out it has nothing to give, and a barely visited one is not loaded for nothing.
        if (!READING.add(pos.pack())) {
            return;
        }
        overworld.getChunkSource().chunkMap.read(pos).whenComplete((tag, error) -> server.execute(() -> {
            READING.remove(pos.pack());
            LevelChunk fogChunk = fog.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (fogChunk == null || fogChunk.getData(IMPRINTED.get())) {
                return;
            }
            if (error != null || !worthLoading(tag)) {
                fogChunk.setData(IMPRINTED.get(), true);
                return;
            }
            imprint(fog, fogChunk, overworld.getChunk(pos.x(), pos.z()));
        }));
    }

    private static boolean worthLoading(Optional<CompoundTag> tag) {
        return tag.isPresent()
                && SerializableChunkData.getChunkStatusFromTag(tag.get()) == ChunkStatus.FULL
                && tag.get().getLongOr("InhabitedTime", 0L) >= requiredInhabitedTime();
    }

    private static long requiredInhabitedTime() {
        return Config.IMPRINT_AFTER_DAYS.getAsInt() * 24000L;
    }

    private static void imprint(ServerLevel fog, LevelChunk chunk, LevelChunk real) {
        chunk.setData(IMPRINTED.get(), true);
        if (real.getInhabitedTime() < requiredInhabitedTime()) {
            return;
        }
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                if (MemoryDensityFunction.memory(x, z) < INTACT_MEMORY) {
                    continue;
                }
                for (int y = fog.getMinY(); y <= fog.getMaxY(); y++) {
                    pos.set(x, y, z);
                    BlockState natural = chunk.getBlockState(pos);
                    BlockState remembered = remembered(real.getBlockState(pos), natural);
                    if (remembered != natural) {
                        fog.setBlock(pos, remembered, FLAGS);
                        changed++;
                    }
                }
            }
        }
        if (changed > 0) {
            SeaOfMemory.LOGGER.debug("Imprinted {} blocks into fog chunk {}", changed, chunk.getPos());
        }
    }

    /**
     * What the fog keeps of a real block, given what nature would have put there.
     */
    private static BlockState remembered(BlockState real, BlockState natural) {
        if (real == natural) {
            return natural;
        }
        // Water and lava people poured go their own way. Blocks merely standing in water (piers, pillars) do carry over.
        if (real.getBlock() instanceof LiquidBlock) {
            return natural;
        }
        // Perishable things are gone, and so is whatever people removed: both leave an empty space,
        // except where nature had water, which simply comes back.
        if (real.isAir() || real.is(PERISHABLE)) {
            return natural.isAir() || natural.getBlock() instanceof LiquidBlock ? natural : Blocks.AIR.defaultBlockState();
        }
        return real;
    }
}
