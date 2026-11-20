package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> shooter: a round was fired; apply this camera recoil (degrees) and muzzle flash. */
public record ShotFiredPayload(float pitchKick, float yawKick, float delay) implements CustomPacketPayload {
    public static final Type<ShotFiredPayload> TYPE = new Type<>(ModernArmory.id("shot_fired"));
    public static final StreamCodec<ByteBuf, ShotFiredPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShotFiredPayload::pitchKick,
            ByteBufCodecs.FLOAT, ShotFiredPayload::yawKick,
            ByteBufCodecs.FLOAT, ShotFiredPayload::delay,
            ShotFiredPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
