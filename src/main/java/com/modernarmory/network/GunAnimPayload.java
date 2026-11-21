package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> players watching a shooter: play gun animation {@code anim} on the GeckoLib instance
 * {@code instanceId} of gun item {@code item}. Used for the fire animation, which the shooter's own client
 * already played when it predicted the shot (GeckoLib's own trigger would send it to the shooter too).
 */
public record GunAnimPayload(String item, long instanceId, String anim) implements CustomPacketPayload {
    public static final Type<GunAnimPayload> TYPE = new Type<>(ModernArmory.id("gun_anim"));
    public static final StreamCodec<ByteBuf, GunAnimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GunAnimPayload::item,
            ByteBufCodecs.VAR_LONG, GunAnimPayload::instanceId,
            ByteBufCodecs.STRING_UTF8, GunAnimPayload::anim,
            GunAnimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
