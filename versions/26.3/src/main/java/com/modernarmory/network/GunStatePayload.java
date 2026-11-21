package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> shooter: what the client needs to predict its shots (client.ShotPredictor): whether the gun in
 * {@code slot} is reloading (and whether pulling the trigger stops that, as with a tube magazine), its
 * rounds, and whether this player has infinite ammo (creative). Sent when any of it changes.
 */
public record GunStatePayload(boolean reloading, boolean interruptible, int slot, int ammo, boolean infinite)
        implements CustomPacketPayload {
    public static final Type<GunStatePayload> TYPE = new Type<>(ModernArmory.id("gun_state"));
    public static final StreamCodec<ByteBuf, GunStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunStatePayload::reloading,
            ByteBufCodecs.BOOL, GunStatePayload::interruptible,
            ByteBufCodecs.VAR_INT, GunStatePayload::slot,
            ByteBufCodecs.VAR_INT, GunStatePayload::ammo,
            ByteBufCodecs.BOOL, GunStatePayload::infinite,
            GunStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
