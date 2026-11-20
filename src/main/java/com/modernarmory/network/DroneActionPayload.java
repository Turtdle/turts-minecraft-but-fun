package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pilot's client -> server: leave the camera view, or the drone's primary action (see DroneControl.ACTION_*). */
public record DroneActionPayload(int action) implements CustomPacketPayload {
    public static final Type<DroneActionPayload> TYPE = new Type<>(ModernArmory.id("drone_action"));
    public static final StreamCodec<ByteBuf, DroneActionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DroneActionPayload::new, DroneActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
