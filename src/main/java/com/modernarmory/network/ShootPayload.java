package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the shooter's client fired a round (client.ShotPredictor) and already shows it.
 * The server checks it may (gun in that slot, ammo, fire rate, not reloading) and fires the real one from
 * the same {@code seed} (gun.ShotMath), aimed along (xRot, yRot) with the client's {@code spread}
 * (degrees, cone diameter) unless that is suspiciously tighter than its own.
 */
public record ShootPayload(int seq, long seed, int slot, float xRot, float yRot, float spread) implements CustomPacketPayload {
    public static final Type<ShootPayload> TYPE = new Type<>(ModernArmory.id("shoot"));
    public static final StreamCodec<ByteBuf, ShootPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShootPayload::seq,
            ByteBufCodecs.VAR_LONG, ShootPayload::seed,
            ByteBufCodecs.VAR_INT, ShootPayload::slot,
            ByteBufCodecs.FLOAT, ShootPayload::xRot,
            ByteBufCodecs.FLOAT, ShootPayload::yRot,
            ByteBufCodecs.FLOAT, ShootPayload::spread,
            ShootPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
