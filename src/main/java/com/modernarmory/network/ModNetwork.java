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
        PayloadRegistrar r = event.registrar("2"); // 2: client-predicted shots
        r.playToServer(GunInputPayload.TYPE, GunInputPayload.STREAM_CODEC,
                (p, ctx) -> GunHandler.setInput((ServerPlayer) ctx.player(), p.trigger(), p.aiming()));
        r.playToServer(GrenadeInputPayload.TYPE, GrenadeInputPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.grenade.GrenadeHandler.setInput((ServerPlayer) ctx.player(), p.left(), p.right()));
        r.playToServer(GunActionPayload.TYPE, GunActionPayload.STREAM_CODEC,
                (p, ctx) -> GunHandler.action((ServerPlayer) ctx.player(), p.action()));
        // shots: the shooter's client fires and shows the round at once, the server checks and fires the real one
        r.playToServer(ShootPayload.TYPE, ShootPayload.STREAM_CODEC,
                (p, ctx) -> GunHandler.shoot((ServerPlayer) ctx.player(), p));
        // The client class is only touched when this handler runs, which is always on the client.
        r.playToClient(ShotAckPayload.TYPE, ShotAckPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.ShotPredictor.onAck(p));
        r.playToClient(GunStatePayload.TYPE, GunStatePayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.ShotPredictor.onState(p));
        r.playToClient(GunAnimPayload.TYPE, GunAnimPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.ShotPredictor.onOthersAnim(p));
        r.playToClient(MasterySyncPayload.TYPE, MasterySyncPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.ClientMastery.onSync(p));
        r.playToClient(FlashbangPayload.TYPE, FlashbangPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.FlashbangOverlay.onFlash(p));
        // drones (drone.DroneControl server side, client.DronePilot client side)
        r.playToServer(DronePilotPayload.TYPE, DronePilotPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.drone.DroneControl.pilotUpdate((ServerPlayer) ctx.player(), p));
        r.playToServer(DroneActionPayload.TYPE, DroneActionPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.drone.DroneControl.action((ServerPlayer) ctx.player(), p.action()));
        r.playToClient(DroneViewPayload.TYPE, DroneViewPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.DronePilot.onView(p));
        r.playToClient(DroneSpotPayload.TYPE, DroneSpotPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.DronePilot.onSpot(p));
        r.playToClient(DroneCorrectPayload.TYPE, DroneCorrectPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.DronePilot.onCorrect(p));
        r.playToClient(DroneBlastPayload.TYPE, DroneBlastPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.DronePilot.onBlast(p));
        // laser turret (laser.TurretSeat)
        r.playToServer(LaserCommandPayload.TYPE, LaserCommandPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player().getVehicle() instanceof com.modernarmory.laser.TurretSeat s)
                s.command((ServerPlayer) ctx.player(), p.action(), p.target());
        });
        r.playToClient(LocalSoundPayload.TYPE, LocalSoundPayload.STREAM_CODEC,
                (p, ctx) -> com.modernarmory.client.ClientState.onLocalSound(p));
    }

    private ModNetwork() {}
}
