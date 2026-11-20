package com.modernarmory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a bullet's tracer should appear to start. Bullets fly from the shooter's eye (so they land
 * exactly where the sights point), like in most shooters; the visible tracer starts at the gun's muzzle
 * instead and eases onto the true path within the first few dozen metres (see {@link #fade}).
 */
public final class TracerOrigin {
    /** Distance (m) over which the tracer merges into the bullet's real path. */
    public static final double MERGE_DISTANCE = 60;

    /**
     * Muzzle position minus eye position, in world space, for a bullet just fired by {@code shooter} along
     * {@code dir}. First person: between the hip and the sights by aim progress; anyone seen from outside
     * (other players, or you in F5): near the gun hand.
     */
    public static Vec3 offset(Entity shooter, Vec3 dir) {
        if (!(shooter instanceof LivingEntity s) || dir.lengthSqr() < 1e-8) return Vec3.ZERO;
        Minecraft mc = Minecraft.getInstance();
        boolean local = s == mc.player;
        boolean firstPerson = local && mc.options.getCameraType().isFirstPerson();
        float aim = local ? ClientState.aimProgress() : 0f;
        Vec3 fwd = dir.normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(fwd);
        double rx, uy, fz;
        if (firstPerson) {
            rx = Mth.lerp(aim, 0.17, 0.0);
            uy = Mth.lerp(aim, -0.15, -0.05);
            fz = 0.75;
        } else {
            rx = Mth.lerp(aim, 0.33, 0.27);
            uy = Mth.lerp(aim, -0.28, -0.12);
            fz = Mth.lerp(aim, 0.75, 0.95);
        }
        if (s.getMainArm() == HumanoidArm.LEFT) rx = -rx;
        return right.scale(rx).add(up.scale(uy)).add(fwd.scale(fz));
    }

    /** How much of the muzzle offset is left after {@code flown} metres: 1 at the muzzle, 0 by MERGE_DISTANCE. */
    public static double fade(double flown) {
        double u = 1 - Mth.clamp(flown / MERGE_DISTANCE, 0, 1);
        return u * u;
    }

    private TracerOrigin() {}
}
