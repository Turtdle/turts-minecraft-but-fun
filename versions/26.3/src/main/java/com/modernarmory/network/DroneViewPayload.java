package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> pilot: look through drone {@code drone}'s camera (entity id), or -1: back to your body. */
public record DroneViewPayload(int drone) implements CustomPacketPayload {
    public static final Type<DroneViewPayload> TYPE = new Type<>(ModernArmory.id("drone_view"));
    public static final StreamCodec<ByteBuf, DroneViewPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DroneViewPayload::new, DroneViewPayload::drone);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
