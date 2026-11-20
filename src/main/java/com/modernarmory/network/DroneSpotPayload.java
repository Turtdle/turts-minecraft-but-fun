package com.modernarmory.network;

import java.util.List;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> pilot: these entities were spotted by your scout drone; outline them for {@code ticks}. */
public record DroneSpotPayload(List<Integer> entities, int ticks) implements CustomPacketPayload {
    public static final Type<DroneSpotPayload> TYPE = new Type<>(ModernArmory.id("drone_spot"));
    public static final StreamCodec<ByteBuf, DroneSpotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), DroneSpotPayload::entities,
            ByteBufCodecs.VAR_INT, DroneSpotPayload::ticks,
            DroneSpotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
