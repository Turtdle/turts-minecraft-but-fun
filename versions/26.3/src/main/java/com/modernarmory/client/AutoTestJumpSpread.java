package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;

import com.modernarmory.ModernArmory;

import net.minecraft.world.phys.Vec3;

/**
 * Jumping ruins your aim ("jumpspread" section): {@value #SHOTS} aimed M4 shots at a stone wall {@value #RANGE} m
 * away standing still, then {@value #SHOTS} more each fired near the top of a jump. The holes' sideways RMS angle from
 * the aim line (sideways only: in the air the eye, and so the aim line, is higher) must be several times wider in the
 * air (gun.ShooterSpread.AIRBORNE).
 */
final class AutoTestJumpSpread {
    private static final int SHOTS = 16, SPACING = 24, RANGE = 10;
    private static final int PART = 30 + SHOTS * SPACING + 10;
    static final int LENGTH = 2 * PART + 6;
    private static double rmsGround, rmsAir;
    private static int airborneShots;

    static void script(int t0) {
        group(t0, false);
        group(t0 + PART, true);
        at(t0 + 2 * PART + 2, () -> {
            boolean ok = rmsAir > 1.8 && rmsAir > rmsGround * 4 && airborneShots == SHOTS;
            ModernArmory.LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[autotest] jumpspread: M4 aimed, sideways RMS %.2f deg standing, %.2f deg jumping (%d of %d shots in the air) -> %s",
                    rmsGround, rmsAir, airborneShots, SHOTS, ok ? "PASS" : "FAIL"));
            AutoTest.mc().options.keyUse.setDown(false);
        });
    }

    private static void group(int t0, boolean jump) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("give @s modernarmory:m4"); cmd("gunmastery set @s m4 0");
            cmd("fill -6 -61 -12 6 -50 3 minecraft:air");
            cmd("fill -6 -61 -" + (RANGE + 1) + " 6 -50 -" + (RANGE + 1) + " minecraft:stone");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> { AutoTest.mc().player.getInventory().setSelectedSlot(0); AutoTest.mc().options.keyUse.setDown(true); }); // aiming down sights
        at(t0 + 20, () -> { BulletHoles.clear(); airborneShots = 0; });
        int t = t0 + 30;
        for (int i = 0; i < SHOTS; i++, t += SPACING) {
            if (jump) {
                at(t - 5, () -> AutoTest.mc().options.keyJump.setDown(true));
                at(t - 4, () -> AutoTest.mc().options.keyJump.setDown(false));
            }
            at(t - 1, () -> look(180, 0)); // back on the aim point
            at(t, () -> {
                if (jump && !AutoTest.mc().player.onGround()) airborneShots++;
                AutoTest.mc().options.keyAttack.setDown(true);
            });
            at(t + 1, () -> AutoTest.mc().options.keyAttack.setDown(false));
        }
        at(t + 4, () -> {
            Vec3 eye = AutoTest.mc().player.getEyePosition();
            double sum = 0;
            int n = 0;
            for (Vec3 h : BulletHoles.positions()) {
                double dist = eye.z - h.z, dx = h.x - eye.x;
                if (dist < 1) continue;
                double deg = Math.toDegrees(Math.atan(Math.abs(dx) / dist));
                sum += deg * deg;
                n++;
            }
            double rms = n == 0 ? 0 : Math.sqrt(sum / n);
            if (jump) rmsAir = rms; else rmsGround = rms;
            ModernArmory.LOGGER.info("[autotest] jumpspread: {} holes {}", n, jump ? "jumping" : "standing");
        });
    }

    private AutoTestJumpSpread() {}
}
