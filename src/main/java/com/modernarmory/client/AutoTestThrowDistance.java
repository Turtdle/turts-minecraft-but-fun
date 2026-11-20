package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;

import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeEntity;
import com.modernarmory.grenade.GrenadeType;
import com.modernarmory.grenade.Grenades;
import com.modernarmory.grenade.ThrowKind;

import net.minecraft.client.Minecraft;

/**
 * Overhand throw distance ("throwdist" section): the M67 (400 g) and the heaviest, the AN-M14 (900 g), are thrown
 * overhand 30 degrees up over flat ground; where each first lands is measured (then it is removed before it goes
 * off). Both must carry past 30 m and within a metre of each other. (The M84's 1.5 s fuze airbursts it on a
 * throw this long, as intended, so it never lands.)
 */
final class AutoTestThrowDistance {
    private static final GrenadeType[] TYPES = {Grenades.M67, Grenades.ANM14};
    private static final int EACH = 60;
    static final int LENGTH = 8 + TYPES.length * EACH + 4;
    private static final double[] carry = new double[TYPES.length];
    private static volatile GrenadeEntity flying;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("gamemode creative");
            cmd("fill -4 -60 -60 4 -52 2 minecraft:air");
            cmd("fill -4 -61 -60 4 -61 2 minecraft:stone");
            cmd("tp @s 0.5 -60 0.5 180 -30");
            look(180, -30);
        });
        int t = t0 + 8;
        for (int i = 0; i < TYPES.length; i++) {
            int k = i;
            at(t, () -> {
                carry[k] = -1;
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> flying = GrenadeEntity.throwFrom(server.getPlayerList().getPlayers().get(0), TYPES[k], ThrowKind.OVERHAND));
            });
            for (int d = 1; d < EACH - 2; d++) {
                at(t + d, () -> {
                    var server = Minecraft.getInstance().getSingleplayerServer();
                    server.execute(() -> {
                        GrenadeEntity g = flying;
                        if (g == null || carry[k] >= 0) return;
                        if (g.getY() <= -59.85) {
                            carry[k] = Math.hypot(g.getX() - 0.5, g.getZ() - 0.5);
                            g.discard();
                            flying = null;
                        }
                    });
                });
            }
            t += EACH;
        }
        at(t + 2, () -> {
            double lo = Double.MAX_VALUE, hi = 0;
            for (double c : carry) { lo = Math.min(lo, c); hi = Math.max(hi, c); }
            boolean ok = lo > 30 && hi - lo < 1.0;
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] throwdist: overhand 30 deg up, first landing M67 %.1f m, AN-M14 %.1f m -> %s",
                    carry[0], carry[1], ok ? "PASS" : "FAIL"));
        });
    }

    private AutoTestThrowDistance() {}
}
