package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> player: an M84 went off near you. {@code flash} 0..1 blinds (distance, facing it; 0 if it was out of
 * sight), {@code bang} 0..1 rings the ears (distance; muffled through a wall), {@code glance}: turned away from a
 * flash in the open, an instant's white glare and no more.
 */
public record FlashbangPayload(float flash, float bang, boolean glance) implements CustomPacketPayload {
    public static final Type<FlashbangPayload> TYPE = new Type<>(ModernArmory.id("flashbang"));
    public static final StreamCodec<ByteBuf, FlashbangPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FlashbangPayload::flash, ByteBufCodecs.FLOAT, FlashbangPayload::bang,
            ByteBufCodecs.BOOL, FlashbangPayload::glance, FlashbangPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
