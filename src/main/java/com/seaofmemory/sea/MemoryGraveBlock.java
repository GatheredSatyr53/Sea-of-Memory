package com.seaofmemory.sea;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.entity.ModEntities;
import com.seaofmemory.entity.SnowPerson;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a player's belongings stay when they die in the fog. Memory does not give things back without a fight:
 * the first touch wakes guardians out of the fog, and only once none of them is left does the grave open,
 * spilling everything it held and crumbling. It cannot be broken, only opened; anyone can open it.
 */
public class MemoryGraveBlock extends BaseEntityBlock {
    public static final MapCodec<MemoryGraveBlock> CODEC = simpleCodec(MemoryGraveBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 4, 14, 14, 12);

    private static final int MIN_GUARDIANS = 3;
    private static final int MAX_GUARDIANS = 5;
    // One more guardian for every this many stacks it keeps.
    private static final int STACKS_PER_GUARDIAN = 12;
    private static final int GUARDIAN_MIN_DISTANCE = 4;
    private static final int GUARDIAN_MAX_DISTANCE = 8;
    private static final int MAX_DROP = 64;
    private static final int MAX_CLIMB = 24;

    public MemoryGraveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MemoryGraveBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Raises a grave holding the belongings where they were lost, and returns where it stands.
     * On the ground under that place if it was in the air, or the first free space above it if it is taken.
     */
    static BlockPos place(ServerLevel fog, BlockPos lostAt, List<ItemStack> belongings) {
        LevelChunk chunk = fog.getChunk(SectionPos.blockToSectionCoord(lostAt.getX()), SectionPos.blockToSectionCoord(lostAt.getZ()));
        // Imprint first: imprinting later would take the grave for someone's building and overwrite it.
        MemoryImprint.imprintBlocking(fog.getServer(), fog, chunk);
        BlockPos pos = findSpot(fog, lostAt);
        fog.setBlockAndUpdate(pos, SeaOfMemory.MEMORY_GRAVE.get().defaultBlockState());
        if (fog.getBlockEntity(pos) instanceof MemoryGraveBlockEntity grave) {
            grave.fill(belongings);
        }
        return pos;
    }

    private static BlockPos findSpot(ServerLevel fog, BlockPos lostAt) {
        int y = Mth.clamp(lostAt.getY(), fog.getMinY() + 1, fog.getMaxY() - 1);
        BlockPos pos = new BlockPos(lostAt.getX(), y, lostAt.getZ());
        // Died in a fall or a leap: settle on whatever is below, but not through water.
        for (int fell = 0; fell < MAX_DROP && fog.getBlockState(pos.below()).isAir() && pos.getY() > fog.getMinY() + 1; fell++) {
            pos = pos.below();
        }
        for (int climbed = 0; climbed < MAX_CLIMB && !fog.getBlockState(pos).canBeReplaced(); climbed++) {
            pos = pos.above();
        }
        return pos;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof MemoryGraveBlockEntity grave)) {
            return InteractionResult.SUCCESS;
        }
        if (!grave.isWoken()) {
            wakeGuardians(serverLevel, pos, grave, player);
        } else if (grave.guarded(serverLevel)) {
            player.sendOverlayMessage(Component.translatable("seaofmemory.grave.guarded"));
        } else {
            open(serverLevel, pos, grave);
        }
        return InteractionResult.SUCCESS;
    }

    private static void wakeGuardians(ServerLevel level, BlockPos pos, MemoryGraveBlockEntity grave, Player player) {
        int count = Mth.clamp(MIN_GUARDIANS + grave.itemCount() / STACKS_PER_GUARDIAN, MIN_GUARDIANS, MAX_GUARDIANS);
        RandomSource random = level.getRandom();
        List<UUID> woken = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Mth.nextDouble(random, GUARDIAN_MIN_DISTANCE, GUARDIAN_MAX_DISTANCE);
            int x = Mth.floor(pos.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(pos.getZ() + 0.5 + Math.sin(angle) * distance);
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            SnowPerson guardian = ModEntities.SNOW_PERSON.get().spawn(level, at, EntitySpawnReason.EVENT);
            if (guardian == null) {
                continue;
            }
            // They stay until they are dealt with; the grave does not open while any of them is left.
            guardian.setPersistenceRequired();
            guardian.setTarget(player);
            level.sendParticles(ParticleTypes.CLOUD, guardian.getX(), guardian.getY() + 1, guardian.getZ(), 20, 0.4, 0.6, 0.4, 0.01);
            woken.add(guardian.getUUID());
        }
        grave.wake(woken);
        level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1f, 0.5f);
        player.sendOverlayMessage(Component.translatable("seaofmemory.grave.woken"));
    }

    private static void open(ServerLevel level, BlockPos pos, MemoryGraveBlockEntity grave) {
        for (ItemStack stack : grave.items()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
        }
        grave.fill(List.of());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1f, 0.7f);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A faint drift of souls, so it can be found in the fog from close by.
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.03, 0);
        }
    }
}
