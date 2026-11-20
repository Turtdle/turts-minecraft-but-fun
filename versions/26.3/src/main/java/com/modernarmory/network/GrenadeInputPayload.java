package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: which mouse buttons are held while holding a grenade, sent whenever it changes (GrenadeHandler). */
public record GrenadeInputPayload(boolean left, boolean right) implements CustomPacketPayload {
    public static final Type<GrenadeInputPayload> TYPE = new Type<>(ModernArmory.id("grenade_input"));
    public static final StreamCodec<ByteBuf, GrenadeInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GrenadeInputPayload::left,
            ByteBufCodecs.BOOL, GrenadeInputPayload::right,
            GrenadeInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
