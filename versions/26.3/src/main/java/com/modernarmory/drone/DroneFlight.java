package com.modernarmory.drone;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * The flight model, shared by whichever side flies the drone (the pilot's client while it has the link, the
 * server otherwise). A multirotor is a rigid body: its motors push along its own up axis (thrust = throttle x
 * the type's thrust-to-weight x g), gravity pulls down, air drag grows with the square of the speed (sized so
 * the type tops out at its max speed with the throttle open and tilted as far as it can while holding height).
 * The attitude is a quaternion in camera convention (forward = -Z, up = +Y, right = +X; identity faces north).
 * <ul>
 *   <li>{@link #stepAcro}: the pilot's rate sticks rotate the body; nothing levels it.</li>
 *   <li>{@link #stepAuto}: a flight controller (autopilot, stabilised mode) flies a commanded velocity by
 *       tilting and throttling the same body, levelling it as it goes.</li>
 * </ul>
 * Collisions use the vanilla mover; the speed lost to a block is the impact speed. A drone sitting on the
 * ground without enough thrust to lift settles level on its feet.
 */
public final class DroneFlight {
    static final double DT = 0.05, G = 9.81;
    /** Yaw rate at full stick, as a share of the pitch / roll rate (props yaw slower than they tilt). */
    private static final float YAW_SHARE = 0.6f;
    /** Most the autopilot tilts (deg). */
    private static final double AUTO_MAX_TILT = 35;

    /** One tick in acro mode: rotate by the sticks, then fly. @return impact speed (m/s), 0 if it flew free */
    public static double stepAcro(DroneEntity d, AcroSticks s) {
        DroneType t = d.type();
        float max = s.boost() ? t.boostRateDeg() : t.rateDeg(), cap = t.boostRateDeg();
        Quaternionf q = d.attitude();
        boolean grounded = d.onGround() && s.throttle() * t.thrustToWeight() < 1; // a grounded drone can only yaw on its feet
        // what the pilot asks for (sticks through the expo curve, plus the mouse's rotation this tick) ...
        float wantPitch = Mth.clamp(expo(s.pitch(), t.expo()) * max + s.pitchDeg() / (float) DT, -cap, cap);
        float wantRoll = Mth.clamp(expo(s.roll(), t.expo()) * max + s.rollDeg() / (float) DT, -cap, cap);
        float wantYaw = Mth.clamp(expo(s.yaw(), t.expo()) * max * YAW_SHARE + s.yawDeg() / (float) DT, -cap, cap);
        // ... and the airframe gets there as its motors change speed: quick, never instant
        float k = (float) (1 - Math.exp(-DT / Math.max(0.01, t.rotTau())));
        d.pitchRate = grounded ? 0 : d.pitchRate + (wantPitch - d.pitchRate) * k;
        d.rollRate = grounded ? 0 : d.rollRate + (wantRoll - d.rollRate) * k;
        d.yawRate += (wantYaw - d.yawRate) * k;
        q.rotateX((float) Math.toRadians(-d.pitchRate * DT))
         .rotateZ((float) Math.toRadians(-d.rollRate * DT))
         .rotateY((float) Math.toRadians(-d.yawRate * DT)).normalize();
        d.setAttitude(q);
        if (t.gimbal()) d.setCameraPitch(s.gimbal());
        return integrate(d, Mth.clamp(s.throttle(), 0, 1), true);
    }

    /**
     * One tick under the flight controller: chase the commanded velocity (heading-relative stick, climb) by
     * tilting toward it and setting the thrust, then fly. Motors off: falls.
     */
    public static double stepAuto(DroneEntity d, DroneInput in, boolean motors) {
        DroneType t = d.type();
        Quaternionf q = d.attitude();
        float thrust = 0;
        if (motors) {
            double yaw = Math.toRadians(in.yaw());
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 wantH = fwd.scale(in.forward()).add(right.scale(in.strafe()));
            if (wantH.lengthSqr() > 1) wantH = wantH.normalize();
            Vec3 want = wantH.scale(in.boost() ? t.maxSpeed() : t.cruiseSpeed()).add(0, Mth.clamp(in.lift(), -1, 1) * t.climbRate(), 0);
            Vec3 v = d.velocity();
            Vec3 acc = want.subtract(v).scale(2.5);
            if (acc.length() > t.accel()) acc = acc.normalize().scale(t.accel());
            // thrust has to give that acceleration, hold the drone up and beat the drag
            Vec3 need = acc.add(0, G, 0).add(v.scale(t.drag() * v.length()));
            if (need.y < 0.2 * G) need = new Vec3(need.x, 0.2 * G, need.z);
            Vec3 up = need.normalize();
            double tilt = Math.toDegrees(Math.acos(Mth.clamp(up.y, -1, 1)));
            if (tilt > AUTO_MAX_TILT) {
                Vec3 h = new Vec3(up.x, 0, up.z).normalize();
                double r = Math.toRadians(AUTO_MAX_TILT);
                up = h.scale(Math.sin(r)).add(0, Math.cos(r), 0);
            }
            thrust = (float) Mth.clamp(need.y / up.y / (t.thrustToWeight() * G), 0, 1);
            Quaternionf target = level(in.yaw()).premul(new Quaternionf().rotationTo(0, 1, 0, (float) up.x, (float) up.y, (float) up.z));
            q.slerp(target, 0.35f).normalize();
        }
        d.setAttitude(q);
        d.setCameraPitch(in.pitch());
        return integrate(d, thrust, motors);
    }

    /** Thrust along the body's up axis, gravity, drag; move with collisions. */
    private static double integrate(DroneEntity d, float thrust, boolean motors) {
        DroneType t = d.type();
        Quaternionf q = d.attitude();
        Vector3f up = q.transform(new Vector3f(0, 1, 0));
        double push = motors ? thrust * t.thrustToWeight() * G : 0;
        Vec3 v = d.velocity();
        Vec3 a = new Vec3(up.x, up.y, up.z).scale(push).add(0, -G, 0).subtract(v.scale(t.drag() * v.length()));
        v = v.add(a.scale(DT));
        if (d.isInWater()) v = v.scale(0.6);
        Vec3 want = v.scale(DT);
        Vec3 before = d.position();
        d.move(MoverType.SELF, want);
        Vec3 moved = d.position().subtract(before);
        double impact = want.subtract(moved).length() / DT;
        // hovering at zero vertical speed never "lands" in the vanilla mover: probe just below
        if (!d.onGround() && v.y <= 0.05) d.setOnGround(!d.level().noCollision(d, d.getBoundingBox().move(0, -0.04, 0)));
        // what actually happened is the new velocity: a wall or the ground took the rest
        v = impact < 0.05 ? v : moved.scale(1 / DT);
        if (d.onGround() && push * up.y < G) { // resting on its feet: it settles level and stops sliding
            v = new Vec3(v.x * 0.5, Math.min(0, v.y), v.z * 0.5);
            d.setAttitude(level(heading(q)));
        }
        d.setVelocity(v);
        d.setThrust(motors ? thrust : 0);
        // rotor effort for sound and rotor speed: idle 0.3, hover ~0.5-0.6, flat out 1
        d.setThrottle(motors ? Mth.clamp(0.3f + 0.7f * thrust * t.thrustToWeight() / (1 + 0.5f * t.thrustToWeight()), 0.3f, 1) : 0);
        d.setYRot(heading(d.attitude()));
        d.setXRot(Mth.clamp(viewPitch(d), -90, 90)); // so a third-person (F5) view orbits with the camera
        return impact > 0.5 ? impact : 0;
    }

    /** Stick expo: soft around the centre, full rate at full stick. */
    static float expo(float stick, float expo) {
        float x = Mth.clamp(stick, -1, 1);
        return x * (1 - expo) + x * x * x * expo;
    }

    /** Level attitude facing {@code yaw} (Minecraft degrees). */
    public static Quaternionf level(float yaw) {
        return new Quaternionf().rotationY((float) (Math.PI - Math.toRadians(yaw)));
    }

    /** Minecraft yaw of the body's nose projected on the ground. */
    public static float heading(Quaternionf q) {
        Vector3f f = q.transform(new Vector3f(0, 0, -1));
        if (f.x * f.x + f.z * f.z < 1e-6f) f = q.transform(new Vector3f(0, 1, 0)).mul(f.y > 0 ? 1 : -1); // nose straight up / down
        return (float) Math.toDegrees(Math.atan2(-f.x, f.z));
    }

    /** The camera's orientation: the body, tilted up by the camera's mount angle. */
    public static Quaternionf camera(Quaternionf body, DroneType t) {
        return new Quaternionf(body).rotateX((float) Math.toRadians(t.cameraTilt()));
    }

    /** Minecraft pitch (+ = down) the camera looks at: the gimbal's, or the hard-mounted camera's. */
    public static float viewPitch(DroneEntity d) {
        if (d.type().gimbal()) return d.cameraPitch();
        Vector3f f = camera(d.attitude(), d.type()).transform(new Vector3f(0, 0, -1));
        return (float) -Math.toDegrees(Math.asin(Mth.clamp(f.y, -1, 1)));
    }

    private DroneFlight() {}
}
