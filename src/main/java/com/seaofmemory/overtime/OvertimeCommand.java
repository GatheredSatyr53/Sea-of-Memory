package com.seaofmemory.overtime;

import com.seaofmemory.SeaOfMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug command: {@code /seaofmemory overtime start|stop|status}.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class OvertimeCommand {
    private OvertimeCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SeaOfMemory.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("overtime")
                        .then(Commands.literal("start").executes(ctx -> {
                            Overtime.start(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            Overtime.end(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (Overtime.isActive(server)) {
            long secondsLeft = Overtime.ticksLeft(server) / 20;
            source.sendSuccess(() -> Component.translatable("commands.seaofmemory.overtime.active", secondsLeft), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.seaofmemory.overtime.scheduled", Overtime.nextDay(server)), false);
        }
        return 1;
    }
}
