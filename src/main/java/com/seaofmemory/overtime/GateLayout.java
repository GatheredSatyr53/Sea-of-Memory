package com.seaofmemory.overtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Where everything of the Gates stands, and what of it is left in the ruin: the bridge out over the pond, the clock
 * on its left shore pillar, and on the other side of the bridge the old gazebo that becomes the guardhouse.
 * <p>
 * Worked out from the shore point, the direction out over the water and a seed alone, never from the world, so the
 * settlement's generation and the Gates at the Overtime agree on every block.
 * <p>
 * Coordinates: {@code o} runs out over the water from the shore ({@code o = 1} is the first span), {@code w} across,
 * negative to the left as seen from the shore, and {@code y} is up from the ground level (the first air above the ground).
 */
public final class GateLayout {
    public static final int MIN_BRIDGE = 14;
    public static final int MAX_BRIDGE = 18;
    // The pond: the bridge goes across it, from bank to bank.
    public static final int POND_HALF_WIDTH = 8;
    public static final int POND_DEPTH = 4;
    // The deck lies level with the shore's paths; the water stands a block below the banks.
    public static final int DECK_Y = -1;
    public static final int WATER_TOP = -2;
    private static final int LAMP_SPACING = 6;
    // The arch: pillars this far to each side of the middle, this tall under the lintel.
    public static final int ARCH_HALF_WIDTH = 3;
    public static final int ARCH_HEIGHT = 4;
    private static final int PILLAR_SPACING = 4;
    // The gazebo, on the right of the bridge's shore end.
    static final int GAZEBO_O0 = -6;
    static final int GAZEBO_O1 = -2;
    static final int GAZEBO_W0 = 4;
    static final int GAZEBO_W1 = 8;

