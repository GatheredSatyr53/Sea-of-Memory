package com.seaofmemory.client;

import com.seaofmemory.SeaOfMemory;
import com.seaofmemory.overtime.OvertimePayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Whether the server says an Overtime is on.
 */
@EventBusSubscriber(modid = SeaOfMemory.MODID, value = Dist.CLIENT)
public final class ClientOvertime {
    private static boolean active;

    private ClientOvertime() {
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(OvertimePayload.TYPE, (payload, context) -> active = payload.active());
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }
}
