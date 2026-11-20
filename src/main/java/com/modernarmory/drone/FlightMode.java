package com.modernarmory.drone;

/** How the pilot's sticks fly a drone (per type, {@link DroneType#flightMode()}). */
public enum FlightMode {
    /**
     * Acro / rate mode: no self-levelling and nothing holding the attitude. Pitch and roll sticks command
     * rotation rates, the mouse yaws (and pitches a hard-mounted camera), the throttle is raw thrust. The
     * drone keeps any attitude it is left in, so it can flip; keeping it level and at height is the pilot's job.
     */
    ACRO,
    /** Stabilised (GPS) mode: the sticks command a velocity, the flight controller levels and holds the drone. */
    STABILIZED
}
