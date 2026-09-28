package com.seaofmemory.overtime;

import java.util.List;
import java.util.function.Consumer;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.ModEntities;
import com.seaofmemory.entity.SnowPerson;
import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Game tests for the Gates' settlement: it generates, its ruin is where the Gates expect it, the Gates rise
 * over it and fall back into it without leaving anything lying about.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class GateTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, SeaOfMemory.MODID);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GATE_VILLAGE =
            TEST_FUNCTIONS.register("gate_village", () -> GateTests::gateVillage);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> GATE_WAVE_WALK =
            TEST_FUNCTIONS.register("gate_wave_walk", () -> GateTests::gateWaveWalk);
    // Far enough from the test's own plot that the settlement has the ground to itself.
    private static final int DISTANCE = 400;
    private static final int WALK_DISTANCE = 800;

    private GateTests() {
    }

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(id("gate_village"), new FunctionGameTestInstance(GATE_VILLAGE.getKey(),
                new TestData<>(environment, id("empty"), 100, 0, true)));
        event.registerTest(id("gate_wave_walk"), new FunctionGameTestInstance(GATE_WAVE_WALK.getKey(),
                new TestData<>(environment, id("empty"), 1200, 0, true)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, path);
    }

    private static void gateVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = build(helper, DISTANCE);
        BoundingBox bounds = start.getBoundingBox();
        GateLayout layout = layoutOf(helper, start);
        check(helper, level.getBlockState(layout.clock()).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "No clock stump at " + layout.clock());
        BlockPos bell = layout.at(GateVillage.SQUARE_O, 0, 0);
        check(helper, level.getBlockState(bell).is(Blocks.BELL), "No bell at " + bell);
        AABB area = AABB.of(bounds);
        int villagers = level.getEntitiesOfClass(Villager.class, area).size();
        check(helper, villagers >= 3, "Only " + villagers + " villagers");

        Gates.raise(level, layout);
        check(helper, level.getBlockState(layout.clock()).is(SeaOfMemory.GATE_CLOCK.get()), "The clock did not rise");
        check(helper, level.getBlockState(layout.at(layout.length(), 0, GateLayout.DECK_Y)).is(Blocks.DEEPSLATE_TILES), "The deck does not reach the far bank");
        BlockPos farBank = layout.farEnd().below();
        check(helper, level.getBlockState(farBank).isSolid() && level.getBlockState(farBank.above()).isAir(), "The far bank is not level with the deck at " + farBank + ": " + level.getBlockState(farBank) + " under " + level.getBlockState(farBank.above()));
        // One steps from the path onto the deck without climbing.
        BlockPos shore = layout.at(0, 0, GateLayout.DECK_Y);
        check(helper, level.getBlockState(shore).isSolid() && level.getBlockState(shore.above()).isAir(), "The shore is not level with the deck at " + shore);
        check(helper, level.getBlockState(layout.at(1, 0, GateLayout.DECK_Y)).is(Blocks.DEEPSLATE_TILES), "The deck does not start at the shore");

        BlockPos lintel = layout.at(layout.length() + 1, 0, GateLayout.DECK_Y + GateLayout.ARCH_HEIGHT + 1);
        check(helper, level.getBlockState(lintel).is(Blocks.DEEPSLATE_BRICKS), "No arch over the far end at " + lintel);
        Gates.lower(level, layout);
        check(helper, level.getBlockState(layout.clock()).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "The clock did not go back to a stump");
        int dropped = level.getEntitiesOfClass(ItemEntity.class, area).size();
        check(helper, dropped == 0, dropped + " items dropped as the Gates fell back into the ruin");

        SeaOfMemory.LOGGER.info("Gate settlement test: origin {}, clock {}, {} villagers", layout.at(0, 0, 0), layout.clock(), villagers);
        helper.succeed();
    }

    /**
     * A snow person of a wave, let out of the arch among the settlement's people, gets across the bridge to the clock.
     */
    private static void gateWaveWalk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = build(helper, WALK_DISTANCE);
        GateLayout layout = layoutOf(helper, start);
        Gates.raise(level, layout);
        // Thick fog over it all, or the snow person would melt away in the test's clear air.
        BoundingBox bounds = start.getBoundingBox();
        ChunkPos.rangeClosed(ChunkPos.containing(new BlockPos(bounds.minX(), 0, bounds.minZ())), ChunkPos.containing(new BlockPos(bounds.maxX(), 0, bounds.maxZ())))
                .forEach(chunk -> {
                    // Kept loaded and ticking with no player around.
                    level.setChunkForced(chunk.x(), chunk.z(), true);
                    CognitiveFog.setDensity(level.getChunk(chunk.x(), chunk.z()), 1f);
                });
        // Two, side by side: mobs run their goals on odd or even ticks by their id, so between them both kinds are tried.
        BlockPos arch = layout.farEnd().relative(layout.out().getOpposite());
        List<SnowPerson> ghosts = List.of(
                ModEntities.SNOW_PERSON.get().spawn(level, arch, EntitySpawnReason.EVENT),
                ModEntities.SNOW_PERSON.get().spawn(level, arch.relative(layout.side()), EntitySpawnReason.EVENT));
        check(helper, ghosts.stream().allMatch(ghost -> ghost != null), "Could not spawn the snow people");
        Vec3 clock = Vec3.atCenterOf(layout.clock());
        helper.onEachTick(() -> {
            if (level.getGameTime() % 20 == 0) {
                ghosts.stream().filter(SnowPerson::isAlive).forEach(ghost -> Gates.drive(level, layout, ghost));
            }
        });
        helper.succeedWhen(() -> {
            for (SnowPerson ghost : ghosts) {
                if (!ghost.isAlive()) {
                    throw helper.assertionException(Component.literal("A snow person is gone: " + ghost.getRemovalReason()));
                }
                double distance = ghost.position().distanceTo(clock);
                if (distance > Gates.BREACH_DISTANCE) {
                    throw helper.assertionException(Component.literal(String.format("Snow person %d is %.1f from the clock at %s, target %s", ghost.getId(),
                            distance, ghost.blockPosition(), ghost.getTarget() == null ? "none" : ghost.getTarget().getName().getString())));
                }
            }
        });
    }

    private static StructureStart build(GameTestHelper helper, int distance) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(GateVillage.KEY);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BlockPos at = helper.absolutePos(BlockPos.ZERO).offset(distance, 0, 0);
        // As the place command does it, but loading the chunks it needs.
        StructureStart start = structure.value().generate(structure, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), ChunkPos.containing(at), 0, level, biome -> true);
        if (!start.isValid()) {
            throw helper.assertionException(Component.literal("The settlement did not generate"));
        }
        BoundingBox bounds = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bounds.minX()), SectionPos.blockToSectionCoord(bounds.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bounds.maxX()), SectionPos.blockToSectionCoord(bounds.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> {
            level.getChunk(chunk.x(), chunk.z());
            start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY() + 1, chunk.getMaxBlockZ()), chunk);
        });
        return start;
    }

    private static GateLayout layoutOf(GameTestHelper helper, StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof GateVillage.Piece village) {
                return village.layout();
            }
        }
        throw helper.assertionException(Component.literal("No settlement piece"));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(Component.literal(message));
        }
    }
}
