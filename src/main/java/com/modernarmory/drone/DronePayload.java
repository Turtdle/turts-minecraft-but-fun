package com.modernarmory.drone;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a drone carries (strategy, one per drone type): the pilot's primary action (left click), and how
 * impacts, contact and being shot down end. Everything here runs on the server.
 */
public interface DronePayload {
    /** Short label for the HUD's action line, e.g. "SPOT" / "DET". */
    String actionLabel();

    /** The pilot pressed the primary action (left click) in the camera view. */
    void primary(ServerLevel level, DroneEntity drone, ServerPlayer pilot);

    /** Ticks of powered flight before the payload is live; 0 = nothing to arm. */
    default int armTicks() {
        return 0;
    }

    /** The drone hit a block at {@code speed} m/s. Default: airframe damage above the type's crash speed. */
    default void onImpact(ServerLevel level, DroneEntity drone, double speed) {
        float crash = drone.type().crashSpeed();
        if (speed > crash) drone.crashDamage((float) ((speed - crash) * 1.5));
    }

    /** The drone flew into a living thing (checked every tick while it is powered). */
    default void onContact(ServerLevel level, DroneEntity drone, LivingEntity target) {}

    /** The airframe was destroyed (shot down, crashed); the drone is removed right after. */
    default void onDestroyed(ServerLevel level, DroneEntity drone) {}
}
