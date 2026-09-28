package com.seaofmemory.overtime;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The black gothic clock at the shore end of the Gates, lit turquoise. It cannot be broken.
 * During the Overtime it tells how long until the next wave and how much strength the Gates have left.
 * It exists only while the bridge stands whole: when the Overtime ends it goes back to a stump with the rest of the ruin.
 */
public class GateClockBlock extends Block {
    public static final MapCodec<GateClockBlock> CODEC = simpleCodec(GateClockBlock::new);
    private static final VoxelShape SHAPE = Block.column(10, 0, 16);
    private static final int TURQUOISE = 0x2EC4B6;

    public GateClockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (Overtime.isActive(serverLevel)) {
            player.sendOverlayMessage(Gates.status(serverLevel, pos));
        } else {
            // A clock left behind outside the Overtime (an older save, a placed one): it crumbles at a touch.
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.playSound(null, pos, SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.BLOCKS, 1f, 0.6f);
            player.sendOverlayMessage(Component.translatable("seaofmemory.gates.silent"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(TURQUOISE, 0.8f), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 1.4 + random.nextDouble() * 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
        }
    }
}
