package com.seaofmemory.scene;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server side of scene effects: key moments call these to change how a player sees the world.
 * The effects are per player, so in multiplayer only the people in the scene see it.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class Scenes {
    private static final String NETWORK_VERSION = "1";

    private Scenes() {
    }

    /**
     * Shows the silhouette effect for the given number of ticks, or until stopped for {@link SilhouettePayload#UNTIL_STOPPED}.
     */
    public static void silhouette(ServerPlayer player, int ticks) {
        PacketDistributor.sendToPlayer(player, new SilhouettePayload(ticks));
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // The client handler is registered separately on the client (see SilhouetteEffect).
        event.registrar(NETWORK_VERSION).playToClient(SilhouettePayload.TYPE, SilhouettePayload.STREAM_CODEC);
    }

    /**
     * Debug command: {@code /seaofmemory silhouette on|off|<seconds> [players]}.
     */
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SeaOfMemory.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("silhouette")
                        .then(withPlayers(Commands.literal("on"), SilhouettePayload.UNTIL_STOPPED))
                        .then(withPlayers(Commands.literal("off"), SilhouettePayload.OFF))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> run(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(ctx, "seconds") * 20))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> run(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), IntegerArgumentType.getInteger(ctx, "seconds") * 20))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> withPlayers(ArgumentBuilder<CommandSourceStack, ?> node, int ticks) {
        return node
                .executes(ctx -> run(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), ticks))
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> run(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), ticks)));
    }

    private static int run(CommandSourceStack source, Collection<ServerPlayer> players, int ticks) {
        for (ServerPlayer player : players) {
            silhouette(player, ticks);
        }
        String key = ticks == SilhouettePayload.OFF ? "commands.seaofmemory.silhouette.off" : "commands.seaofmemory.silhouette.on";
        source.sendSuccess(() -> Component.translatable(key, players.size()), true);
        return players.size();
    }
}
