package com.modernarmory.drone;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Radio jamming of drones (server side), for anti-drone jammers. A jammed drone has no control link however
 * strong its signal: it does what its type does when the link is lost ({@link LinkLoss}: the scout holds,
 * then flies home on its own navigation; the FPV holds, then its failsafe cuts the motors), the pilot's
 * sticks and trigger do nothing, and the pilot's camera feed breaks up into static. Jamming has to be kept
 * up: when it stops, the drone gets its link back the way it does coming back into range.
 */
public final class DroneJamming {
    /**
     * Jams {@code drone} for at least the next {@code ticks} server ticks (a jammer calls this every tick
     * while the drone is in its beam, with a few ticks to spare).
     *
     * @param jammer who is jamming it (may be null); not used for now
     */
    public static void jam(DroneEntity drone, int ticks, @Nullable Entity jammer) {
        if (drone.isRemoved() || ticks <= 0) return;
        drone.jam(ticks);
    }

    /**
     * Drones inside a cone: within {@code range} of {@code origin} and within {@code halfAngleDeg} of
     * {@code dir}. Only the cone's bounding box is searched. Blocks don't stop the cone (jamming goes through
     * cover); filter the result further if a jammer should need a clear path.
     */
    public static List<DroneEntity> inCone(ServerLevel level, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
        Vec3 axis = dir.normalize();
        Vec3 end = origin.add(axis.scale(range));
        double r = range * Math.tan(Math.toRadians(Math.min(halfAngleDeg, 89)));
        double cos = Math.cos(Math.toRadians(halfAngleDeg));
        AABB box = new AABB(origin, end).inflate(r);
        return level.getEntitiesOfClass(DroneEntity.class, box, d -> {
            if (d.isRemoved()) return false;
            Vec3 to = d.position().add(0, d.getBbHeight() / 2, 0).subtract(origin);
            double len = to.length();
            return len <= range && (len < 0.5 || to.dot(axis) / len >= cos);
        });
    }

    private DroneJamming() {}
}
