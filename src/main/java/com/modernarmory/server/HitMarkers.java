package com.modernarmory.server;

import java.util.Map;
import java.util.WeakHashMap;

import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hit-marker sounds: when a player's bullet lands on a living target they hear a body-hit or headshot sound
 * (tools/soundgen/hit_markers.py), played only to them and not positional. At most one per server tick, so
 * a shotgun's pellets give one marker (a headshot one if any pellet hit the head). When body armour stopped the
 * round, the marker is that armour's impact sound instead, so the shooter knows they hit armour.
 */
public final class HitMarkers {
    /** Per shooter: the last tick a marker was sent and whether it was a headshot one. */
    private static final Map<ServerPlayer, long[]> LAST = new WeakHashMap<>();
    /** Markers sent since start: body, headshot (the hit-registration autotest reads them). */
    public static int sentBody, sentHead, sentArmor;

    public static void onHit(Entity shooter, Entity target, boolean headshot) {
        onHit(shooter, target, headshot, null);
    }

    /** {@code armor}: the impact sound of the armour that stopped the round, or null if none did. */
    public static void onHit(Entity shooter, Entity target, boolean headshot, SoundEvent armor) {
        if (!(shooter instanceof ServerPlayer p) || !(target instanceof LivingEntity) || target == p) return;
        long now = p.level().getGameTime();
        int rank = headshot ? 2 : 1;
        long[] last = LAST.computeIfAbsent(p, k -> new long[] {-1, 0});
        if (last[0] == now && last[1] >= rank) return;
        last[0] = now;
        last[1] = rank;
        if (armor != null) sentArmor++;
        else if (headshot) sentHead++;
        else sentBody++;
        SoundEvent sound = armor != null ? armor : (headshot ? ModSounds.HIT_HEAD : ModSounds.HIT_BODY).get();
        PacketDistributor.sendToPlayer(p, new LocalSoundPayload(sound.getLocation(), 1.0f, 1.0f, 0f));
    }

    private HitMarkers() {}
}
