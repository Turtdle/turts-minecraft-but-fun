package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: trigger held / aiming state, sent whenever it changes. */
public record GunInputPayload(boolean trigger, boolean aiming) implements CustomPacketPayload {
    public static final Type<GunInputPayload> TYPE = new Type<>(ModernArmory.id("gun_input"));
    public static final StreamCodec<ByteBuf, GunInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunInputPayload::trigger,
            ByteBufCodecs.BOOL, GunInputPayload::aiming,
            GunInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
