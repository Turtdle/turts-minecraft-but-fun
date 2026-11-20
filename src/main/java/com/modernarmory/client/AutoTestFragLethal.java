package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;

import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.FragEffect;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frag grenade lethality ("fraglethal" section): an M67 burst (blast + fragments, as it goes off lying on flat
 * ground) in the middle of a ring of 8 unarmoured villagers (20 health), at 2.4 m (8 ft), 5 m and 12 m. At 2.4 m all
 * but at most one must die; at 12 m most must live; 5 m is logged.
 */
final class AutoTestFragLethal {
    private static final double[] RADII = {2.4, 5.0, 12.0};
    private static final int EACH = 40;
    static final int LENGTH = 6 + RADII.length * EACH + 4;
    private static final AABB AREA = new AABB(-20, -62, -20, 21, -52, 21);
    private static final int[] dead = new int[RADII.length];

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("gamemode creative");
            cmd("kill @e[type=!player]");
            cmd("fill -16 -60 -16 16 -55 16 minecraft:air");
            cmd("fill -16 -61 -16 16 -61 16 minecraft:stone");
            cmd("tp @s 0.5 -60 40.5 180 0");
        });
        int t = t0 + 6;
        for (int i = 0; i < RADII.length; i++) {
            int k = i;
            double r = RADII[i];
            at(t, () -> {
                cmd("kill @e[tag=fl]");
                for (int j = 0; j < 8; j++) {
                    double a = Math.toRadians(45 * j + 10);
                    cmd(String.format(Locale.ROOT, "summon villager %.2f -60 %.2f {NoAI:1b,Tags:[\"fl\"]}",
                            0.5 + r * Math.cos(a), 0.5 + r * Math.sin(a)));
                }
            });
            at(t + 4, () -> {
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> FragEffect.burst(server.overworld(), null, null, new Vec3(0.5, -59.7, 0.5),
                        FragEffect.BLAST_RADIUS, FragEffect.FRAGMENTS, FragEffect.GROUND_LOWEST_Z, false));
            });
            at(t + 30, () -> {
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    int alive = level.getEntitiesOfClass(LivingEntity.class, AREA, e -> e.isAlive() && e.getTags().contains("fl")).size();
                    dead[k] = 8 - alive;
                    ModernArmory.LOGGER.info(String.format(Locale.ROOT, "[autotest] fraglethal: %.1f m ring, %d of 8 villagers killed", r, dead[k]));
                });
            });
            t += EACH;
        }
        at(t + 2, () -> {
            boolean ok = dead[0] >= 7 && dead[2] <= 3;
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] fraglethal: killed %d/8 at 2.4 m, %d/8 at 5 m, %d/8 at 12 m -> %s", dead[0], dead[1], dead[2], ok ? "PASS" : "FAIL"));
            cmd("kill @e[type=!player]");
        });
    }

    private AutoTestFragLethal() {}
}
