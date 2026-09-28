package com.seaofmemory.overtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A settlement by a pond, with the ruin of an old bridge going out over the water into the fog: the Gates,
 * waiting for the Overtime. A few houses with their people around a square with a bell, an oak by the bridge,
 * and on the other side of the bridge from the clock an old gazebo.
 * <p>
 * Everything is laid out from one shore point, a direction and a seed, so the Gates later know exactly where
 * their bridge is (see {@link GateLayout}).
 */
public class GateVillage extends Structure {
    public static final MapCodec<GateVillage> CODEC = simpleCodec(GateVillage::new);
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "gate_village"));

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, SeaOfMemory.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, SeaOfMemory.MODID);
    public static final DeferredHolder<StructureType<?>, StructureType<GateVillage>> TYPE =
            STRUCTURE_TYPES.register("gate_village", () -> () -> CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            STRUCTURE_PIECES.register("gate_village", () -> (StructurePieceType.ContextlessType) Piece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> FOOTPRINT =
            STRUCTURE_PIECES.register("gate_village_footprint", () -> (StructurePieceType.ContextlessType) Footprint::new);

    // The settlement, in the layout's coordinates: from the far end of the village to past the pond, and across.
    private static final int BACK = -32;
    private static final int FRONT = GateLayout.MAX_BRIDGE + 8;
    private static final int HALF_WIDTH = 18;
    private static final int MAX_SLOPE = 10;
    static final int SQUARE_O = -16;

    public GateVillage(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        Direction out = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        long seed = context.random().nextLong();
        Direction side = out.getClockWise();

        // Only on fairly level ground above the sea: the settlement is flattened, but not carved out of a hillside.
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        int count = 0;
        for (int o = BACK; o <= FRONT; o += 16) {
            for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w += HALF_WIDTH) {
                int sx = x + out.getStepX() * o + side.getStepX() * w;
                int sz = z + out.getStepZ() * o + side.getStepZ() * w;
                int y = context.chunkGenerator().getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                min = Math.min(min, y);
                max = Math.max(max, y);
                sum += y;
                count++;
            }
        }
        int ground = sum / count;
        if (max - min > MAX_SLOPE || min <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, ground + 1, z);
        return Optional.of(new GenerationStub(origin, builder -> {
            Piece village = new Piece(origin, out, seed);
            builder.addPiece(village);
            // The ground is levelled only where something stands on it: the houses, the square, the gazebo.
            // The pond levels its own banks (see Piece#getBeardifierBox); between them the land stays as it was.
            GateLayout layout = village.layout();
            for (Piece.House house : Piece.houses(layout, seed)) {
                builder.addPiece(new Footprint(BoundingBox.fromCorners(house.at(-1, 0, -1), house.at(Piece.House.WIDTH, 8, Piece.House.DEPTH))));
            }
            builder.addPiece(new Footprint(BoundingBox.fromCorners(layout.at(SQUARE_O - 3, -3, 0), layout.at(SQUARE_O + 3, 3, 4))));
            builder.addPiece(new Footprint(BoundingBox.fromCorners(layout.at(GateLayout.GAZEBO_O0 - 1, GateLayout.GAZEBO_W0 - 1, 0),
                    layout.at(GateLayout.GAZEBO_O1 + 1, GateLayout.GAZEBO_W1 + 1, 5))));
        }));
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    /**
     * The whole settlement as one piece, built chunk by chunk.
     */
    public static class Piece extends StructurePiece implements PieceBeardifierModifier {
        private final BlockPos origin;
        private final Direction out;
        private final long seed;
        private final GateLayout layout;

        Piece(BlockPos origin, Direction out, long seed) {
            super(PIECE.get(), 0, bounds(GateLayout.of(origin, out, seed), GateLayout.POND_DEPTH + 2, 12));
            this.origin = origin;
            this.out = out;
            this.seed = seed;
            this.layout = GateLayout.of(origin, out, seed);
        }

        Piece(CompoundTag tag) {
            super(PIECE.get(), tag);
            this.origin = BlockPos.of(tag.getLongOr("Origin", 0L));
            this.out = Direction.from2DDataValue(tag.getIntOr("Out", 0));
            this.seed = tag.getLongOr("Seed", 0L);
            this.layout = GateLayout.of(origin, out, seed);
        }

        private static BoundingBox bounds(GateLayout layout, int below, int above) {
            return BoundingBox.fromCorners(layout.at(BACK, -HALF_WIDTH, -below), layout.at(FRONT, HALF_WIDTH, above));
        }

        public GateLayout layout() {
            return layout;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putLong("Origin", origin.asLong());
            tag.putInt("Out", out.get2DDataValue());
            tag.putLong("Seed", seed);
        }

        // The pond and its banks are levelled at the shore's height, so both ends of the bridge meet the ground;
        // the pond is dug into it afterwards.
        @Override
        public BoundingBox getBeardifierBox() {
            int half = GateLayout.POND_HALF_WIDTH + 5;
            return BoundingBox.fromCorners(layout.at(-1, -half, 0), layout.at(layout.length() + 5, half, 6));
        }

        @Override
        public TerrainAdjustment getTerrainAdjustment() {
            return TerrainAdjustment.BEARD_THIN;
        }

        @Override
        public int getGroundLevelDelta() {
            return 0;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            // Its own randomness, the same whichever chunk is being built.
            pond(level, chunkBB);
            List<House> houses = houses(layout, seed);
            paths(level, chunkBB, houses);
            square(level, chunkBB);
            for (House house : houses) {
                house.build(level, chunkBB);
            }
            for (GateLayout.Debris debris : layout.debris()) {
                set(level, chunkBB, debris.pos(), debris.state());
            }
            for (GateLayout.Part part : layout.parts()) {
                part.ruin().ifPresent(state -> set(level, chunkBB, part.pos(), state));
            }
            plants(level, chunkBB);
            BlockPos oak = surface(level, layout.at(-3, -6, 0)).above();
            if (chunkBB.isInside(oak)) {
                level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(TreeFeatures.FANCY_OAK)
                        .ifPresent(tree -> tree.value().place(level, generator, random, oak));
            }
        }

        // ---- Randomness tied to a place, so every chunk agrees on it ----

        private float chance(int o, int w, int salt) {
            long h = seed ^ (o * 0x9E3779B97F4A7C15L) ^ (w * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
            h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
            h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
            h ^= h >>> 33;
            return (h >>> 40) / (float) (1L << 24);
        }

        // Smooth noise in [-1, 1] over a grid four blocks wide.
        private float noise(int o, int w, int salt) {
            int o0 = Math.floorDiv(o, 4);
            int w0 = Math.floorDiv(w, 4);
            float fo = smooth((o - o0 * 4) / 4f);
            float fw = smooth((w - w0 * 4) / 4f);
            float a = chance(o0, w0, salt);
            float b = chance(o0 + 1, w0, salt);
            float c = chance(o0, w0 + 1, salt);
            float d = chance(o0 + 1, w0 + 1, salt);
            float top = a + (b - a) * fo;
            float bottom = c + (d - c) * fo;
            return (top + (bottom - top) * fw) * 2 - 1;
        }

        private static float smooth(float t) {
            return t * t * (3 - 2 * t);
        }

        // ---- The pond ----

        /**
         * How deep the pond is here, or 0 on land. Its banks wander; it is shallow at the edges.
         * Where the bridge goes out it is always open water.
         */
        private int depth(int o, int w) {
            int half = GateLayout.POND_HALF_WIDTH + Math.round(noise(o, 0, 1) * 2.5f);
            int start = 1 + (Math.abs(w) <= 3 ? 0 : Math.round(Math.max(0, noise(0, w, 2)) * 3));
            // Across the bridge's way the far bank is where the bridge lands; beside it the pond reaches a little further.
            int end = layout.length() + (Math.abs(w) <= 3 ? 0 : Math.round(Math.abs(noise(0, w, 3)) * 3));
            if (o < start || o > end || Math.abs(w) > half) {
                return 0;
            }
            int radius = 4;
            int dO = Math.max(0, Math.max(start + radius - o, o - (end - radius)));
            int dW = Math.max(0, Math.abs(w) - (half - radius));
            if (dO * dO + dW * dW > radius * radius) {
                return 0;
            }
            int fromEdge = Math.min(half - Math.abs(w), Math.min(o - start, end - o));
            return Math.clamp(1 + fromEdge, 1, GateLayout.POND_DEPTH);
        }

        private boolean nearWater(int o, int w) {
            for (int dO = -1; dO <= 1; dO++) {
                for (int dW = -1; dW <= 1; dW++) {
                    if (depth(o + dO, w + dW) > 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void pond(WorldGenLevel level, BoundingBox box) {
            int margin = 5;
            int top = GateLayout.WATER_TOP;
            int deepest = top - GateLayout.POND_DEPTH;
            for (int o = 0; o <= layout.length() + margin; o++) {
                for (int w = -GateLayout.POND_HALF_WIDTH - margin; w <= GateLayout.POND_HALF_WIDTH + margin; w++) {
                    int depth = depth(o, w);
                    if (depth > 0) {
                        int floor = top - depth;
                        for (int y = deepest - 1; y <= floor; y++) {
                            BlockPos pos = layout.at(o, w, y);
                            if (y < floor) {
                                if (box.isInside(pos) && !level.getBlockState(pos).isSolid()) {
                                    set(level, box, pos, Blocks.DIRT.defaultBlockState());
                                }
                            } else {
                                float r = chance(o, w, 10);
                                set(level, box, pos, (r < 0.15f ? Blocks.CLAY : r < 0.45f ? Blocks.MUD : r < 0.8f ? Blocks.GRAVEL : Blocks.DIRT).defaultBlockState());
                            }
                        }
                        for (int y = floor + 1; y <= top; y++) {
                            set(level, box, layout.at(o, w, y), Blocks.WATER.defaultBlockState());
                        }
                        for (int y = top + 1; y <= 4; y++) {
                            set(level, box, layout.at(o, w, y), Blocks.AIR.defaultBlockState());
                        }
                    } else if (nearWater(o, w)) {
                        // The bank: trodden and muddy, and never letting the water run off into a cave.
                        for (int y = deepest - 1; y <= GateLayout.DECK_Y; y++) {
                            BlockPos pos = layout.at(o, w, y);
                            if (y == GateLayout.DECK_Y) {
                                float r = chance(o, w, 11);
                                set(level, box, pos, (r < 0.5f ? Blocks.GRASS_BLOCK : r < 0.7f ? Blocks.COARSE_DIRT : r < 0.85f ? Blocks.MUD : Blocks.GRAVEL).defaultBlockState());
                            } else if (box.isInside(pos) && !level.getBlockState(pos).isSolid()) {
                                set(level, box, pos, Blocks.DIRT.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }

        /**
         * Lily pads and weed in the water, grass and a few flowers on the banks; none of it on the bridge's way.
         */
        private void plants(WorldGenLevel level, BoundingBox box) {
            int margin = 5;
            for (int o = 0; o <= layout.length() + margin; o++) {
                for (int w = -GateLayout.POND_HALF_WIDTH - margin; w <= GateLayout.POND_HALF_WIDTH + margin; w++) {
                    boolean underBridge = Math.abs(w) <= 3 && o <= layout.length() + 1;
                    int depth = depth(o, w);
                    float r = chance(o, w, 12);
                    if (depth >= 2 && !underBridge) {
                        BlockPos surface = layout.at(o, w, GateLayout.DECK_Y);
                        if (r < 0.06f && box.isInside(surface) && level.getBlockState(surface).isAir()) {
                            set(level, box, surface, Blocks.LILY_PAD.defaultBlockState());
                        }
                        BlockPos bottom = layout.at(o, w, GateLayout.WATER_TOP - depth + 1);
                        if (chance(o, w, 13) < 0.15f && box.isInside(bottom) && level.getBlockState(bottom).is(Blocks.WATER)) {
                            set(level, box, bottom, Blocks.SEAGRASS.defaultBlockState());
                        }
                    } else if (depth == 0 && !underBridge && nearWater(o, w)) {
                        BlockPos ground = layout.at(o, w, GateLayout.DECK_Y);
                        BlockPos above = ground.above();
                        if (box.isInside(above) && level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && level.getBlockState(above).isAir()) {
                            if (r < 0.25f) {
                                set(level, box, above, Blocks.SHORT_GRASS.defaultBlockState());
                            } else if (r < 0.33f) {
                                set(level, box, above, Blocks.FERN.defaultBlockState());
                            } else if (r < 0.37f) {
                                set(level, box, above, (chance(o, w, 14) < 0.5f ? Blocks.BLUE_ORCHID : Blocks.AZURE_BLUET).defaultBlockState());
                            }
                        }
                    }
                }
            }
        }

        private void square(WorldGenLevel level, BoundingBox box) {
            for (int o = -2; o <= 2; o++) {
                for (int w = -2; w <= 2; w++) {
                    // Worn cobbles; at the corners the grass wins.
                    if (Math.abs(o) == 2 && Math.abs(w) == 2 && chance(o, w, 20) < 0.5f) {
                        continue;
                    }
                    float r = chance(o, w, 21);
                    BlockState stone = (o == 0 && w == 0 ? Blocks.STONE_BRICKS : r < 0.55f ? Blocks.COBBLESTONE : r < 0.8f ? Blocks.MOSSY_COBBLESTONE : Blocks.GRAVEL).defaultBlockState();
                    set(level, box, layout.at(SQUARE_O + o, w, -1), stone);
                }
            }
            for (int[] post : new int[][] {{-2, -2}, {2, 2}}) {
                set(level, box, layout.at(SQUARE_O + post[0], post[1], 0), Blocks.SPRUCE_FENCE.defaultBlockState());
                set(level, box, layout.at(SQUARE_O + post[0], post[1], 1), Blocks.LANTERN.defaultBlockState());
            }
            set(level, box, layout.at(SQUARE_O, 0, 0), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, layout.side()));
        }

        private void paths(WorldGenLevel level, BoundingBox box, List<House> houses) {
            // From the square to the bridge: wandering a little, frayed at the edges, meeting the deck straight.
            for (int o = SQUARE_O + 3; o <= 0; o++) {
                int bend = o < -4 ? Math.round(noise(o, 0, 30) * 1.2f) : 0;
                for (int w = -1; w <= 1; w++) {
                    if (w != 0 && chance(o, w, 31) < 0.3f) {
                        continue;
                    }
                    path(level, box, o, w + bend);
                }
            }
            for (House house : houses) {
                int o = SQUARE_O;
                int w = 0;
                while (o != house.pathO) {
                    o += Integer.signum(house.pathO - o);
                    path(level, box, o, w);
                }
                while (w != house.pathW) {
                    w += Integer.signum(house.pathW - w);
                    path(level, box, o, w);
                }
            }
        }

        private void path(WorldGenLevel level, BoundingBox box, int o, int w) {
            BlockPos pos = surface(level, layout.at(o, w, 0));
            if (!box.isInside(pos) || !(level.getBlockState(pos).is(Blocks.GRASS_BLOCK) || level.getBlockState(pos).is(Blocks.DIRT)
                    || level.getBlockState(pos).is(Blocks.COARSE_DIRT) || level.getBlockState(pos).is(Blocks.MUD))) {
                return;
            }
            float r = chance(o, w, 32);
            set(level, box, pos, (r < 0.75f ? Blocks.DIRT_PATH : r < 0.88f ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState());
            set(level, box, pos.above(), Blocks.AIR.defaultBlockState());
        }

        /**
         * Where houses may stand around the square, each with its door towards it. Three always, two more by chance.
         */
        private static List<House> houses(GateLayout layout, long seed) {
            RandomSource plan = RandomSource.create(seed ^ 0x5EA0F3E3L);
            Direction side = layout.side();
            Direction out = layout.out();
            int[][] slots = {
                    {SQUARE_O, -11, 1}, {SQUARE_O, 11, -1}, {SQUARE_O - 10, -5, 2}, {SQUARE_O - 10, 5, 2}, {-7, -13, 1},
            };
            List<House> houses = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                int[] slot = slots[i];
                boolean stands = i < 3 || plan.nextBoolean();
                Direction front = switch (slot[2]) {
                    case 1 -> side;
                    case -1 -> side.getOpposite();
                    default -> out;
                };
                long houseSeed = plan.nextLong();
                if (!stands) {
                    continue;
                }
                // The door is three blocks in front of the house's middle; the path ends one further.
                int doorO = slot[0] + front.getStepX() * out.getStepX() * 3 + front.getStepZ() * out.getStepZ() * 3;
                int doorW = slot[1] + front.getStepX() * side.getStepX() * 3 + front.getStepZ() * side.getStepZ() * 3;
                int stepO = front.getStepX() * out.getStepX() + front.getStepZ() * out.getStepZ();
                int stepW = front.getStepX() * side.getStepX() + front.getStepZ() * side.getStepZ();
                houses.add(new House(layout.at(doorO, doorW, 0), front, houseSeed, doorO + stepO, doorW + stepW));
            }
            return houses;
        }

        /**
         * The top block of the land at that column, wherever it is: paths and the oak follow the ground.
         */
        private static BlockPos surface(WorldGenLevel level, BlockPos column) {
            // A finished chunk (the place command) keeps the final heightmap, not the worldgen one.
            Heightmap.Types type = level.getChunk(column) instanceof LevelChunk ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
            return new BlockPos(column.getX(), level.getHeight(type, column.getX(), column.getZ()) - 1, column.getZ());
        }

        private static void set(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
            if (!box.isInside(pos)) {
                return;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            // Rails, panes and fences join up once the chunk is done; a finished chunk (the place command) has no such step.
            if (state.getBlock() instanceof CrossCollisionBlock && !(level.getChunk(pos) instanceof LevelChunk)) {
                level.getChunk(pos).markPosForPostProcessing(pos);
            }
        }

        /**
         * A small cottage: planks between log corners, a gable roof, a bed and a workstation, and someone living in it.
         * Laid out from its door: {@code x} across (the door at 3), {@code z} going back into the house.
         */
        record House(BlockPos door, Direction front, long seed, int pathO, int pathW) {
            static final int WIDTH = 7;
            static final int DEPTH = 6;
            private static final List<Supplier<Block>> WORKSTATIONS = List.of(
                    () -> Blocks.BARREL, () -> Blocks.COMPOSTER, () -> Blocks.SMOKER, () -> Blocks.LECTERN,
                    () -> Blocks.CARTOGRAPHY_TABLE, () -> Blocks.FLETCHING_TABLE, () -> Blocks.LOOM, () -> Blocks.STONECUTTER);
            private static final List<Supplier<Block>> BY_THE_DOOR = List.of(
                    () -> Blocks.BARREL, () -> Blocks.HAY_BLOCK, () -> Blocks.COMPOSTER, () -> Blocks.POTTED_POPPY);

            /**
             * The wood a house is built of, and the wood of its roof.
             */
            private record Wood(Block planks, Block log, Block door, Block roof, Block ridge) {
            }

            private static final List<Supplier<Wood>> WOODS = List.of(
                    () -> new Wood(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_DOOR, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS),
                    () -> new Wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS),
                    () -> new Wood(Blocks.BIRCH_PLANKS, Blocks.OAK_LOG, Blocks.BIRCH_DOOR, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS));

            BlockPos at(int x, int y, int z) {
                return door.relative(front.getClockWise(), x - 3).relative(front.getOpposite(), z).above(y);
            }

            void build(WorldGenLevel level, BoundingBox box) {
                RandomSource random = RandomSource.create(seed);
                Direction right = front.getClockWise();
                Direction back = front.getOpposite();
                Wood wood = WOODS.get(random.nextInt(WOODS.size())).get();
                BlockState planks = wood.planks().defaultBlockState();
                BlockState log = wood.log().defaultBlockState();
                BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
                BlockState air = Blocks.AIR.defaultBlockState();

                for (int x = 0; x < WIDTH; x++) {
                    for (int z = 0; z < DEPTH; z++) {
                        boolean edgeX = x == 0 || x == WIDTH - 1;
                        boolean edgeZ = z == 0 || z == DEPTH - 1;
                        boolean edge = edgeX || edgeZ;
                        // An old foundation, mossy in places.
                        BlockState stone = (random.nextFloat() < 0.3f ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
                        set(level, box, at(x, -1, z), edge ? stone : planks);
                        if (edge) {
                            // Down to firm ground.
                            for (int y = -2; y >= -6; y--) {
                                BlockPos below = at(x, y, z);
                                if (!box.isInside(below) || level.getBlockState(below).isSolid()) {
                                    break;
                                }
                                set(level, box, below, stone);
                            }
                        }
                        for (int y = 0; y <= 3; y++) {
                            BlockState state = air;
                            if (edgeX && edgeZ && y <= 2) {
                                state = log;
                            } else if (edge) {
                                boolean window = y == 1 && ((edgeX && (z == 2 || z == 3)) || (z == DEPTH - 1 && x == 3) || (z == 0 && (x == 1 || x == 5)));
                                state = window ? pane : planks;
                            }
                            set(level, box, at(x, y, z), state);
                        }
                    }
                }

                BlockState door = wood.door().defaultBlockState().setValue(DoorBlock.FACING, back);
                set(level, box, at(3, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                set(level, box, at(3, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

                // The roof: a gable over the width, overhanging by one.
                BlockState toRight = wood.roof().defaultBlockState().setValue(StairBlock.FACING, right);
                BlockState toLeft = wood.roof().defaultBlockState().setValue(StairBlock.FACING, right.getOpposite());
                for (int k = 0; k <= 3; k++) {
                    int y = 3 + k;
                    for (int z = -1; z <= DEPTH; z++) {
                        set(level, box, at(-1 + k, y, z), toRight);
                        set(level, box, at(WIDTH - k, y, z), toLeft);
                    }
                    for (int x = k; x <= WIDTH - 1 - k; x++) {
                        set(level, box, at(x, y, 0), planks);
                        set(level, box, at(x, y, DEPTH - 1), planks);
                    }
                }
                for (int z = 0; z < DEPTH; z++) {
                    set(level, box, at(0, 3, z), planks);
                    set(level, box, at(WIDTH - 1, 3, z), planks);
                }
                for (int z = -1; z <= DEPTH; z++) {
                    set(level, box, at(3, 6, z), wood.ridge().defaultBlockState());
                }

                // Inside: a bed along the left wall, a workstation in the far corner, a lantern.
                set(level, box, at(1, 0, 3), Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, back).setValue(BedBlock.PART, BedPart.FOOT));
                set(level, box, at(1, 0, 4), Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, back).setValue(BedBlock.PART, BedPart.HEAD));
                set(level, box, at(5, 0, 4), WORKSTATIONS.get(random.nextInt(WORKSTATIONS.size())).get().defaultBlockState());
                set(level, box, at(5, 0, 1), Blocks.LANTERN.defaultBlockState());

                // Something kept by the door, as people do.
                if (random.nextFloat() < 0.6f) {
                    set(level, box, at(random.nextBoolean() ? 1 : 5, 0, -1), BY_THE_DOOR.get(random.nextInt(BY_THE_DOOR.size())).get().defaultBlockState());
                }

                BlockPos home = at(3, 0, 2);
                if (box.isInside(home)) {
                    Villager villager = EntityTypes.VILLAGER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                    if (villager != null) {
                        villager.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0f, 0f);
                        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(home), EntitySpawnReason.STRUCTURE, null);
                        level.addFreshEntityWithPassengers(villager);
                    }
                }
            }
        }
    }

    /**
     * Only a patch of ground to level, under something that stands in the settlement.
     */
    public static class Footprint extends StructurePiece implements PieceBeardifierModifier {
        Footprint(BoundingBox box) {
            super(FOOTPRINT.get(), 0, box);
        }

        Footprint(CompoundTag tag) {
            super(FOOTPRINT.get(), tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        }

        @Override
        public BoundingBox getBeardifierBox() {
            return boundingBox;
        }

        @Override
        public TerrainAdjustment getTerrainAdjustment() {
            return TerrainAdjustment.BEARD_THIN;
        }

        @Override
        public int getGroundLevelDelta() {
            return 0;
        }
    }
}
