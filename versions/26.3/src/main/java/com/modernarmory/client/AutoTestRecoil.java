package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import com.modernarmory.ModernArmory;
import com.modernarmory.gun.FireMode;
import com.modernarmory.item.GunItem;

/**
 * Recoil recovery ("recoil" section): a fresh M4A1 must start on full auto (the default for guns that have
 * it); a half-second spray from a level aim must climb, then come all the way back down to where it started
 * once the trigger is let go. Then the same spray while "pulling down" 3 degrees: the pull counts as
 * recovery, so it still ends on the start.
 */
final class AutoTestRecoil {
    private static final int HOLD = 10, PART = 20 + HOLD + 50;
    static final int LENGTH = 2 * PART;
    private static float start, peak, startYaw, drift;
    private static String mode = "-";

    static void script(int t0) {
        spray(t0, false);
        spray(t0 + PART, true);
    }

    private static void spray(int t0, boolean pullDown) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:m4a1");
            cmd("fill -3 -60 -3 3 -55 3 minecraft:air"); cmd("tp @s 0.5 -60 0.5 0 0");
        });
        at(t0 + 4, () -> mc().player.getInventory().setSelectedSlot(0));
        at(t0 + 14, () -> look(0, 5));
        at(t0 + 18, () -> {
            startYaw = mc_yaw(); drift = 0;
            start = peak = mc().player.getXRot();
            var st = mc().player.getMainHandItem();
            mode = st.getItem() instanceof GunItem g ? g.fireMode(st).name() : "-";
        });
        at(t0 + 20, () -> mc().options.keyAttack.setDown(true));
        for (int k = 1; k <= HOLD + 6; k++) {
            int kk = k;
            at(t0 + 20 + k, () -> {
                if (kk == HOLD) mc().options.keyAttack.setDown(false);
                if (pullDown && (kk == 4 || kk == 6 || kk == 8)) mc().player.turn(0, 1.0 / 0.15); // the player drags 1 deg down
                drift = Math.max(drift, Math.abs(mc_yaw() - startYaw));
                peak = Math.min(peak, mc().player.getXRot());
            });
        }
        at(t0 + 20 + HOLD + 45, () -> {
            float yawOff = mc_yaw() - startYaw;
            float end = mc().player.getXRot(), climb = start - peak, off = end - start;
            boolean ok = mode.equals(FireMode.AUTO.name()) && climb > 1f && Math.abs(off) < 0.25f && Math.abs(yawOff) < 0.25f;
            ModernArmory.LOGGER.info("[autotest] recoil" + (pullDown ? " (pulled down 3 deg)" : "")
                    + ": M4A1 starts on {}, start pitch {}, climbed {} deg, ended {} deg off; drifted sideways up to {} deg, ended {} deg off -> {}",
                    mode, start, climb, off, drift, yawOff, ok ? "PASS" : "FAIL");
        });
    }

    private static float mc_yaw() {
        return mc().player.getYRot();
    }

    private AutoTestRecoil() {}
}
