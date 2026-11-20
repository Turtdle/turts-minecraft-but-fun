package com.modernarmory.ballistics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * How many ticks to rewind targets ({@link HitboxHistory}) for a shot, so it hits what the shooter saw.
 * What a player sees is behind the server by:
 * <ul>
 * <li>the network round trip: the world state took half of it to arrive, the shot the other half to come back;</li>
 * <li>{@link #VIEW_DELAY} ticks of client interpolation: mobs' positions arrive every few ticks and the
 *     client glides them there over 3 ticks (measured by the "hitreg" autotest section);</li>
 * <li>{@link #SPAWN_DELAY}: a bullet fired in the player tick first moves in the next level tick.</li>
 * </ul>
 * Capped at {@link #MAX_TICKS} so a laggy client can't shoot far into the past.
 */
public final class LagCompensation {
    public static final int DEFAULT_VIEW_DELAY = 3;
    /** Ticks of client interpolation; a variable only so the autotest can compare values. */
    public static int VIEW_DELAY = DEFAULT_VIEW_DELAY;
    /** Off only for the autotest's comparison. */
    public static boolean enabled = true;
    public static final int SPAWN_DELAY = 1;
    public static final int MAX_TICKS = 10;

    /** Rewind for a shot by {@code shooter}: 0 for anything that isn't a player (mobs, grenades). */
    public static int ticks(LivingEntity shooter) {
        if (!enabled || !(shooter instanceof ServerPlayer p) || p.connection == null) return 0;
        int ping = Math.round(Math.max(0, p.connection.latency()) / 50f);
        return Math.min(MAX_TICKS, ping + VIEW_DELAY + SPAWN_DELAY);
    }

    private LagCompensation() {}
}
