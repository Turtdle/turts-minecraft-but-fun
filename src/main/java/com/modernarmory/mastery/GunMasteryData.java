package com.modernarmory.mastery;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A player's mastery XP per gun type (gun item id -> xp). Stored on the player, kept through death. */
public record GunMasteryData(Map<String, Integer> xp) {
    public static final GunMasteryData EMPTY = new GunMasteryData(Map.of());

    public static final Codec<GunMasteryData> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(m -> new GunMasteryData(Map.copyOf(m)), GunMasteryData::xp);
    public static final StreamCodec<ByteBuf, GunMasteryData> STREAM_CODEC = ByteBufCodecs
            .map((java.util.function.IntFunction<Map<String, Integer>>) HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
            .map(m -> new GunMasteryData(Map.copyOf(m)), GunMasteryData::xp);

    public int xp(String gunId) {
        return xp.getOrDefault(gunId, 0);
    }

    public int level(String gunId) {
        return Mastery.level(xp(gunId));
    }

    public GunMasteryData add(String gunId, int amount) {
        Map<String, Integer> m = new HashMap<>(xp);
        m.merge(gunId, amount, Integer::sum);
        return new GunMasteryData(Map.copyOf(m));
    }
}
