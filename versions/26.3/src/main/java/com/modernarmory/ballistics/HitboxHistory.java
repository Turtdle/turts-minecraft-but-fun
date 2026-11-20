package com.modernarmory.ballistics;

import java.util.Map;
import java.util.WeakHashMap;

import com.modernarmory.ModernArmory;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Where every living entity's hitbox was over the last second, server side, for lag compensation
 * ({@link LagCompensation}): a shooter aims at the world as their client drew it, which is a few ticks
 * behind the server, so a bullet is tested against the targets as they were then ("favour the shooter").
 * Recorded at the end of each server level tick.
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class HitboxHistory {
    /** How far back it remembers (ticks); rewinds are capped below this. */
    public static final int TICKS = 20;

    private static final Map<ServerLevel, Int2ObjectOpenHashMap<Track>> LEVELS = new WeakHashMap<>();

    /** One entity's boxes, a ring indexed by game time. */
    private static final class Track {
        final AABB[] boxes = new AABB[TICKS];
        final long[] at = new long[TICKS];
        long last;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        var tracks = LEVELS.computeIfAbsent(level, l -> new Int2ObjectOpenHashMap<>());
        for (Entity e : level.getAllEntities()) {
            // players, mobs and drones (fast, and shot at by players who see them where they were)
            if (!(e instanceof LivingEntity || e instanceof com.modernarmory.drone.DroneEntity) || e.isRemoved()) continue;
            Track t = tracks.computeIfAbsent(e.getId(), id -> new Track());
            int i = (int) Math.floorMod(now, (long) TICKS);
            t.boxes[i] = e.getBoundingBox();
            t.at[i] = now;
            t.last = now;
        }
        tracks.int2ObjectEntrySet().removeIf(en -> en.getValue().last < now); // gone or unloaded
    }

    /**
     * {@code e}'s hitbox {@code ticksAgo} ticks before now (0 = where it is). An entity with a shorter
     * history (just spawned or loaded) gives its oldest recorded box.
     */
    public static AABB boxAt(Entity e, int ticksAgo) {
        if (ticksAgo <= 0 || !(e.level() instanceof ServerLevel level)) return e.getBoundingBox();
        var tracks = LEVELS.get(level);
        Track t = tracks == null ? null : tracks.get(e.getId());
        if (t == null) return e.getBoundingBox();
        long want = level.getGameTime() - Math.min(ticksAgo, TICKS - 1);
        AABB best = null;
        long bestAt = Long.MAX_VALUE;
        for (int i = 0; i < TICKS; i++) {
            if (t.boxes[i] == null || t.at[i] < want || t.at[i] >= bestAt) continue;
            best = t.boxes[i]; // the oldest record at or after the wanted tick
            bestAt = t.at[i];
        }
        return best != null ? best : e.getBoundingBox();
    }

    private HitboxHistory() {}
}
