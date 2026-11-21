package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;

import com.modernarmory.ModernArmory;

import net.minecraft.world.phys.Vec3;

/**
 * The body stays put while you fly ("dronestill" section): sprinting forward, jump, and deploy a scout drone mid-air
 * (the controller takes you straight into its camera) with forward still held. The body must stop where it was, not
 * carry its run-up and jump on while you fly.
 */
final class AutoTestDroneStill {
    static final int LENGTH = 80;
    private static Vec3 atEntry;
    private static double drift = -1;
    private static boolean entered;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:drone]"); cmd("clear @s"); cmd("gamemode creative"); cmd("time set noon");
            cmd("fill -6 -60 -30 6 -45 3 minecraft:air");
            cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("give @s modernarmory:drone_controller"); cmd("give @s modernarmory:drone_scout");
            atEntry = null; drift = -1; entered = false;
        });
        at(t0 + 6, () -> { AutoTest.mc().player.getInventory().setSelectedSlot(1); look(180, 40); });
        at(t0 + 10, () -> { AutoTest.mc().options.keyUp.setDown(true); AutoTest.mc().options.keySprint.setDown(true); });
        at(t0 + 22, () -> AutoTest.mc().options.keyJump.setDown(true));
        at(t0 + 23, () -> { AutoTest.mc().options.keyJump.setDown(false); AutoTest.mc().options.keyUse.setDown(true); }); // deploy in the air
        at(t0 + 24, () -> AutoTest.mc().options.keyUse.setDown(false));
        for (int t = t0 + 24; t < t0 + 70; t++) at(t, () -> {
            if (!entered && DronePilot.active()) {
                entered = true;
                atEntry = AutoTest.mc().player.position();
            }
        });
        at(t0 + 70, () -> {
            if (atEntry != null) {
                Vec3 now = AutoTest.mc().player.position();
                drift = Math.hypot(now.x - atEntry.x, now.z - atEntry.z);
            }
            AutoTest.mc().options.keyUp.setDown(false);
            AutoTest.mc().options.keySprint.setDown(false);
            boolean ok = entered && drift >= 0 && drift < 0.25;
            ModernArmory.LOGGER.info("[autotest] drone still: piloting {}, body drifted {} blocks after taking off mid-jump -> {}",
                    entered, String.format("%.2f", drift), ok ? "PASS" : "FAIL");
            if (DronePilot.active()) DronePilot.leave(true, false);
        });
        at(t0 + 76, () -> { cmd("kill @e[type=modernarmory:drone]"); look(180, 0); });
    }

    private AutoTestDroneStill() {}
}
