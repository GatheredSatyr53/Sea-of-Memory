package com.seaofmemory.scene;

import com.seaofmemory.SeaOfMemory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells a client to show the silhouette scene effect.
 *
 * @param ticks how long to keep it on: {@link #OFF} switches it off, {@link #UNTIL_STOPPED} keeps it until the next payload
 */
public record SilhouettePayload(int ticks) implements CustomPacketPayload {
    public static final int OFF = 0;
    public static final int UNTIL_STOPPED = -1;

    public static final Type<SilhouettePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SeaOfMemory.MODID, "silhouette"));
    public static final StreamCodec<ByteBuf, SilhouettePayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(SilhouettePayload::new, SilhouettePayload::ticks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
