package com.seaofmemory.fog;

import java.util.Locale;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.sea.FogWorld;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug command: {@code /seaofmemory fog get}, {@code /seaofmemory fog set <density> [radius]},
 * {@code /seaofmemory fog absorb [player]} and {@code /seaofmemory fog release [player]}.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class FogCommand {
    private FogCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SeaOfMemory.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("fog")
                        .then(Commands.literal("get").executes(ctx -> get(ctx.getSource())))
                        .then(Commands.literal("absorb")
                                .executes(ctx -> absorb(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> absorb(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("release")
                                .executes(ctx -> release(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> release(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("density", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> set(ctx.getSource(), FloatArgumentType.getFloat(ctx, "density"), 0))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                                .executes(ctx -> set(ctx.getSource(), FloatArgumentType.getFloat(ctx, "density"), IntegerArgumentType.getInteger(ctx, "radius"))))))));
    }

    private static int get(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        LevelChunk chunk = level.getChunkAt(BlockPos.containing(source.getPosition()));
        float density = CognitiveFog.getDensity(chunk);
        float target = FogSimulation.targetDensity(level, chunk);
        Component state = Component.translatable(CognitiveFog.isCritical(density) ? "commands.seaofmemory.fog.critical" : "commands.seaofmemory.fog.normal");
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.fog.get", format(density), format(target), state), false);
        return Math.round(density * 100);
    }

    private static int set(CommandSourceStack source, float density, int radius) {
        ServerLevel level = source.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        int changed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
                if (chunk != null) {
                    CognitiveFog.setDensity(chunk, density);
                    changed++;
                }
            }
        }
        int chunks = changed;
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.fog.set", format(density), chunks), true);
        return chunks;
    }

    private static int absorb(CommandSourceStack source, ServerPlayer player) {
        if (!FogWorld.absorb(player)) {
            source.sendFailure(Component.translatable("commands.seaofmemory.fog.absorb.failed", player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.fog.absorb", player.getDisplayName()), true);
        return 1;
    }

    private static int release(CommandSourceStack source, ServerPlayer player) {
        if (!FogWorld.is(player.level()) || !FogWorld.release(player, player.blockPosition())) {
            source.sendFailure(Component.translatable("commands.seaofmemory.fog.release.failed", player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.seaofmemory.fog.release", player.getDisplayName()), true);
        return 1;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
