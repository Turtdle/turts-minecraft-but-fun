package com.modernarmory.network;

import com.modernarmory.ModernArmory;
import com.modernarmory.server.GunHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ModernArmory.MODID)
public final class ModNetwork {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(GunInputPayload.TYPE, GunInputPayload.STREAM_CODEC,
                (p, ctx) -> GunHandler.setInput((ServerPlayer) ctx.player(), p.trigger(), p.aiming()));
        r.playToServer(GrenadeInputPayload.TYPE, GrenadeInputPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.grenade.GrenadeHandler.setInput((ServerPlayer) ctx.player(), p.left(), p.right()));
        r.playToServer(GunActionPayload.TYPE, GunActionPayload.STREAM_CODEC,
                (p, ctx) -> GunHandler.action((ServerPlayer) ctx.player(), p.action()));
        // Client-bound handlers are registered from the client side (RegisterClientPayloadHandlersEvent in
        // ModernArmoryClient), which keeps client classes out of common code.
        r.playToClient(ShotFiredPayload.TYPE, ShotFiredPayload.STREAM_CODEC);
        r.playToClient(MasterySyncPayload.TYPE, MasterySyncPayload.STREAM_CODEC);
        r.playToClient(FlashbangPayload.TYPE, FlashbangPayload.STREAM_CODEC);
        // laser turret (laser.TurretSeat)
        r.playToServer(LaserCommandPayload.TYPE, LaserCommandPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player().getVehicle() instanceof com.modernarmory.laser.TurretSeat s)
                s.command((ServerPlayer) ctx.player(), p.action(), p.target());
        });
        r.playToClient(LocalSoundPayload.TYPE, LocalSoundPayload.STREAM_CODEC);
        // drones (drone.DroneControl server side, client.DronePilot client side)
        r.playToServer(DronePilotPayload.TYPE, DronePilotPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.drone.DroneControl.pilotUpdate((ServerPlayer) ctx.player(), p));
        r.playToServer(DroneActionPayload.TYPE, DroneActionPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.drone.DroneControl.action((ServerPlayer) ctx.player(), p.action()));
        r.playToClient(DroneViewPayload.TYPE, DroneViewPayload.STREAM_CODEC);
        r.playToClient(DroneSpotPayload.TYPE, DroneSpotPayload.STREAM_CODEC);
        r.playToClient(DroneCorrectPayload.TYPE, DroneCorrectPayload.STREAM_CODEC);
        r.playToClient(DroneBlastPayload.TYPE, DroneBlastPayload.STREAM_CODEC);
    }

    private ModNetwork() {}
}
