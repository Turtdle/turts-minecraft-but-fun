package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> everyone who can see player {@code entityId}, and that player: a flashbang blinded them for
 * {@code seconds} (their white-out, grenade.FlashEffect.whiteoutSeconds), so they throw a hand over their eyes
 * (client.FlashCover).
 */
public record FlashCoverPayload(int entityId, float seconds) implements CustomPacketPayload {
    public static final Type<FlashCoverPayload> TYPE = new Type<>(ModernArmory.id("flash_cover"));
    public static final StreamCodec<ByteBuf, FlashCoverPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlashCoverPayload::entityId,
            ByteBufCodecs.FLOAT, FlashCoverPayload::seconds,
            FlashCoverPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
