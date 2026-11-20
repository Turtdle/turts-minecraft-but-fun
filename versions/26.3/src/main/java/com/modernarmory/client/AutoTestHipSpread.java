package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import com.modernarmory.ModernArmory;

import net.minecraft.world.phys.Vec3;

/**
 * Hip-fire mastery ("hipspread" section): {@value #SHOTS} paced hip-fire shots from a Glock 17 at a stone wall
 * {@value #RANGE} m away, at mastery 0 and at mastery V. The RMS angle of the holes from the aim point must
 * shrink to well under two thirds at V (the perk takes 60% off).
 */
final class AutoTestHipSpread {
    private static final int SHOTS = 24, SPACING = 16, RANGE = 10;
    private static final int PART = 30 + SHOTS * SPACING + 10;
    static final int LENGTH = 2 * PART + 12;
    private static double rms0, rms5;

    static void script(int t0) {
        group(t0, 0);
        group(t0 + PART, 4000);
        at(t0 + 2 * PART + 2, () -> {
            boolean ok = rms0 > 0.3 && rms5 < rms0 * 0.65;
            ModernArmory.LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[autotest] hipspread: Glock 17 from the hip, RMS %.2f deg at mastery 0, %.2f deg at mastery V (%.0f%%) -> %s",
                    rms0, rms5, 100 * rms5 / rms0, ok ? "PASS" : "FAIL"));
            cmd("gunmastery set @s glock17 0");
        });
        // "/gunmastery set @s all 4000" maxes every gun
        at(t0 + 2 * PART + 4, () -> cmd("gunmastery set @s all 4000"));
        at(t0 + 2 * PART + 9, () -> {
            var d = ClientMastery.data();
            int maxed = 0;
            for (var g : com.modernarmory.gun.Guns.all()) if (com.modernarmory.mastery.Mastery.level(d.xp(g.id())) == 5) maxed++;
            ModernArmory.LOGGER.info("[autotest] mastery all: {} of {} guns at mastery V -> {}", maxed, com.modernarmory.gun.Guns.all().size(),
                    maxed == com.modernarmory.gun.Guns.all().size() ? "PASS" : "FAIL");
            cmd("gunmastery set @s all 0");
        });
    }

    private static void group(int t0, int xp) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:glock17"); cmd("gunmastery set @s glock17 " + xp);
            cmd("fill -6 -61 -12 6 -52 3 minecraft:air");
            cmd("fill -6 -61 -" + (RANGE + 1) + " 6 -52 -" + (RANGE + 1) + " minecraft:stone");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> mc().player.getInventory().setSelectedSlot(0));
        at(t0 + 20, () -> BulletHoles.clear());
        int t = t0 + 30;
        for (int i = 0; i < SHOTS; i++, t += SPACING) {
            at(t - 1, () -> look(180, 0)); // back on the aim point: every shot starts settled
            at(t, () -> mc().options.keyAttack.setDown(true));
            at(t + 2, () -> mc().options.keyAttack.setDown(false));
        }
        at(t + 4, () -> {
            Vec3 eye = mc().player.getEyePosition();
            double sum = 0;
            int n = 0;
            for (Vec3 h : BulletHoles.positions()) {
                double dist = eye.z - h.z, dx = h.x - eye.x, dy = h.y - eye.y;
                if (dist < 1) continue;
                double deg = Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dy * dy) / dist));
                sum += deg * deg;
                n++;
            }
            double rms = n == 0 ? 0 : Math.sqrt(sum / n);
            if (xp == 0) rms0 = rms; else rms5 = rms;
            ModernArmory.LOGGER.info("[autotest] hipspread: {} holes at {} xp", n, xp);
        });
    }

    private AutoTestHipSpread() {}
}
