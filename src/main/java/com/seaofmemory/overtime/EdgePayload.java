package com.seaofmemory.overtime;

import com.seaofmemory.SeaOfMemory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells a player kept in by the Overtime how close they are to its edge, from 0 (far from it) to 1 (right at it),
 * so the world can turn to its negative as they near it.
 */
public record EdgePayload(float nearness) implements CustomPacketPayload {
    public static final Type<EdgePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "overtime_edge"));
    public static final StreamCodec<ByteBuf, EdgePayload> STREAM_CODEC = ByteBufCodecs.FLOAT.map(EdgePayload::new, EdgePayload::nearness);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
