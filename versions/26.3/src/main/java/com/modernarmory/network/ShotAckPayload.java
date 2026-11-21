package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> shooter: the answer to {@link ShootPayload} {@code seq}: fired or refused, and the rounds left
 * in the gun in {@code slot} afterwards, so the client's predicted ammo count follows the server's.
 */
public record ShotAckPayload(int seq, boolean accepted, int slot, int ammo) implements CustomPacketPayload {
    public static final Type<ShotAckPayload> TYPE = new Type<>(ModernArmory.id("shot_ack"));
    public static final StreamCodec<ByteBuf, ShotAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShotAckPayload::seq,
            ByteBufCodecs.BOOL, ShotAckPayload::accepted,
            ByteBufCodecs.VAR_INT, ShotAckPayload::slot,
            ByteBufCodecs.VAR_INT, ShotAckPayload::ammo,
            ShotAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
