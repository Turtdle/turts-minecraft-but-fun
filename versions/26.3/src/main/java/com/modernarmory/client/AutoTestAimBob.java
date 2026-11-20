package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import com.modernarmory.ModernArmory;

/**
 * Mastery aim bob ("aimbob" section): walks aimed in with an M4 at mastery 0, then at mastery V, and checks
 * the view bob is untouched at 0 and scaled down at V (AimBob). On 26.3 this also proves the bob field is reachable.
 */
final class AutoTestAimBob {
    static final int LENGTH = 110;
    private static float scale0, scale5, raw5, drawn5;

    static void script(int t0) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:m4"); cmd("gunmastery set @s m4 0");
            cmd("fill -3 -60 -3 3 -55 30 minecraft:air"); cmd("tp @s 0.5 -60 0.5 0 0");
        });
        at(t0 + 4, () -> mc().player.getInventory().setSelectedSlot(0));
        walkAimed(t0 + 10, true);
        at(t0 + 40, () -> scale0 = AimBob.lastScale);
        walkAimed(t0 + 42, false);
        at(t0 + 50, () -> { cmd("gunmastery set @s m4 4000"); cmd("tp @s 0.5 -60 0.5 0 0"); });
        walkAimed(t0 + 60, true);
        at(t0 + 90, () -> { scale5 = AimBob.lastScale; raw5 = AimBob.lastRawBob; drawn5 = AimBob.lastScaledBob; });
        walkAimed(t0 + 92, false);
        at(t0 + 96, () -> {
            boolean ok = scale0 > 0.99f && scale5 < 0.3f && raw5 > 0.01f && drawn5 < raw5 * 0.3f;
            ModernArmory.LOGGER.info("[autotest] aimbob: mastery 0 scale {}, mastery V scale {} (frame camera bob {} drawn as {}) -> {}",
                    scale0, scale5, raw5, drawn5, ok ? "PASS" : "FAIL");
            cmd("gunmastery set @s m4 0");
        });
    }

    private static void walkAimed(int t, boolean on) {
        at(t, () -> {
            if (on) look(0, 0);
            mc().options.keyUse.setDown(on);
            mc().options.keyUp.setDown(on);
        });
    }

    private AutoTestAimBob() {}
}
