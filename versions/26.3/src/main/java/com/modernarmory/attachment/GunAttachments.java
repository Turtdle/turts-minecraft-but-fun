package com.modernarmory.attachment;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Data component value: which attachment id sits in each slot of a gun. */
public record GunAttachments(Map<AttachmentType, String> slots) {
    public static final GunAttachments EMPTY = new GunAttachments(Map.of());

    private static final Codec<AttachmentType> TYPE_CODEC = Codec.STRING.xmap(AttachmentType::valueOf, Enum::name);
    public static final Codec<GunAttachments> CODEC =
            Codec.unboundedMap(TYPE_CODEC, Codec.STRING).xmap(m -> new GunAttachments(Map.copyOf(m)), GunAttachments::slots);
    public static final StreamCodec<ByteBuf, GunAttachments> STREAM_CODEC = ByteBufCodecs
            .map((java.util.function.IntFunction<Map<AttachmentType, String>>) HashMap::new,
                    ByteBufCodecs.STRING_UTF8.map(AttachmentType::valueOf, Enum::name), ByteBufCodecs.STRING_UTF8)
            .map(m -> new GunAttachments(Map.copyOf(m)), GunAttachments::slots);

    public Attachment get(AttachmentType type) {
        return Attachments.byId(slots.get(type));
    }

    public GunAttachments with(AttachmentType type, Attachment a) {
        Map<AttachmentType, String> m = new EnumMap<>(AttachmentType.class);
        m.putAll(slots);
        if (a == null) m.remove(type);
        else m.put(type, a.id());
        return new GunAttachments(Map.copyOf(m));
    }
}
