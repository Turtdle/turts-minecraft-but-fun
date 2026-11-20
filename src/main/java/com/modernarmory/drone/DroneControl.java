package com.modernarmory.drone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.modernarmory.ModernArmory;
import com.modernarmory.network.DroneCorrectPayload;
import com.modernarmory.network.DronePilotPayload;
import com.modernarmory.network.DroneViewPayload;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of piloting: who is looking through which drone's camera, starting and ending that, and the
 * pilot's actions. The pilot's body stays where it is (and can be hurt) while they fly.
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class DroneControl {
    public static final int ACTION_EXIT = 0, ACTION_PRIMARY = 1;

    /** Player -> the drone they are flying. */
    private static final Map<UUID, DroneEntity> PILOTING = new HashMap<>();

    /** Puts {@code p} behind {@code d}'s camera if the drone is free, has charge and is in range. */
    public static boolean startPiloting(ServerPlayer p, DroneEntity d) {
        if (d.isRemoved()) return false;
        ServerPlayer other = d.pilot();
        if (other != null && other != p) return fail(p, "Another pilot is flying this drone");
        if (d.battery() <= 0) return fail(p, "Drone battery is flat");
        if (d.jammed()) return fail(p, "No link: the drone is being jammed");
        float s = d.level() == p.level() ? DroneLink.link((ServerLevel) d.level(), d.type(), p, d.getEyePosition()).signal() : 0;
        if (d.level() != p.level() || s < DroneLink.REGAIN) return fail(p, "No signal: the drone is out of range");
        DroneEntity previous = PILOTING.get(p.getUUID());
        if (previous != null && previous != d) previous.setPilot(-1);
        d.setPilot(p.getId());
        d.setSignal(s);
        if (!d.motorsOn()) d.setThrottle(0.3f);
        PILOTING.put(p.getUUID(), d);
        PacketDistributor.sendToPlayer(p, new DroneViewPayload(d.getId()));
        cue(p, ModSounds.DRONE_BEEP.get(), 0.8f, 1.25f);
        return true;
    }

    private static boolean fail(ServerPlayer p, String why) {
        p.displayClientMessage(Component.literal(why), true);
        cue(p, ModSounds.DRONE_BEEP.get(), 0.6f, 0.6f);
        return false;
    }

    /** The drone this player is flying, or null. */
    public static DroneEntity piloting(ServerPlayer p) {
        DroneEntity d = PILOTING.get(p.getUUID());
        return d != null && !d.isRemoved() && d.pilotId() == p.getId() ? d : null;
    }

    /** Ends whoever flies {@code d}; {@code notify} sends them back to their body. */
    static void release(DroneEntity d, boolean notify) {
        d.setPilot(-1);
        for (Iterator<Map.Entry<UUID, DroneEntity>> it = PILOTING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, DroneEntity> e = it.next();
            if (e.getValue() != d) continue;
            it.remove();
            ServerPlayer p = d.getServer() == null ? null : d.getServer().getPlayerList().getPlayer(e.getKey());
            if (notify && p != null) PacketDistributor.sendToPlayer(p, new DroneViewPayload(-1));
        }
    }

    static void onRemoved(DroneEntity d) {
        release(d, true);
    }

    /** The pilot left the camera view (their client is already back in the body). */
    public static void exit(ServerPlayer p) {
        DroneEntity d = PILOTING.remove(p.getUUID());
        if (d != null && d.pilotId() == p.getId()) d.setPilot(-1);
    }

    public static void action(ServerPlayer p, int action) {
        if (action == ACTION_EXIT) {
            exit(p);
            return;
        }
        DroneEntity d = piloting(p);
        if (d == null || !d.pilotControls()) return;
        if (action == ACTION_PRIMARY) d.type().payload().primary((ServerLevel) d.level(), d, p);
    }

    public static void pilotUpdate(ServerPlayer p, DronePilotPayload payload) {
        if (p.level().getEntity(payload.drone()) instanceof DroneEntity d) d.acceptPilotUpdate(p, payload);
    }

    static void correct(ServerPlayer p, DroneEntity d) {
        PacketDistributor.sendToPlayer(p, new DroneCorrectPayload(d.getId(), d.position()));
    }

    /** A sound only the pilot hears, not tied to a place (the controller beeping in their hands). */
    public static void cue(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        PacketDistributor.sendToPlayer(p, new LocalSoundPayload(sound.getLocation(), volume, pitch, 0));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) exit(p);
    }

    private DroneControl() {}
}
