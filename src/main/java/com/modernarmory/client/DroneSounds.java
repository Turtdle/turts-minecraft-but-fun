package com.modernarmory.client;

import java.util.HashMap;
import java.util.Map;

import com.modernarmory.drone.DroneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Rotor noise: one looping sound per drone whose motors run, following it, louder and higher with rotor
 * effort. Through the drone's own camera it is turned down (the pilot hears it from the controller's
 * microphone-less feed only faintly, and the world around the drone stays audible).
 */
final class DroneSounds {
    private static final Map<Integer, Rotor> PLAYING = new HashMap<>();

    static void tick(Minecraft mc) {
        PLAYING.values().removeIf(Rotor::isStopped);
        if (mc.level == null) return;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof DroneEntity d) || !d.motorsOn() || PLAYING.containsKey(d.getId())) continue;
            Rotor r = new Rotor(d);
            PLAYING.put(d.getId(), r);
            mc.getSoundManager().play(r);
        }
    }

    static void clear() {
        PLAYING.clear();
    }

    private static final class Rotor extends AbstractTickableSoundInstance {
        private final DroneEntity drone;

        Rotor(DroneEntity d) {
            super(d.type().rotorSound().get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            drone = d;
            looping = true;
            delay = 0;
            attenuation = Attenuation.LINEAR;
            update();
        }

        private void update() {
            x = drone.getX();
            y = drone.getY();
            z = drone.getZ();
            float t = drone.throttle();
            boolean own = Minecraft.getInstance().getCameraEntity() == drone;
            volume = (0.25f + 0.75f * t) * (own ? 0.3f : 1f);
            pitch = 0.75f + 0.55f * t;
        }

        @Override
        public void tick() {
            if (drone.isRemoved() || !drone.motorsOn()) {
                stop();
                return;
            }
            update();
        }
    }

    private DroneSounds() {}
}
