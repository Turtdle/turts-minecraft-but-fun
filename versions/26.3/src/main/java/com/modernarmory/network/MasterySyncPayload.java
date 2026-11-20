package com.modernarmory.network;

import com.modernarmory.ModernArmory;
import com.modernarmory.mastery.GunMasteryData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> player: their gun mastery (sent on login / respawn / dimension change and whenever it changes). */
public record MasterySyncPayload(GunMasteryData data) implements CustomPacketPayload {
    public static final Type<MasterySyncPayload> TYPE = new Type<>(ModernArmory.id("mastery_sync"));
    public static final StreamCodec<ByteBuf, MasterySyncPayload> STREAM_CODEC =
            GunMasteryData.STREAM_CODEC.map(MasterySyncPayload::new, MasterySyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
