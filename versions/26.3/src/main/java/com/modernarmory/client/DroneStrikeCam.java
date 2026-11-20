package com.modernarmory.client;

import com.modernarmory.network.DroneBlastPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The strike camera: when the pilot's FPV charge goes off, the view cuts for {@link #TICKS} to a plain view from a vantage point
 * that sees the blast (as if from an overwatch drone) before going back to the body. The spot is searched
 * behind the drone's approach first, then around it: a few metres out and a little above the blast, in open
 * air, with a clear line of sight. No such spot (in a tunnel, inside a building): no strike camera.
 */
final class DroneStrikeCam {
    /** How long the strike camera shows the blast (2 s). */
    static final int TICKS = 40;
    private static final double[] DISTANCES = {8, 11, 5.5};
    private static final double[] HEIGHTS = {3.5, 2, 6};
    private static final double[] ANGLES = {0, 35, -35, 70, -70, 110, -110, 150, -150, 180};

    /** A camera (a marker entity, never added to the world) looking at the blast, or null if nowhere sees it. */
    static Marker create(Minecraft mc, DroneBlastPayload p) {
        Vec3 spot = find(mc.level, mc, p.pos(), p.velocity());
        if (spot == null) return null;
        Marker cam = new Marker(EntityTypes.MARKER, mc.level);
        Vec3 d = p.pos().subtract(spot);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        cam.snapTo(spot.x, spot.y, spot.z, yaw, pitch); // 26.x: snapTo replaces moveTo
        cam.xo = spot.x;
        cam.yo = spot.y;
        cam.zo = spot.z;
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        return cam;
    }

    private static Vec3 find(Level level, Minecraft mc, Vec3 blast, Vec3 velocity) {
        Vec3 back = new Vec3(-velocity.x, 0, -velocity.z);
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize();
        for (double dist : DISTANCES) {
            for (double h : HEIGHTS) {
                for (double a : ANGLES) {
                    Vec3 spot = blast.add(back.yRot((float) Math.toRadians(a)).scale(dist)).add(0, h, 0);
                    if (!level.hasChunkAt(BlockPos.containing(spot))) continue;
                    if (!level.noCollision(new AABB(spot, spot).inflate(0.5))) continue; // open air around the lens
                    var hit = level.clip(new ClipContext(spot, blast, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(blast) < 1.5) return spot;
                }
            }
        }
        return null;
    }

    private DroneStrikeCam() {}
}
