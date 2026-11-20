package com.modernarmory.grenade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * What a grenade does when its fuze runs out (strategy, one per grenade type). Instant effects (frag,
 * stun) happen in {@link #detonate}; burning ones (smoke, thermite) then keep the grenade alive for
 * {@link #burnTicks()} ticks, ticking {@link #burn} on the server and {@link #clientBurn} on clients.
 */
public interface GrenadeEffect {
    /** Called once on the server when the fuze runs out. */
    void detonate(ServerLevel level, GrenadeEntity grenade);

    /** How long the grenade keeps burning after detonating (0 = it is gone at once). */
    default int burnTicks() {
        return 0;
    }

    /** Server tick while burning; {@code tick} counts up from 0. */
    default void burn(ServerLevel level, GrenadeEntity grenade, int tick) {}

    /** Client tick while burning (particles); {@code tick} counts up from 0. */
    default void clientBurn(Level level, GrenadeEntity grenade, int tick) {}
}
