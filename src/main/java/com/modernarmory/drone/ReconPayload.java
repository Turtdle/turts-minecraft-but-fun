package com.modernarmory.drone;

import java.util.ArrayList;
import java.util.List;

import com.modernarmory.network.DroneSpotPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A reconnaissance camera: the primary action tags what is under the crosshair (and anything standing
 * right next to it). Tagged targets are outlined for the pilot for {@code spotSeconds}, through walls.
 *
 * @param range        how far the camera can pick out a target (m)
 * @param groupRadius  others within this distance of the target are tagged with it (m)
 * @param spotSeconds  how long the outline lasts
 */
public record ReconPayload(double range, double groupRadius, float spotSeconds) implements DronePayload {
    @Override
    public String actionLabel() {
        return "SPOT";
    }

    @Override
    public void primary(ServerLevel level, DroneEntity drone, ServerPlayer pilot) {
        Entity target = aimed(level, drone, pilot, range);
        if (target == null) {
            DroneControl.cue(pilot, ModSounds.DRONE_BEEP.get(), 0.6f, 0.6f);
            return;
        }
        List<Integer> ids = new ArrayList<>();
        ids.add(target.getId());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(groupRadius),
                e -> e != target && e != pilot && e.isAlive() && !e.isSpectator())) {
            if (ids.size() >= 16) break;
            ids.add(e.getId());
        }
        PacketDistributor.sendToPlayer(pilot, new DroneSpotPayload(ids, Math.round(spotSeconds * 20)));
        DroneControl.cue(pilot, ModSounds.DRONE_SPOT.get(), 0.9f, 1.0f);
    }

    /** The living thing under the drone camera's crosshair, up to {@code range}, not behind a block. */
    static Entity aimed(ServerLevel level, DroneEntity drone, ServerPlayer pilot, double range) {
        Vec3 eye = drone.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(drone.cameraPitch(), drone.getYRot()).scale(range));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, drone));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, drone, eye, end, new AABB(eye, end).inflate(1.5),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != pilot, 0.6f);
        return hit == null ? null : hit.getEntity();
    }
}
