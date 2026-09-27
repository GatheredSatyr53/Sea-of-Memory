package com.seaofmemory.fog;

import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Drifts the fog density of chunks around players towards an environmental target.
 * Fog gathers over water, in humid biomes, at night and in the rain, and it rises faster than it clears.
 * Chunks away from players keep whatever density they had.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogSimulation {
    // Natural fog never quite reaches 1.0; only anomalies and events push it there.
    private static final float MAX_NATURAL_DENSITY = 0.95f;
    private static final float WATER_WEIGHT = 0.35f;
    private static final float HUMIDITY_WEIGHT = 0.15f;
    private static final float NIGHT_BONUS = 0.2f;
    private static final float RAIN_BONUS = 0.25f;
    // Surface samples per chunk axis when looking for water (4x4 grid).
    private static final int WATER_SAMPLES = 4;

    private FogSimulation() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimensionType().hasSkyLight()) {
            return;
        }
        if (level.getGameTime() % Config.FOG_UPDATE_INTERVAL.getAsInt() != 0) {
            return;
        }

        int radius = Config.FOG_SIMULATION_RADIUS.getAsInt();
        LongSet visited = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = ChunkPos.containing(player.blockPosition());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x() + dx;
                    int z = center.z() + dz;
                    if (!visited.add(ChunkPos.pack(x, z))) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) {
                        updateChunk(level, chunk);
                    }
                }
            }
        }
    }

    private static void updateChunk(ServerLevel level, LevelChunk chunk) {
        float current = CognitiveFog.getDensity(chunk);
        float target = targetDensity(level, chunk);
        double rate = target > current ? Config.FOG_RISE_RATE.getAsDouble() : Config.FOG_FALL_RATE.getAsDouble();
        float next = Mth.approach(current, target, (float) rate);
        if (next != current) {
            CognitiveFog.setDensity(chunk, next);
        }
    }

    static float targetDensity(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int spacing = 16 / WATER_SAMPLES;
        int waterSamples = 0;
        for (int i = 0; i < WATER_SAMPLES; i++) {
            for (int j = 0; j < WATER_SAMPLES; j++) {
                int localX = i * spacing + spacing / 2;
                int localZ = j * spacing + spacing / 2;
                // The heightmap points one above the top block, so step down to find the surface fluid.
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
                if (chunk.getFluidState(pos.getMinBlockX() + localX, y, pos.getMinBlockZ() + localZ).is(FluidTags.WATER)) {
                    waterSamples++;
                }
            }
        }
        float water = waterSamples / (float) (WATER_SAMPLES * WATER_SAMPLES);

        BlockPos surface = new BlockPos(pos.getMiddleBlockX(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8), pos.getMiddleBlockZ());
        float humidity = level.getBiome(surface).value().modifiableBiomeInfo().get().climateSettings().downfall();

        float target = water * WATER_WEIGHT + Mth.clamp(humidity, 0f, 1f) * HUMIDITY_WEIGHT;
        if (level.isDarkOutside()) {
            target += NIGHT_BONUS;
        }
        if (level.isRainingAt(surface)) {
            target += RAIN_BONUS;
        }
        return Mth.clamp(target, 0f, MAX_NATURAL_DENSITY);
    }
}
