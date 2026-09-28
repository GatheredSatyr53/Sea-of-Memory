package com.seaofmemory.overtime;

import com.seaofmemory.SeaOfMemory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells clients whether an Overtime is on, for client-side effects that only belong in it.
 */
public record OvertimePayload(boolean active) implements CustomPacketPayload {
    public static final Type<OvertimePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "overtime"));
    public static final StreamCodec<ByteBuf, OvertimePayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(OvertimePayload::new, OvertimePayload::active);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
