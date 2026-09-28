package com.seaofmemory.overtime;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * During the Overtime every living creature of the real world turns into an ice statue: villagers, animals,
 * zombies and all. They stand where they were, unaware of anything. Only projections move: our own mobs.
 * <p>
 * A statue that is struck shatters, and that creature is gone. Snow people hunt villagers,
 * so the player has something to guard through the long day.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FrozenMobs {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeaOfMemory.MODID);

    // Synced to everyone who sees the mob, so it can be drawn in ice. Saved, so an unloaded statue stays one.
    public static final Supplier<AttachmentType<Boolean>> FROZEN = ATTACHMENT_TYPES.register("frozen", () -> AttachmentType.builder(() -> false)
            .serialize(Codec.BOOL.fieldOf("frozen"), frozen -> frozen)
            .sync(ByteBufCodecs.BOOL)
            .build());
    // Marks mobs we silenced, so thawing only gives back a voice we took.
    private static final String SILENCED_TAG = SeaOfMemory.MODID + ".silenced";

    private FrozenMobs() {
    }

    public static boolean isFrozen(Entity entity) {
        return Boolean.TRUE.equals(entity.getExistingDataOrNull(FROZEN.get()));
    }

    private static boolean canFreeze(Mob mob) {
        // Projections keep moving; so does anything carrying a rider, or the rider would be stuck with it.
        // A mob someone else already stopped (a map maker, say) is left alone, so thawing cannot undo their choice.
        return mob.isAlive() && !mob.isVehicle() && !mob.isNoAi()
                && !SeaOfMemory.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace());
    }

    static void freeze(Mob mob) {
        if (!isFrozen(mob)) {
            if (!canFreeze(mob)) {
                return;
            }
            mob.setData(FROZEN.get(), true);
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        }
        // Applied every time, not only on the first freeze, so a statue saved by an older version,
        // or one whose state something else touched, is brought fully back to ice when it loads.
        mob.setNoAi(true);
        // Ice makes no sound.
        if (!mob.isSilent()) {
            mob.setSilent(true);
            mob.addTag(SILENCED_TAG);
        }
    }

    /**
     * Brings a statue back to life before the Overtime is over, as a detransmogrifier's light does.
     * It stays awake until its chunk is reloaded during the Overtime.
     */
    public static void wake(Mob mob) {
        if (isFrozen(mob) && mob.level() instanceof ServerLevel level) {
            thaw(mob);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                    mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 20, mob.getBbWidth() / 2, mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.05);
            level.playSound(null, mob.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.6f, 1.4f);
        }
    }

    static void thaw(Mob mob) {
        if (!isFrozen(mob)) {
            return;
        }
        mob.setData(FROZEN.get(), false);
        mob.setNoAi(false);
        if (mob.removeTag(SILENCED_TAG)) {
            mob.setSilent(false);
        }
    }

    static void freezeAllLoaded(ServerLevel level) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> true)) {
            freeze(mob);
        }
    }

    static void thawAllLoaded(ServerLevel level) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), FrozenMobs::isFrozen)) {
            thaw(mob);
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        // Anything loaded or spawned during the Overtime freezes; statues saved during one thaw once it is over.
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (Overtime.isActive(level)) {
            freeze(mob);
        } else {
            thaw(mob);
        }
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level && isFrozen(mob)) {
            event.setCanceled(true);
            shatter(level, mob);
        }
    }

    /**
     * Some mobs never get as far as taking damage from a projectile: an enderman teleports away first.
     * A statue cannot dodge, so the hit is caught at the impact instead; the projectile flies on through the shards.
     */
    @SubscribeEvent
    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof Mob mob
                && mob.level() instanceof ServerLevel level && isFrozen(mob)) {
            event.setCanceled(true);
            shatter(level, mob);
        }
    }

    @SubscribeEvent
    static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        // Endermen and shulkers blink away on their own; ice stays put.
        if (event.getEntityLiving() instanceof Mob mob && isFrozen(mob)) {
            event.setCanceled(true);
        }
    }

    // Ice does not bleed. One blow and it is gone.
    private static void shatter(ServerLevel level, Mob mob) {
        if (mob.isRemoved()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 60, mob.getBbWidth() / 2, mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.1);
        level.playSound(null, mob.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2f, 0.7f);
        mob.discard();
    }
}
