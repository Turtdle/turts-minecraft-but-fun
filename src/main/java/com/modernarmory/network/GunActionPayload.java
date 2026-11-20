package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: reload / switch fire mode (see GunHandler.ACTION_*). */
public record GunActionPayload(int action) implements CustomPacketPayload {
    public static final Type<GunActionPayload> TYPE = new Type<>(ModernArmory.id("gun_action"));
    public static final StreamCodec<ByteBuf, GunActionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(GunActionPayload::new, GunActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
