package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.FlashEffect;
import com.modernarmory.network.FlashbangPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Flashbang exposure ("flash" section): a flash 4 m away on the ground, with the player facing north, placed in
 * front, to the side, behind, and in front behind a stone wall. In front must blind hard, side-on far less, behind
 * and behind the wall not at all, while the bang still reaches the ears in every case (muffled by the wall).
 * Then real payloads: a bang alone must not white the screen out, a seen flash must.
 */
final class AutoTestFlash {
    static final int LENGTH = 50;
    private static volatile FlashEffect.Exposure front, side, behind, wall, far;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("fill -6 -60 -28 6 -54 8 minecraft:air");
            cmd("tp @s 0.5 -60 0.5 180 0");
            look(180, 0);
        });
        at(t0 + 6, () -> measure(false));
        at(t0 + 8, () -> cmd("fill -3 -60 -1 3 -56 -1 minecraft:stone"));
        at(t0 + 12, () -> measure(true));
        at(t0 + 16, () -> {
            boolean ok = front.flash() > 0.4f
                    && side.flash() > FlashEffect.THRESHOLD && side.flash() < front.flash() * 0.5f
                    && behind.flash() < FlashEffect.THRESHOLD && behind.bang() > 0.3f && behind.glance()
                    && !front.glance() && !wall.glance() && far.flash() > 0.25f
                    && wall.flash() == 0 && wall.bang() > FlashEffect.THRESHOLD && wall.bang() < front.bang();
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] flash exposure (eyes/ears): far front (25 m) %.2f/%.2f, front %.2f/%.2f, side %.2f/%.2f, behind %.2f/%.2f (glance %s), behind wall %.2f/%.2f (glance %s) -> %s",
                    far.flash(), far.bang(), front.flash(), front.bang(), side.flash(), side.bang(), behind.flash(), behind.bang(), behind.glance(),
                    wall.flash(), wall.bang(), wall.glance(),
                    ok ? "PASS" : "FAIL"));
        });
        // the real packet: a bang you didn't see rings but doesn't blind
        at(t0 + 18, () -> send(0, 0.8f, false));
        at(t0 + 24, () -> {
            float a = FlashbangOverlay.current();
            ModernArmory.LOGGER.info("[autotest] flash unseen bang: white-out {} -> {}", a, a == 0 ? "PASS" : "FAIL");
        });
        // turned away from a flash in the open: white for an instant (under 0.1 s), then clear
        at(t0 + 25, () -> {
            FlashbangOverlay.onFlash(new FlashbangPayload(0, 0.8f, true));
            glanceAt0 = FlashbangOverlay.current();
        });
        at(t0 + 28, () -> {
            float a = FlashbangOverlay.current();
            ModernArmory.LOGGER.info("[autotest] flash glance (turned away): white-out {} at once, {} 0.15 s later -> {}", glanceAt0, a,
                    glanceAt0 >= 0.9f && a == 0 ? "PASS" : "FAIL");
        });
        at(t0 + 30, () -> send(0.6f, 0.6f, false));
        at(t0 + 34, () -> {
            float a = FlashbangOverlay.current();
            ModernArmory.LOGGER.info("[autotest] flash seen: white-out {} -> {}", a, a >= 0.99f ? "PASS" : "FAIL");
        });
        at(t0 + 36, () -> cmd("fill -3 -60 -1 3 -56 -1 minecraft:air"));
    }

    private static void measure(boolean walled) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) sp.level();
            double y = -60 + 0.2;
            if (walled) {
                wall = FlashEffect.exposure(level, sp, new Vec3(0.5, y, -3.5));
            } else {
                front = FlashEffect.exposure(level, sp, new Vec3(0.5, y, -3.5));
                side = FlashEffect.exposure(level, sp, new Vec3(4.5, y, 0.5));
                behind = FlashEffect.exposure(level, sp, new Vec3(0.5, y, 4.5));
                far = FlashEffect.exposure(level, sp, new Vec3(0.5, -58.4, -24.5)); // an overhand throw's airburst, 25 m out, eye level
            }
        });
    }

    private static volatile float glanceAt0;

    private static void send(float flash, float bang, boolean glance) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> PacketDistributor.sendToPlayer(server.getPlayerList().getPlayers().get(0), new FlashbangPayload(flash, bang, glance)));
    }

    private AutoTestFlash() {}
}
