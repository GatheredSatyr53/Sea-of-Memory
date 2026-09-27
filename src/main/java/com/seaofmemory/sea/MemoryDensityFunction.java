package com.seaofmemory.sea;

import com.mojang.serialization.MapCodec;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * How well the fog world remembers a column: 1 near the places people were absorbed, where it is an intact
 * copy of the real world, fading to 0 in the open fog sea. Used by the fog world's noise settings to blend
 * overworld terrain into an empty sea.
 */
public final class MemoryDensityFunction implements DensityFunction.SimpleFunction {
    public static final MemoryDensityFunction INSTANCE = new MemoryDensityFunction();
    public static final MapCodec<MemoryDensityFunction> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final KeyDispatchDataCodec<MemoryDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    // Blocks from the nearest anchor where the copy starts to crumble, and where only the sea is left.
    private static final double INTACT_RADIUS = 96;
    private static final double SEA_RADIUS = 256;

    private MemoryDensityFunction() {
    }

    @Override
    public double compute(FunctionContext context) {
        return memory(context.blockX(), context.blockZ());
    }

    /**
     * Memory of a column: 1 for an intact copy of the real world, 0 for open sea.
     */
    public static double memory(int x, int z) {
        long[] anchors = MemoryAnchors.snapshot();
        double nearestSq = Double.MAX_VALUE;
        for (long anchor : anchors) {
            double dx = x - MemoryAnchors.anchorX(anchor);
            double dz = z - MemoryAnchors.anchorZ(anchor);
            nearestSq = Math.min(nearestSq, dx * dx + dz * dz);
        }
        if (nearestSq == Double.MAX_VALUE) {
            return 0;
        }
        double t = Mth.clamp((Math.sqrt(nearestSq) - INTACT_RADIUS) / (SEA_RADIUS - INTACT_RADIUS), 0, 1);
        return 1 - t * t * (3 - 2 * t);
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
