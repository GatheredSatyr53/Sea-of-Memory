package com.seaofmemory.cold;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.seaofmemory.SeaOfMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug command: {@code /seaofmemory cold get [player]} and {@code /seaofmemory cold set <value> [players]}.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class ColdCommand {
    private ColdCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SeaOfMemory.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cold")
                        .then(Commands.literal("get")
                                .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> get(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, Cold.MAX))
                                        .executes(ctx -> set(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), FloatArgumentType.getFloat(ctx, "value")))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), FloatArgumentType.getFloat(ctx, "value"))))))));
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        float cold = Cold.get(player);
        ColdTicker.Target target = ColdTicker.target(player.level(), player);
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.cold.get", player.getDisplayName(),
                format(cold), format(target.target())), false);
        // What the target is made of, so it can be checked on the spot.
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.cold.fog",
                format(target.density()), format(target.temperatureFactor()), yesNo(target.dark()), yesNo(target.alone()),
                format(target.fogCold())), false);
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.cold.sources",
                target.watchingSnowPeople(), format(target.snowPeopleCold()), yesNo(target.warmth()), yesNo(target.bright()),
                yesNo(!target.alone())), false);
        return Math.round(cold);
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "gui.yes" : "gui.no");
    }

    private static int set(CommandSourceStack source, Collection<ServerPlayer> players, float value) {
        for (ServerPlayer player : players) {
            Cold.set(player, value);
        }
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.cold.set", String.format(Locale.ROOT, "%.1f", value), players.size()), true);
        return players.size();
    }
}
