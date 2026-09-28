package com.seaofmemory.entity;

import com.seaofmemory.cold.Cold;
import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;

import java.util.EnumSet;

/**
 * "Снежные люди" from "Огонёк": pale, faceless figures moulded out of wet snow and someone's grief.
 * They drift towards light, warmth and noise, crawl over walls and roofs, and their blank stare
 * alone makes a person colder (see ColdTicker). They melt away once the fog thins out.
 * <p>
 * Drawn to warmth, they still cannot bear it up close: they shy away from fire and lava and end up
 * crowding at the edge of its light. They hunt villagers as well as players.
 */
public class SnowPerson extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(SnowPerson.class, EntityDataSerializers.BOOLEAN);

    // Below this fog density they start to melt; the fog world (0.6) keeps them whole.
    private static final float MELT_BELOW = 0.55f;
    private static final float MELT_DAMAGE = 2f;
    private static final double GAZE_RANGE = 12;
    private static final float HIT_COLD = 8f;
    // How long they keep heading for a light or a noise before losing interest.
    private static final int LURE_TICKS = 400;
    private static final int LIGHT_SCAN_RADIUS = 16;
    private static final int LIGHT_SCAN_SAMPLES = 16;
    private static final int ATTRACTIVE_LIGHT = 10;
    // Closer than this to fire or lava they back off.
    private static final int HEAT_RADIUS = 3;
    private static final int RETREAT_DISTANCE = 8;
    private static final double RETREAT_SPEED = 1.2;

    private BlockPos lure;
    private int lureTicks;

    public SnowPerson(EntityType<? extends SnowPerson> type, Level level) {
        super(type, level);
        // Never plan a path through or right next to fire or lava.
        setPathfindingMalus(PathType.FIRE, -1f);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1f);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, -1f);
        setPathfindingMalus(PathType.LAVA, -1f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ShyFromHeatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(3, new FollowLureGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, (float) GAZE_RANGE));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CLIMBING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            entityData.set(DATA_CLIMBING, horizontalCollision);
        }
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(DATA_CLIMBING);
    }

    /**
     * Draws this one towards a light or a noise at the given position.
     */
    public void lure(BlockPos pos) {
        lure = pos.immutable();
        lureTicks = LURE_TICKS;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (lureTicks > 0 && --lureTicks == 0) {
            lure = null;
        }
        if (tickCount % 40 == 0 && lure == null && getTarget() == null) {
            seekLight(level);
        }
        if (tickCount % 20 == 0) {
            meltIfFogThins(level);
        }
    }

    /**
     * They go towards light the way the frozen go towards a stove.
     */
    private void seekLight(ServerLevel level) {
        BlockPos brightest = null;
        int brightestLight = ATTRACTIVE_LIGHT - 1;
        for (int i = 0; i < LIGHT_SCAN_SAMPLES; i++) {
            BlockPos pos = blockPosition().offset(
                    random.nextIntBetweenInclusive(-LIGHT_SCAN_RADIUS, LIGHT_SCAN_RADIUS),
                    random.nextIntBetweenInclusive(-4, 4),
                    random.nextIntBetweenInclusive(-LIGHT_SCAN_RADIUS, LIGHT_SCAN_RADIUS));
            int light = level.getBrightness(LightLayer.BLOCK, pos);
            if (light > brightestLight) {
                brightest = pos;
                brightestLight = light;
            }
        }
        if (brightest != null) {
            lure(brightest);
        }
    }

    private void meltIfFogThins(ServerLevel level) {
        if (CognitiveFog.densityAt(level, blockPosition()) < MELT_BELOW) {
            level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.5, 0.3, 0);
            hurtServer(level, damageSources().generic(), MELT_DAMAGE);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof Player player) {
            Cold.set(player, Cold.get(player) + HIT_COLD);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            // Falls apart like wet cotton wool, without a sound of its own.
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() / 2, getZ(), 40, 0.3, 0.6, 0.3, 0.05);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SNOW_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SNOW_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SNOW_STEP, 0.3f, 0.8f);
    }

    /**
     * Backs away from a source of something it cannot bear, such as a detransmogrifier's light.
     */
    public void shyFrom(Vec3 source) {
        Vec3 away = DefaultRandomPos.getPosAway(this, RETREAT_DISTANCE, 4, source);
        if (away != null) {
            lure = null;
            lureTicks = 0;
            getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
        }
    }

    /**
     * The nearest fire, lava or other heat source within {@link #HEAT_RADIUS}, if any.
     */
    private BlockPos nearbyHeat() {
        BlockPos center = blockPosition();
        BlockPos nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HEAT_RADIUS, -HEAT_RADIUS, -HEAT_RADIUS), center.offset(HEAT_RADIUS, HEAT_RADIUS, HEAT_RADIUS))) {
            BlockState state = level().getBlockState(pos);
            boolean hot = state.is(Cold.WARMTH_SOURCES) && state.getValueOrElse(BlockStateProperties.LIT, true)
                    || state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA);
            double distanceSq = pos.distSqr(center);
            if (hot && distanceSq < nearestSq) {
                nearest = pos.immutable();
                nearestSq = distanceSq;
            }
        }
        return nearest;
    }

    /**
     * Backs away from heat, even in the middle of a chase.
     */
    private static final class ShyFromHeatGoal extends Goal {
        private final SnowPerson mob;

        ShyFromHeatGoal(SnowPerson mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.tickCount % 10 != 0) {
                return false;
            }
            BlockPos heat = mob.nearbyHeat();
            if (heat == null) {
                return false;
            }
            Vec3 away = DefaultRandomPos.getPosAway(mob, RETREAT_DISTANCE, 4, Vec3.atCenterOf(heat));
            return away != null && mob.getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }
    }

    /**
     * Walks towards the current lure, then stands there staring until it loses interest.
     */
    private static final class FollowLureGoal extends Goal {
        private final SnowPerson mob;

        FollowLureGoal(SnowPerson mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.lure != null && mob.getTarget() == null;
        }

        @Override
        public void tick() {
            if (mob.lure != null && mob.tickCount % 10 == 0 && mob.blockPosition().distSqr(mob.lure) > 4) {
                mob.getNavigation().moveTo(mob.lure.getX() + 0.5, mob.lure.getY(), mob.lure.getZ() + 0.5, 0.9);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}
