package com.modernarmory.grenade;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * How a thrown grenade is turned, worked out on the client from how it actually moves (the server only sends
 * positions). It is drawn as: turn to face the way it is going ({@link #yaw}), bank left or right ({@link #tilt}:
 * a random lean of the spin axis per throw, with a little wobble, so it doesn't spin dead flat), turn about the
 * sideways axis ({@link #roll}: end over end in the air, rolling along the ground), then tip over onto its side
 * ({@link #lean}, 0 = upright, 90 = lying across the direction of travel, so it rolls like a can).
 * <p>
 * In the air it spins at a steady rate picked per throw; on the ground the roll follows the distance covered
 * (no slipping), and it settles on its side. Pure maths on positions, so both game versions share it.
 */
public final class GrenadeTumble {
    /** Rolling radius on its side, in blocks (the body's half-width in the item model). */
    private static final double ROLL_RADIUS = 0.06;
    /** Centre height above the entity position: standing on its end vs lying on its side. */
    private static final float LIFT_UPRIGHT = 0.11f, LIFT_LYING = 0.035f;

    public float yaw, roll, lean, tilt;
    private float prevYaw, prevRoll, prevLean, prevTilt;
    private final float airSpin;
    /** This throw's bank of the spin axis (degrees, left or right) and how much it wobbles about it. */
    private final float airTilt, wobble;
    private int airTicks;
    private Vec3 last;
    /** Whether it has a heading yet: the first one is taken as it is (easing in from 0 would swing the spin axis
     * round in mid-air, which looks like a corkscrew); later changes (bounces) ease in. */
    private boolean headed;

    /** @param seed anything stable per grenade (its entity id): picks the in-air spin rate */
    public GrenadeTumble(int seed) {
        // a thrown grenade turns over 1.5-3 times a second (27-54 degrees a tick)
        this.airSpin = 27f + (Math.floorMod(seed * 2654435761L, 1000L) / 1000f) * 27f;
        // banked 10-35 degrees to a random side, wobbling +-6 degrees
        long h = seed * 0x9E3779B97F4A7C15L;
        float u = Math.floorMod(h >>> 17, 1000L) / 1000f;
        this.airTilt = (10f + 25f * u) * ((h & 1L) == 0 ? 1f : -1f);
        this.wobble = 3f + 3f * (Math.floorMod(h >>> 31, 1000L) / 1000f);
    }

    /**
     * Call once per client tick with the grenade's current position and, for its very first tick, its velocity
     * (from the spawn packet), so it faces its flight direction from the first frame.
     */
    public void tick(Level level, Vec3 pos, Vec3 velocity) {
        prevYaw = yaw;
        prevRoll = roll;
        prevLean = lean;
        prevTilt = tilt;
        if (last == null) {
            last = pos;
            if (velocity.horizontalDistanceSqr() > 1e-6) {
                yaw = prevYaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
                headed = true;
            }
            return;
        }
        Vec3 d = pos.subtract(last);
        last = pos;
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz > 0.005) {
            float heading = (float) Math.toDegrees(Math.atan2(d.x, d.z));
            if (!headed) {
                yaw = prevYaw = heading;
                headed = true;
            } else {
                yaw += Mth.wrapDegrees(heading - yaw) * 0.5f;
            }
        }

        boolean grounded = Math.abs(d.y) < 0.03 && !level.getBlockState(BlockPos.containing(pos.add(0, -0.1, 0))).isAir();
        if (grounded) {
            roll += (float) Math.toDegrees(horiz / ROLL_RADIUS);
            lean += (90f - lean) * 0.35f;
            tilt += (0f - tilt) * 0.35f; // a rolling can is level
        } else {
            roll += airSpin;
            lean += (0f - lean) * 0.08f;
            airTicks++;
            float target = airTilt + wobble * (float) Math.sin(airTicks * 0.45);
            tilt += (target - tilt) * (airTicks <= 1 ? 1f : 0.5f);
        }
        // keep the numbers small without a visible jump (prev moves by the same whole turns)
        if (roll > 3600f) {
            roll -= 3600f;
            prevRoll -= 3600f;
        }
        if (yaw - prevYaw > 180f) prevYaw += 360f;
        else if (yaw - prevYaw < -180f) prevYaw -= 360f;
    }

    public float yaw(float partialTick) {
        return Mth.lerp(partialTick, prevYaw, yaw);
    }

    public float roll(float partialTick) {
        return Mth.lerp(partialTick, prevRoll, roll);
    }

    public float tilt(float partialTick) {
        return Mth.lerp(partialTick, prevTilt, tilt);
    }

    public float lean(float partialTick) {
        return Mth.lerp(partialTick, prevLean, lean);
    }

    /** Height of the grenade's centre above the entity position: it sinks as it tips onto its side. */
    public float lift(float partialTick) {
        return Mth.lerp(lean(partialTick) / 90f, LIFT_UPRIGHT, LIFT_LYING);
    }
}
