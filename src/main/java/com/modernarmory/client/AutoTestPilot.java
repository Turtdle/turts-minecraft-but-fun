package com.modernarmory.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.modernarmory.drone.AcroSticks;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.drone.DroneType;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Autotest: the pilot's hands for an acro drone. The drone has no self-levelling, so the test flies it the way
 * a person does: it works out which way the thrust has to point and moves the pitch / roll / throttle sticks to
 * get it there, every tick. Modes: fly to (and hold) a point, land, flip.
 */
final class AutoTestPilot {
    private static final double G = 9.81;
    private static Vec3 target;
    private static double speed = 6, maxTiltDeg = 40;
    private static boolean land;
    private static int flipTicks;
    private static Float faceYaw;
    private static java.util.Deque<Vec3> route = new java.util.ArrayDeque<>();
    private static float gimbal;

    static void install() {
        DronePilot.testPilot = AutoTestPilot::sticks;
        DronePilot.testInput = AutoTestPilot::input;
    }

    static void flyTo(Vec3 p, double maxSpeed) {
        flyTo(p, maxSpeed, 40);
    }

    static void flyTo(Vec3 p, double maxSpeed, double maxTilt) {
        route.clear();
        maxTiltDeg = maxTilt;
        target = p;
        speed = maxSpeed;
        land = false;
        flipTicks = 0;
    }

    /** Fly through these points in turn, each one once it is reached (within a metre), holding the last. */
    static void route(double maxSpeed, Vec3... points) {
        flyTo(points[0], maxSpeed);
        route = new java.util.ArrayDeque<>(java.util.List.of(points));
        route.poll();
    }

    static void land() {
        land = true;
        flipTicks = 0;
    }

    /** A back flip with the sprint (high) rates, then back to holding {@link #target}. */
    static void flip(int ticks) {
        flipTicks = ticks;
    }

    /** Turn the nose (and the gimbal) to look this way; null = keep the heading. */
    static void face(Float yaw, float gimbalPitch) {
        faceYaw = yaw;
        gimbal = gimbalPitch;
    }

    private static AcroSticks sticks(DroneEntity d) {
        DroneType t = d.type();
        float yawDeg = faceYaw == null ? 0 : Mth.clamp(Mth.wrapDegrees(faceYaw - d.getYRot()), -12, 12);
        if (flipTicks > 0) {
            flipTicks--;
            return new AcroSticks(-1, 0, 0, 0, 0, 0, 0.35f, true, gimbal); // full back-flip rate, a little throttle
        }
        Vec3 pos = d.position(), v = d.velocity();
        if (target != null && !route.isEmpty() && pos.distanceTo(target) < 1.0) target = route.poll(); // next waypoint
        Vec3 want;
        if (land) {
            if (d.onGround()) return new AcroSticks(0, 0, 0, 0, 0, yawDeg, 0, false, gimbal);
            want = new Vec3(-v.x, -1.5 - v.y, -v.z).scale(2);
        } else {
            Vec3 tgt = target != null ? target : pos;
            Vec3 vDes = tgt.subtract(pos).scale(1.2);
            if (vDes.length() > speed) vDes = vDes.normalize().scale(speed);
            want = vDes.subtract(v).scale(2.5);
        }
        if (want.length() > 25) want = want.normalize().scale(25);
        Vec3 need = want.add(0, G, 0).add(v.scale(t.drag() * v.length()));
        if (need.y < 2) need = new Vec3(need.x, 2, need.z);
        Vec3 up = need.normalize();
        double tilt = Math.acos(Mth.clamp(up.y, -1, 1)), max = Math.toRadians(maxTiltDeg);
        if (tilt > max) {
            Vec3 h = new Vec3(up.x, 0, up.z).normalize();
            up = h.scale(Math.sin(max)).add(0, Math.cos(max), 0);
        }
        // the wanted up direction seen from the airframe: lean it over with the pitch / roll sticks
        Quaternionf q = d.attitude();
        Vector3f bodyUp = q.transform(new Vector3f(0, 1, 0));
        Vector3f wantB = new Quaternionf(q).conjugate().transform(new Vector3f((float) up.x, (float) up.y, (float) up.z));
        float perTick = t.rateDeg() * 0.05f;
        float pitch = Mth.clamp((float) Math.toDegrees(-wantB.z) / (3 * perTick), -1, 1);
        float roll = Mth.clamp((float) Math.toDegrees(wantB.x) / (3 * perTick), -1, 1);
        float throttle = (float) Mth.clamp(need.dot(new Vec3(bodyUp.x, bodyUp.y, bodyUp.z)) / (t.thrustToWeight() * G), 0, 1);
        return new AcroSticks(pitch, roll, 0, 0, 0, yawDeg, throttle, false, gimbal);
    }

    /**
     * Stabilised mode: the velocity to fly toward the target (or down to land), given as the sticks a pilot would hold:
     * forward / strafe relative to the heading (a share of the cruise speed, or of the top speed with sprint), lift (a
     * share of the climb rate); the heading turns toward {@link #face} if set; the camera holds the gimbal pitch.
     */
    private static com.modernarmory.drone.DroneInput input(DroneEntity d) {
        DroneType t = d.type();
        float yaw = faceYaw != null ? faceYaw : d.getYRot();
        Vec3 pos = d.position();
        if (target != null && !route.isEmpty() && pos.distanceTo(target) < 1.0) target = route.poll(); // next waypoint
        if (land) return new com.modernarmory.drone.DroneInput(0, 0, d.onGround() ? 0 : -1, false, yaw, gimbal);
        Vec3 tgt = target != null ? target : pos;
        Vec3 vDes = tgt.subtract(pos).scale(1.2);
        if (vDes.length() > speed) vDes = vDes.normalize().scale(speed);
        boolean boost = speed > t.cruiseSpeed();
        double base = boost ? t.maxSpeed() : t.cruiseSpeed();
        double r = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        float f = (float) Mth.clamp(vDes.dot(fwd) / base, -1, 1), s = (float) Mth.clamp(vDes.dot(right) / base, -1, 1);
        float lift = (float) Mth.clamp(vDes.y / t.climbRate(), -1, 1);
        return new com.modernarmory.drone.DroneInput(f, s, lift, boost, yaw, gimbal);
    }

    private AutoTestPilot() {}
}
