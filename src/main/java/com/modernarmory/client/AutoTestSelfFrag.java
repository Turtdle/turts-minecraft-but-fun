package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;

import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.FragEffect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Your own frag hurts you ("selffrag" section): an M67 goes off 4 m from you (no armour, survival, Easy like the
 * server; max health raised to 100 so the damage can be measured), once thrown by nobody and once thrown by you.
 * The thrower must be hit by fragments too, not just the blast (they used to pass through their own thrower).
 * At 4 m a body takes ~5 fragments on average, so none at all would be a 1-in-100 fluke.
 */
final class AutoTestSelfFrag {
    static final int LENGTH = 150;
    private static float before, damageOther, damageOwn;
    private static volatile int hitsBefore, hitsOther, hitsOwn;
    private static volatile float serverBefore;

    static void script(int t0) {
        // after the 3 s of spawn protection a player has on joining (a scenario of just this section starts sooner)
        burst(t0 + 70, false);
        burst(t0 + 106, true);
        at(t0 + 144, () -> {
            boolean ok = hitsOwn > 0;
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] selffrag: M67 at 4 m, nobody's: %d fragments, %.1f damage; yours: %d fragments, %.1f damage -> %s",
                    hitsOther, damageOther, hitsOwn, damageOwn, ok ? "PASS" : "FAIL"));
            cmd("attribute @s minecraft:generic.max_health base set 20");
            cmd("gamemode creative"); cmd("difficulty peaceful"); cmd("effect give @s minecraft:instant_health 1 10 true");
        });
    }

    private static void burst(int t, boolean own) {
        Minecraft mc = Minecraft.getInstance();
        at(t + 2, () -> { // after the section's own setup, which may build things here
            cmd("kill @e[type=!player]"); cmd("clear @s"); cmd("effect clear @s");
            cmd("fill -8 -60 -8 8 -55 8 minecraft:air"); cmd("fill -8 -61 -8 8 -61 8 minecraft:stone");
            cmd("gamemode survival"); cmd("difficulty easy");
            cmd("attribute @s minecraft:generic.max_health base set 100");
            cmd("tp @s 0.5 -60 4.5 180 0");
        });
        at(t + 6, () -> cmd("effect give @s minecraft:instant_health 1 10 true"));
        at(t + 10, () -> {
            before = mc.player.getHealth();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var p0 = server.getPlayerList().getPlayers().get(0);
                hitsBefore = com.modernarmory.entity.BulletEntity.entityHits;
                serverBefore = p0.getHealth();
                var sp = own ? p0 : null;
                FragEffect.burst(server.overworld(), null, sp, new Vec3(0.5, -59.7, 0.5),
                        FragEffect.BLAST_RADIUS, FragEffect.FRAGMENTS, FragEffect.GROUND_LOWEST_Z, false);
            });
        });
        at(t + 32, () -> {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                int hits = com.modernarmory.entity.BulletEntity.entityHits - hitsBefore; // only you are in range
                if (own) hitsOwn = hits; else hitsOther = hits;
            });
            float d = before - mc.player.getHealth();
            if (own) damageOwn = d; else damageOther = d;
        });
    }

    private AutoTestSelfFrag() {}
}
