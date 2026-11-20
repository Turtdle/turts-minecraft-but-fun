package com.modernarmory.entity;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLEnvironment;

/** Safe access to the local player from common code (returns null on a dedicated server). */
final class ClientAccess {
    static Player localPlayer() {
        if (!FMLEnvironment.dist.isClient()) return null;
        return com.modernarmory.client.ClientState.localPlayer();
    }

    /** Muzzle-minus-eye offset for this bullet's tracer (client only; see client.TracerOrigin). */
    static net.minecraft.world.phys.Vec3 tracerOffset(net.minecraft.world.entity.Entity shooter, net.minecraft.world.phys.Vec3 dir) {
        if (!FMLEnvironment.dist.isClient()) return net.minecraft.world.phys.Vec3.ZERO;
        return com.modernarmory.client.TracerOrigin.offset(shooter, dir);
    }

    private ClientAccess() {}
}
