package com.seaofmemory.dtm;

import com.mojang.serialization.MapCodec;
import com.seaofmemory.entity.PlushHare;
import com.seaofmemory.entity.SnowPerson;
import com.seaofmemory.overtime.FrozenMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A detransmogrifier lamp ("ДТМ") from "Овертайм": its turquoise light weakens projections, so snow people
 * melt in it and back away, and the plush hare grows weak and slow.
 * <p>
 * But it also wakes the frozen. During the Overtime every statue in its light comes back to life:
 * zombies hunt again, villagers panic. It protects from the fog and undoes the ice, both at once.
 */
public class DtmLampBlock extends Block {
    public static final MapCodec<DtmLampBlock> CODEC = simpleCodec(DtmLampBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.column(8, 0, 16);

    private static final int PULSE_TICKS = 20;
    private static final double RADIUS = 8;
    private static final float MELT_DAMAGE = 3f;
    private static final int WEAKEN_TICKS = PULSE_TICKS * 2;
    private static final int TURQUOISE = 0x2EC4B6;

    public DtmLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, pos, lit ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.6f);
            if (lit) {
                serverLevel.scheduleTick(pos, this, PULSE_TICKS);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        pulse(level, pos);
        // Scheduled ticks are saved with the chunk, so the lamp keeps working after a reload without a block entity.
        level.scheduleTick(pos, this, PULSE_TICKS);
    }

    private static void pulse(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(RADIUS);
        double radiusSq = RADIUS * RADIUS;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob.distanceToSqr(center) <= radiusSq)) {
            if (mob instanceof SnowPerson snowPerson) {
                snowPerson.hurtServer(level, level.damageSources().magic(), MELT_DAMAGE);
                snowPerson.shyFrom(center);
            } else if (mob instanceof PlushHare hare) {
                hare.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKEN_TICKS, 1, true, true));
                hare.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WEAKEN_TICKS, 1, true, true));
            } else if (FrozenMobs.isFrozen(mob)) {
                FrozenMobs.wake(mob);
            }
        }
        level.sendParticles(new DustParticleOptions(TURQUOISE, 1.2f), center.x, center.y + 0.3, center.z, 6, 0.3, 0.3, 0.3, 0);
    }
}
