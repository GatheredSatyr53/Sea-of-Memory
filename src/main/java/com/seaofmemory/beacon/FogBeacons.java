package com.seaofmemory.beacon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.sea.FogWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Active fog beacons in the real world, and what they do for those lost in the fog:
 * a hum they can follow from far away, a pillar of light once they are close,
 * and a way out when they reach the spot where the beacon stands.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogBeacons extends SavedData {
    // Horizontal range, in blocks, at which the lost can hear a beacon.
    private static final double RANGE = 160;
    // How close to the beacon's position the lost have to get to walk out.
    private static final double RESCUE_DISTANCE = 2.5;
    private static final int GUIDE_INTERVAL = 20;
    private static final int SOUND_INTERVAL = 60;
    private static final int PILLAR_HEIGHT = 24;

    private static final Codec<FogBeacons> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("beacons").forGetter(data -> data.beacons)
    ).apply(i, FogBeacons::new));
    private static final SavedDataType<FogBeacons> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "fog_beacons"), FogBeacons::new, CODEC);

    private final List<BlockPos> beacons;

    private FogBeacons() {
        this(List.of());
    }

    private FogBeacons(List<BlockPos> beacons) {
        this.beacons = new ArrayList<>(beacons);
    }

    private static FogBeacons get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Only beacons in the real world reach into the fog, since the fog world is its copy.
     */
    static void setActive(ServerLevel level, BlockPos pos, boolean active) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        FogBeacons data = get(level.getServer());
        BlockPos key = pos.immutable();
        boolean changed = active ? !data.beacons.contains(key) && data.beacons.add(key) : data.beacons.remove(key);
        if (changed) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % GUIDE_INTERVAL != 0) {
            return;
        }
        ServerLevel fog = server.getLevel(FogWorld.KEY);
        if (fog == null || fog.players().isEmpty()) {
            return;
        }
        FogBeacons data = get(server);
        data.forgetBroken(server.overworld());
        boolean playSound = server.getTickCount() % SOUND_INTERVAL == 0;
        // Copy: rescuing a player moves them out of the fog world's player list.
        for (ServerPlayer player : List.copyOf(fog.players())) {
            BlockPos beacon = data.nearest(player);
            if (beacon != null) {
                guide(fog, player, beacon, playSound);
            }
        }
    }

    /**
     * Drops beacons that were broken or switched off in some other way. Unloaded ones are kept as they are.
     */
    private void forgetBroken(ServerLevel overworld) {
        Iterator<BlockPos> it = beacons.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (overworld.isLoaded(pos) && !FogBeaconBlock.isActive(overworld.getBlockState(pos))) {
                it.remove();
                setDirty();
            }
        }
    }

    private BlockPos nearest(ServerPlayer player) {
        BlockPos nearest = null;
        double nearestSq = RANGE * RANGE;
        for (BlockPos pos : beacons) {
            double distanceSq = horizontalDistanceSq(player, pos);
            if (distanceSq <= nearestSq) {
                nearest = pos;
                nearestSq = distanceSq;
            }
        }
        return nearest;
    }

    private static double horizontalDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private static void guide(ServerLevel fog, ServerPlayer player, BlockPos beacon, boolean playSound) {
        double x = beacon.getX() + 0.5;
        double z = beacon.getZ() + 0.5;
        if (horizontalDistanceSq(player, beacon) <= RESCUE_DISTANCE * RESCUE_DISTANCE && rescue(player, beacon)) {
            return;
        }
        for (int dy = -4; dy <= PILLAR_HEIGHT; dy += 2) {
            fog.sendParticles(player, ParticleTypes.END_ROD, true, true, x, player.getY() + dy, z, 1, 0.15, 0.5, 0.15, 0.0);
        }
        if (playSound) {
            // Louder than 1 carries further: a volume of 10 is heard about 160 blocks away.
            float volume = (float) (RANGE / 16);
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_AMBIENT),
                    SoundSource.BLOCKS, x, player.getY(), z, volume, 0.7f, player.getRandom().nextLong()));
        }
    }

    private static boolean rescue(ServerPlayer player, BlockPos beacon) {
        ServerLevel overworld = player.level().getServer().overworld();
        // Load the chunk: the beacon must really still be there and shining.
        if (!FogBeaconBlock.isActive(overworld.getBlockState(beacon))) {
            return false;
        }
        if (!FogWorld.release(player, beacon.east())) {
            return false;
        }
        overworld.playSound(null, beacon, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.8f);
        return true;
    }
}
