package com.seaofmemory.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.seaofmemory.cold.Cold;
import com.seaofmemory.scene.Scenes;
import com.seaofmemory.scene.SilhouettePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The plush hare from "Овертайм": a huge, balding toy that someone's childhood fear dragged out of the fog.
 * <p>
 * At first it is clumsy and slow, hopping crookedly. Once half its stuffing is gone it tears open:
 * eight spider legs burst out of the fur, the empty eye socket lights up red, and it runs and climbs.
 * The empty socket is its weak point.
 */
public class PlushHare extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_TRANSFORMED = SynchedEntityData.defineId(PlushHare.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(PlushHare.class, EntityDataSerializers.BOOLEAN);

    private static final float TRANSFORM_AT_HEALTH = 0.5f;
    private static final double TRANSFORMED_SPEED = 0.34;
    private static final double TRANSFORMED_DAMAGE = 7;
    private static final float WEAK_POINT_MULTIPLIER = 2.5f;
    // Radius of the head around the empty socket, in blocks.
    private static final double HEAD_RADIUS = 0.7;
    private static final double MELEE_REACH = 5;
    private static final int HOP_INTERVAL = 25;
    private static final float HIT_COLD = 5f;
    // Players this close to the torn-open hare are pulled into its scene, and stay in it until it is gone.
    private static final double SCENE_RADIUS = 24;
    private static final int SCENE_JOIN_INTERVAL = 20;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(random), getDisplayName(),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final Set<UUID> scenePlayers = new HashSet<>();

    public PlushHare(EntityType<? extends PlushHare> type, Level level) {
        super(type, level);
        xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45f) {
            @Override
            public boolean canUse() {
                return isTransformed() && super.canUse();
            }
        });
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_TRANSFORMED, false);
        entityData.define(DATA_CLIMBING, false);
    }

    public boolean isTransformed() {
        return entityData.get(DATA_TRANSFORMED);
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(DATA_CLIMBING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            entityData.set(DATA_CLIMBING, isTransformed() && horizontalCollision);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!isTransformed() && getHealth() <= getMaxHealth() * TRANSFORM_AT_HEALTH) {
            transform(level);
        }
        if (isTransformed() && tickCount % SCENE_JOIN_INTERVAL == 0) {
            joinScene(level);
        }
        LivingEntity target = getTarget();
        if (!isTransformed() && target != null && onGround() && tickCount % HOP_INTERVAL == 0) {
            hopTowards(target);
        }
    }

    /**
     * An awkward, lopsided hop in the general direction of the target.
     */
    private void hopTowards(LivingEntity target) {
        Vec3 direction = target.position().subtract(position()).multiply(1, 0, 1).normalize();
        double sideways = (random.nextDouble() - 0.5) * 0.3;
        setDeltaMovement(direction.x * 0.35 - direction.z * sideways, 0.42, direction.z * 0.35 + direction.x * sideways);
        playSound(SoundEvents.WOOL_STEP, 1f, 0.6f);
    }

    private void transform(ServerLevel level) {
        entityData.set(DATA_TRANSFORMED, true);
        applyTransformedStats();
        bossEvent.setColor(BossEvent.BossBarColor.RED);

        level.playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
        level.playSound(null, blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2f, 0.4f);
        burstStuffing(level, 60);
        joinScene(level);
    }

    /**
     * From the moment it tears open, everyone nearby sees the world in silhouettes until the hare is defeated.
     * Checked repeatedly, so players who arrive later, or rejoin, are drawn in too.
     */
    private void joinScene(ServerLevel level) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(SCENE_RADIUS))) {
            if (scenePlayers.add(player.getUUID())) {
                Scenes.silhouette(player, SilhouettePayload.UNTIL_STOPPED);
            }
        }
    }

    private void endScene() {
        if (level() instanceof ServerLevel level) {
            for (UUID id : scenePlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    Scenes.silhouette(player, SilhouettePayload.OFF);
                }
            }
        }
        scenePlayers.clear();
    }

    private void applyTransformedStats() {
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(TRANSFORMED_SPEED);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(TRANSFORMED_DAMAGE);
        navigation = new WallClimberNavigation(this, level());
    }

    private void burstStuffing(ServerLevel level, int count) {
        BlockState stuffing = Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stuffing), getX(), getY() + getBbHeight() / 2, getZ(),
                count, getBbWidth() / 2, getBbHeight() / 3, getBbWidth() / 2, 0.1);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (hitsWeakPoint(source)) {
            damage *= WEAK_POINT_MULTIPLIER;
            level.playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.HOSTILE, 1.5f, 1.4f);
            Vec3 head = headCenter();
            level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 12, 0.2, 0.2, 0.2, 0.2);
        }
        return super.hurtServer(level, source, damage);
    }

    /**
     * The head sits in front of the body, so it is placed from the body's facing, not from the eye height alone.
     */
    private Vec3 headCenter() {
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(forward.scale(getBbWidth() * 0.55)).add(0, getEyeHeight(), 0);
    }

    private boolean hitsWeakPoint(DamageSource source) {
        Vec3 head = headCenter();
        if (source.getDirectEntity() instanceof Projectile projectile) {
            return projectile.position().distanceTo(head) <= HEAD_RADIUS + 0.3;
        }
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            Vec3 eye = player.getEyePosition();
            Vec3 reach = eye.add(player.getLookAngle().scale(MELEE_REACH));
            return new AABB(head, head).inflate(HEAD_RADIUS).clip(eye, reach).isPresent();
        }
        return false;
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
        endScene();
        if (level() instanceof ServerLevel level) {
            // The seams give way and the stuffing spills out like lifeless confetti.
            burstStuffing(level, 120);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // Despawned, unloaded or killed by a command: nobody may be left stuck in the scene.
        endScene();
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Transformed", isTransformed());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (input.getBooleanOr("Transformed", false)) {
            entityData.set(DATA_TRANSFORMED, true);
            applyTransformedStats();
            bossEvent.setColor(BossEvent.BossBarColor.RED);
        }
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isTransformed() ? SoundEvents.SPIDER_AMBIENT : null;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.5f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOL_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOL_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(isTransformed() ? SoundEvents.SPIDER_STEP : SoundEvents.WOOL_STEP, 0.4f, 0.7f);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        // It is a stuffed toy.
        return false;
    }
}
