package com.seaofmemory.overtime;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Овертайм": once every few in-game days, at midnight, the real world skips a day. Time stops and the sun
 * does not come back for a whole extra day. Every living creature freezes into ice (see FrozenMobs).
 * The fog itself stays as it was, but reality turns cognitive: everything feels the fog denser
 * (see CognitiveFog), and more of the snow people rise. When it ends the shift is gone at once,
 * and those left in thin fog melt or fall apart.
 * <p>
 * Nothing is to be won: the player only has to hold on, and keep the frozen villagers from being broken,
 * until the world breathes out again.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class Overtime extends SavedData {
    private static final long DAY_TICKS = 24000;
    private static final long MIDNIGHT = 18000;
    // The sky stays clear a little after the Overtime too, so rain does not fall the moment the world breathes out.
    private static final int CLEAR_AFTER = 1200;

    private static final Codec<Overtime> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("next_day").forGetter(o -> o.nextDay),
            Codec.BOOL.fieldOf("active").forGetter(o -> o.active),
            Codec.LONG.fieldOf("ends_at").forGetter(o -> o.endsAt)
    ).apply(i, Overtime::new));
    private static final SavedDataType<Overtime> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "overtime"), Overtime::new, CODEC);

    // The day on which the next Overtime falls; 0 until the first check schedules it.
    private long nextDay;
    private boolean active;
    // Game time (which keeps running while the clock is stopped) at which it ends.
    private long endsAt;

    private Overtime() {
        this(0, false, 0);
    }

    private Overtime(long nextDay, boolean active, long endsAt) {
        this.nextDay = nextDay;
        this.active = active;
        this.endsAt = endsAt;
    }

    private static Overtime get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isActive(MinecraftServer server) {
        return get(server).active;
    }

    /**
     * Whether the level is inside an Overtime right now. Only the real world has one.
     */
    public static boolean isActive(Level level) {
        return level.dimension() == Level.OVERWORLD && level.getServer() != null && isActive(level.getServer());
    }

    private static Optional<Holder<WorldClock>> clock(ServerLevel overworld) {
        return overworld.dimensionType().defaultClock();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        Holder<WorldClock> clock = clock(overworld).orElse(null);
        if (clock == null) {
            return;
        }
        Overtime state = get(server);
        long total = overworld.clockManager().getTotalTicks(clock);
        long day = total / DAY_TICKS;
        Gates.tick(overworld, state.active);
        if (state.active) {
            if (overworld.getGameTime() >= state.endsAt) {
                end(server);
            } else if (overworld.isRaining() || overworld.isThundering()) {
                // Not even a /weather gets through: the sky is as still as the rest.
                stillSky(server, (int) (state.endsAt - overworld.getGameTime()));
            }
            return;
        }
        if (state.nextDay == 0) {
            state.nextDay = day + Config.OVERTIME_INTERVAL_DAYS.getAsInt();
            state.setDirty();
        }
        // Sleeping through the night skips midnight; then it simply comes the next night.
        if (day >= state.nextDay && total % DAY_TICKS >= MIDNIGHT) {
            start(server);
        }
    }

    /**
     * Stops the world at midnight. Used by the schedule and by the command, which first winds the clock to midnight.
     */
    static void start(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Overtime state = get(server);
        if (state.active) {
            return;
        }
        Holder<WorldClock> clock = clock(overworld).orElse(null);
        if (clock != null) {
            long total = overworld.clockManager().getTotalTicks(clock);
            if (total % DAY_TICKS < MIDNIGHT) {
                overworld.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.MIDNIGHT);
            }
            overworld.clockManager().setPaused(clock, true);
        }
        state.active = true;
        state.endsAt = overworld.getGameTime() + Config.OVERTIME_DURATION_TICKS.getAsInt();
        state.setDirty();

        FrozenMobs.freezeAllLoaded(overworld);
        stillSky(server, Config.OVERTIME_DURATION_TICKS.getAsInt());
        announce(overworld, "seaofmemory.overtime.start", 0.5f);
        PacketDistributor.sendToAllPlayers(new OvertimePayload(true));
        SeaOfMemory.LOGGER.info("Overtime began");
    }

    /**
     * No rain and no storm while the world stands still: they stop at once, not fading out, and stay away until
     * a little after the Overtime.
     */
    private static void stillSky(MinecraftServer server, int ticks) {
        ServerLevel overworld = server.overworld();
        server.setWeatherParameters(ticks + CLEAR_AFTER, 0, false, false);
        overworld.setRainLevel(0f);
        overworld.setThunderLevel(0f);
        server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0f), overworld.dimension());
        server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f), overworld.dimension());
        server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0f), overworld.dimension());
    }

    /**
     * The world breathes out: time moves on, the ice lets go of the villagers.
     */
    static void end(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Overtime state = get(server);
        if (!state.active) {
            return;
        }
        Holder<WorldClock> clock = clock(overworld).orElse(null);
        long day = 0;
        if (clock != null) {
            overworld.clockManager().setPaused(clock, false);
            day = overworld.clockManager().getTotalTicks(clock) / DAY_TICKS;
        }
        state.active = false;
        state.nextDay = day + Config.OVERTIME_INTERVAL_DAYS.getAsInt();
        state.setDirty();

        FrozenMobs.thawAllLoaded(overworld);
        Gates.onOvertimeEnd(overworld);
        announce(overworld, "seaofmemory.overtime.end", 0.7f);
        PacketDistributor.sendToAllPlayers(new OvertimePayload(false));
        SeaOfMemory.LOGGER.info("Overtime ended; the next one falls on day {}", state.nextDay);
    }

    static long nextDay(MinecraftServer server) {
        return get(server).nextDay;
    }

    static long ticksLeft(MinecraftServer server) {
        Overtime state = get(server);
        return state.active ? Math.max(0, state.endsAt - server.overworld().getGameTime()) : 0;
    }

    private static void announce(ServerLevel overworld, String key, float pitch) {
        for (ServerPlayer player : overworld.players()) {
            player.sendOverlayMessage(Component.translatable(key));
            overworld.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.5f, pitch);
        }
    }

    @SubscribeEvent
    static void onCanSleep(CanPlayerSleepEvent event) {
        // There is no night to sleep through: the day that never comes has to be lived.
        if (isActive(event.getLevel())) {
            event.setProblem(new Player.BedSleepingProblem(Component.translatable("seaofmemory.overtime.no_sleep")));
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new OvertimePayload(isActive(player.level().getServer())));
        }
    }
}
