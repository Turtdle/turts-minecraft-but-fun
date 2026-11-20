package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> the gun's owner: play one of your own gun sounds "attached" to you (non-positional),
 * so it can't pan to one side when you move away from where you fired. Everyone else hears the
 * normal positional sound.
 */
public record LocalSoundPayload(ResourceLocation sound, float volume, float pitch, float delay) implements CustomPacketPayload {
    public static final Type<LocalSoundPayload> TYPE = new Type<>(ModernArmory.id("local_sound"));
    public static final StreamCodec<ByteBuf, LocalSoundPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LocalSoundPayload::sound,
            ByteBufCodecs.FLOAT, LocalSoundPayload::volume,
            ByteBufCodecs.FLOAT, LocalSoundPayload::pitch,
            ByteBufCodecs.FLOAT, LocalSoundPayload::delay,
            LocalSoundPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
