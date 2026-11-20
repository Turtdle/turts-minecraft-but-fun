package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Laser turret operator's client -> server: lock a drone (its entity id) and fire, or cease fire (laser.TurretSeat.ENGAGE / CEASE). */
public record LaserCommandPayload(int action, int target) implements CustomPacketPayload {
    public static final Type<LaserCommandPayload> TYPE = new Type<>(ModernArmory.id("laser_command"));
    public static final StreamCodec<ByteBuf, LaserCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LaserCommandPayload::action, ByteBufCodecs.VAR_INT, LaserCommandPayload::target, LaserCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
