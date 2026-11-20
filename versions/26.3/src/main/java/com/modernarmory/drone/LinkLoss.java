package com.modernarmory.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a drone's flight controller does on its own when the control link is gone (strategy per type).
 * The pilot gets control back as soon as the signal returns.
 */
public enum LinkLoss {
    /** Hold position until the battery runs low, then land where it is. */
    HOVER {
        @Override
        public DroneInput autopilot(DroneEntity d, int lostTicks, Entity home) {
            return d.lowBattery() ? land(d) : DroneInput.hold(d.getYRot(), d.cameraPitch());
        }
    },
    /**
     * Return to home (Skydio): climb to a safe height above the pilot, fly straight back, descend and land
     * next to them. Coming back into range hands control back to the pilot on the way.
     */
    RETURN_HOME {
        @Override
        public DroneInput autopilot(DroneEntity d, int lostTicks, Entity home) {
            if (home == null || d.lowBattery()) return HOVER.autopilot(d, lostTicks, null);
            if (lostTicks < 20) return DroneInput.hold(d.getYRot(), d.cameraPitch()); // a second to see if the link comes back
            Vec3 to = home.position().subtract(d.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            double safeY = Math.max(home.getY() + RTH_HEIGHT, Math.min(d.getY(), home.getY() + RTH_HEIGHT + 20));
            if (flat > 3) {
                if (d.getY() < safeY - 0.5 || d.horizontalCollision) return new DroneInput(0, 0, 1, false, yaw, 20);
                float fwd = (float) Mth.clamp(flat / 6, 0.2, 1);
                return new DroneInput(fwd, 0, (float) Mth.clamp((safeY - d.getY()) / 3, -0.5, 0.5), false, yaw, 20);
            }
            return land(d);
        }
    },
    /** Failsafe (FPV): hold for a few seconds, then cut the motors and fall. */
    FAILSAFE_DROP {
        @Override
        public DroneInput autopilot(DroneEntity d, int lostTicks, Entity home) {
            return lostTicks < 60 ? DroneInput.hold(d.getYRot(), d.cameraPitch()) : null;
        }
    };

    /** Height above the pilot a returning drone flies at (m). */
    public static final double RTH_HEIGHT = 8;

    /**
     * Stick input for this tick; null = cut the motors. {@code home} is the pilot (or owner), null if
     * they are gone.
     */
    public abstract DroneInput autopilot(DroneEntity d, int lostTicks, Entity home);

    /** Straight down at a gentle rate. */
    static DroneInput land(DroneEntity d) {
        return new DroneInput(0, 0, -0.45f, false, d.getYRot(), d.cameraPitch());
    }
}
