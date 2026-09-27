package com.seaofmemory.sea;

import java.util.function.Supplier;

import com.seaofmemory.Config;
import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.fog.CognitiveFog;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Standing in critical fog slowly pulls a player into the fog world. Stepping out in time lets the pull fade.
 * Progress is synced to the player only, for the whiteout on their screen.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID)
public final class Absorption {
    private static final int INTERVAL = 5;
    // The pull fades twice as fast as it builds.
    private static final int RECOVERY_FACTOR = 2;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeaOfMemory.MODID);

    public static final Supplier<AttachmentType<Integer>> PROGRESS = ATTACHMENT_TYPES.register("absorption", () -> AttachmentType.builder(() -> 0)
            .sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT)
            .build());

    private Absorption() {
    }

    /**
     * Absorption progress from 0 to 1.
     */
    public static float fraction(Player player) {
        return Math.min(1f, player.getData(PROGRESS.get()) / (float) limitTicks());
    }

    private static int limitTicks() {
        return Config.ABSORPTION_SECONDS.getAsInt() * 20;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0) {
            return;
        }
        int progress = player.getData(PROGRESS.get());
        // Only the real world has a copy in the fog.
        boolean exposed = player.level().dimension() == Level.OVERWORLD
                && !player.isCreative() && !player.isSpectator() && player.isAlive()
                && CognitiveFog.isCritical(CognitiveFog.densityAt(player.level(), BlockPos.containing(player.getEyePosition())));

        int next = exposed ? progress + INTERVAL : Math.max(0, progress - INTERVAL * RECOVERY_FACTOR);
        if (exposed && next >= limitTicks() && FogWorld.absorb(player)) {
            next = 0;
        }
        if (next != progress) {
            player.setData(PROGRESS.get(), next);
        }
    }
}
