package com.modernarmory.drone;

/**
 * Stick input for one tick: forward / strafe (right positive) / lift in -1..1, boost (sprint), and the
 * heading (yaw) and camera pitch in degrees. From the pilot's keys and mouse, or from an autopilot.
 */
public record DroneInput(float forward, float strafe, float lift, boolean boost, float yaw, float pitch) {
    /** Hold position, keep the heading. */
    public static DroneInput hold(float yaw, float pitch) {
        return new DroneInput(0, 0, 0, false, yaw, pitch);
    }
}