    /**
     * One block of the Gates: what stands there in the ruin (empty: nothing) and when the Gates are whole.
     */
    public record Part(BlockPos pos, Optional<BlockState> ruin, BlockState whole) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Part::pos),
                BlockState.CODEC.optionalFieldOf("ruin").forGetter(Part::ruin),
                BlockState.CODEC.fieldOf("whole").forGetter(Part::whole)
        ).apply(i, Part::new));
    }

    private final BlockPos origin;
    private final Direction out;
    private final Direction side;
    private final int length;
    private final List<Part> parts = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final BlockPos clock;

    /**
     * What fell off the bridge long ago and lies on the bottom of the pond. It stays there whatever the Gates do.
     */
    public record Debris(BlockPos pos, BlockState state) {
    }

    private GateLayout(BlockPos origin, Direction out, long seed) {
        this.origin = origin;
        this.out = out;
        this.side = out.getClockWise();
        RandomSource random = RandomSource.create(seed);
        this.length = MIN_BRIDGE + random.nextInt(MAX_BRIDGE - MIN_BRIDGE + 1);
        // The clock stands on the deck over the left leg of the first pair of pillars.
        this.clock = at(1, -2, DECK_Y + 1);
        bridge(random);
        arch(random);
        gazebo(random);
    }

    public static GateLayout of(BlockPos origin, Direction out, long seed) {
        return new GateLayout(origin, out, seed);
    }

    public BlockPos at(int o, int w, int y) {
        return origin.relative(out, o).relative(side, w).above(y);
    }

    public List<Part> parts() {
        return parts;
    }

    public List<Debris> debris() {
        return debris;
    }

    public int length() {
        return length;
    }

    public BlockPos clock() {
        return clock;
    }

    /**
     * The far bank, under the arch where the bridge goes into the fog, and the waves come out.
     */
    public BlockPos farEnd() {
        return at(length + 1, 0, DECK_Y + 1);
    }

    public Direction out() {
        return out;
    }

    public Direction side() {
        return side;
    }

    private void bridge(RandomSource random) {
        BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState slab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
        BlockState moss = Blocks.MOSS_CARPET.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockState> fallen = List.of(cracked, slab, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState());
        int bottom = WATER_TOP - POND_DEPTH + 1;
        for (int o = 1; o <= length; o++) {
            int i = o - 1;
            // The further out over the water, the less of the deck is left.
            float remains = Mth.lerp(i / (float) length, 0.8f, 0.25f);
            for (int w = -2; w <= 2; w++) {
                BlockPos pos = at(o, w, DECK_Y);
                // Under the clock's stump the deck always holds.
                boolean underClock = pos.equals(clock.below());
                boolean stays = underClock || random.nextFloat() < (Math.abs(w) == 2 ? remains * 0.7f : remains);
                if (stays) {
                    // Some of it worn down to half, some overgrown.
                    boolean worn = !underClock && random.nextFloat() < 0.25f;
                    parts.add(new Part(pos, Optional.of(worn ? slab : cracked), tiles));
                    if (!worn && Math.abs(w) < 2 && random.nextFloat() < 0.15f) {
                        parts.add(new Part(pos.above(), Optional.of(moss), air));
                    }
                } else {
                    parts.add(new Part(pos, Optional.empty(), tiles));
                    // What was here is on the bottom now.
                    if (random.nextFloat() < 0.5f) {
                        debris.add(new Debris(at(o, w, bottom), fallen.get(random.nextInt(fallen.size()))));
                    }
                }
            }
            for (int w : new int[] {-2, 2}) {
                BlockPos rail = at(o, w, DECK_Y + 1);
                if (rail.equals(clock)) {
                    // In the ruin only a broken stump is left of it.
                    parts.add(new Part(clock, Optional.of(Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()), SeaOfMemory.GATE_CLOCK.get().defaultBlockState()));
                    continue;
                }
                parts.add(new Part(rail, random.nextFloat() < 0.3f ? Optional.of(bars) : Optional.empty(), bars));
                // Detransmogrifiers, unlit, and only far from the shore: they wake the frozen.
                if (i % LAMP_SPACING == LAMP_SPACING - 1 && o > length / 2) {
                    parts.add(new Part(at(o, w, DECK_Y + 2), Optional.empty(), SeaOfMemory.DTM_LAMP.get().defaultBlockState()));
                }
            }
            if (i % PILLAR_SPACING == 0) {
                // A pair of pillars under the edges, down to the bottom of the pond; in the ruin each is a stump.
                for (int w : new int[] {-2, 2}) {
                    int stump = random.nextInt(3) == 0 ? 0 : random.nextInt(POND_DEPTH + 1);
                    for (int depth = 1; depth <= POND_DEPTH; depth++) {
                        boolean inRuin = depth > POND_DEPTH - stump;
                        parts.add(new Part(at(o, w, DECK_Y - depth), inRuin ? Optional.of(tiles) : Optional.empty(), tiles));
                    }
                }
            }
        }
    }

    /**
     * A stone arch on the far bank, over the bridge's end: during the Overtime the way into the fog, and the only way out of it.
     * In the ruin, broken stumps of its pillars.
     */
    private void arch(RandomSource random) {
        int o = length + 1;
        BlockState bricks = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        for (int w : new int[] {-ARCH_HALF_WIDTH, ARCH_HALF_WIDTH}) {
            int stump = 1 + random.nextInt(3);
            for (int y = 1; y <= ARCH_HEIGHT; y++) {
                parts.add(new Part(at(o, w, DECK_Y + y), y <= stump ? Optional.of(cracked) : Optional.empty(), bricks));
            }
        }
        for (int w = -ARCH_HALF_WIDTH; w <= ARCH_HALF_WIDTH; w++) {
            parts.add(new Part(at(o, w, DECK_Y + ARCH_HEIGHT + 1), Optional.empty(), bricks));
        }
        // The rounding under the lintel.
        for (int w : new int[] {-ARCH_HALF_WIDTH + 1, ARCH_HALF_WIDTH - 1}) {
            BlockState corner = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, w < 0 ? side.getOpposite() : side)
                    .setValue(StairBlock.HALF, Half.TOP);
            parts.add(new Part(at(o, w, DECK_Y + ARCH_HEIGHT), Optional.empty(), corner));
        }
        // What fell of it lies by the bank.
        debris.add(new Debris(at(o - 1, random.nextBoolean() ? -3 : 3, WATER_TOP - 1), cracked));
    }

    /**
     * "Старая беседка по другую сторону от часов выпрямилась, доски срослись, между опорами выросли стены
     * с окошками и дверью, за которыми горел тот же свет."
     */
    private void gazebo(RandomSource random) {
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState rot = Blocks.COARSE_DIRT.defaultBlockState();
        int midO = (GAZEBO_O0 + GAZEBO_O1) / 2;
        int midW = (GAZEBO_W0 + GAZEBO_W1) / 2;
        for (int o = GAZEBO_O0; o <= GAZEBO_O1; o++) {
            for (int w = GAZEBO_W0; w <= GAZEBO_W1; w++) {
                boolean corner = (o == GAZEBO_O0 || o == GAZEBO_O1) && (w == GAZEBO_W0 || w == GAZEBO_W1);
                boolean edge = o == GAZEBO_O0 || o == GAZEBO_O1 || w == GAZEBO_W0 || w == GAZEBO_W1;
                // The floor: rotten through in places, whole again at the Overtime.
                parts.add(new Part(at(o, w, -1), Optional.of(random.nextFloat() < 0.7f ? planks : rot), planks));
                if (corner) {
                    int height = 1 + random.nextInt(3);
                    for (int y = 0; y <= 2; y++) {
                        parts.add(new Part(at(o, w, y), y < height ? Optional.of(log) : Optional.empty(), log));
                    }
                } else if (edge) {
                    // The door faces the bridge.
                    boolean door = w == GAZEBO_W0 && o == midO;
                    boolean window = (o == midO || w == midW) && !door;
                    if (!door) {
                        parts.add(new Part(at(o, w, 0), random.nextFloat() < 0.5f ? Optional.of(fence) : Optional.empty(), planks));
                    }
                    if (!door) {
                        parts.add(new Part(at(o, w, 1), Optional.empty(), window ? pane : planks));
                    }
                    parts.add(new Part(at(o, w, 2), Optional.empty(), planks));
                }
            }
        }
        for (int o = GAZEBO_O0 - 1; o <= GAZEBO_O1 + 1; o++) {
            for (int w = GAZEBO_W0 - 1; w <= GAZEBO_W1 + 1; w++) {
                boolean over = o >= GAZEBO_O0 && o <= GAZEBO_O1 && w >= GAZEBO_W0 && w <= GAZEBO_W1;
                parts.add(new Part(at(o, w, 3), over && random.nextFloat() < 0.4f ? Optional.of(slab) : Optional.empty(), slab));
            }
        }
        // Inside, the same light as on the bridge.
        parts.add(new Part(at(midO, midW, 0), Optional.empty(), SeaOfMemory.DTM_LAMP.get().defaultBlockState()));
    }
}
