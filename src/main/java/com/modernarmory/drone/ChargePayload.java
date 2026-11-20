package com.modernarmory.drone;

import com.modernarmory.Config;
import com.modernarmory.grenade.FragEffect;
import com.modernarmory.network.DroneBlastPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A fragmentation charge carried by the drone, with an impact switch on its nose. It is safe until the
 * drone has flown for {@code armSeconds}; armed, it goes off when the drone hits something at
 * {@code impactSpeed} or more, flies into someone, is shot down, or when the pilot fires it (primary
 * action). The blast is the hand grenades' ({@link FragEffect#burst}): an explosion plus steel fragments
 * flown by the ballistics model, all around since it goes off in the air. {@code breaksBlocks} (and the config
 * droneChargeBreaksBlocks) let it crater the terrain. The pilot is sent where it went off, so their client can
 * show it from a strike camera (client.DroneStrikeCam) before going back to their body.
 */
public record ChargePayload(float blastRadius, int fragments, float armSeconds, double impactSpeed, boolean breaksBlocks)
        implements DronePayload {
    @Override
    public String actionLabel() {
        return "DET";
    }

    @Override
    public int armTicks() {
        return Math.round(armSeconds * 20);
    }

    @Override
    public void primary(ServerLevel level, DroneEntity drone, ServerPlayer pilot) {
        if (drone.armed()) detonate(level, drone);
        else DroneControl.cue(pilot, ModSounds.DRONE_BEEP.get(), 0.7f, 0.6f);
    }

    @Override
    public void onImpact(ServerLevel level, DroneEntity drone, double speed) {
        if (drone.armed() && speed >= impactSpeed) detonate(level, drone);
        else DronePayload.super.onImpact(level, drone, speed);
    }

    @Override
    public void onContact(ServerLevel level, DroneEntity drone, LivingEntity target) {
        if (drone.armed()) detonate(level, drone);
    }

    @Override
    public void onDestroyed(ServerLevel level, DroneEntity drone) {
        if (drone.armed()) detonate(level, drone);
    }

    private void detonate(ServerLevel level, DroneEntity drone) {
        Vec3 p = drone.position().add(0, drone.getBbHeight() * 0.5, 0);
        LivingEntity by = drone.controller();
        ServerPlayer pilot = drone.pilot();
        // the pilot's client looks for a vantage point to watch it from (sent before the drone's removal ends the view)
        if (pilot != null) PacketDistributor.sendToPlayer(pilot, new DroneBlastPayload(p, drone.velocity()));
        drone.markDetonated();
        level.playSound(null, p.x, p.y, p.z, ModSounds.DRONE_DETONATION.get(), SoundSource.PLAYERS, 4.0f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
        FragEffect.burst(level, drone, by, p, blastRadius, fragments, -1.0, breaksBlocks && Config.DRONE_CHARGE_BREAKS_BLOCKS.get());
        drone.discard();
    }
}
