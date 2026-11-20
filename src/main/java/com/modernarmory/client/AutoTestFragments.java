package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.grenade.FragEffect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Fragment ricochet ("fragments" section): an M67-sized fragment burst in the middle of a closed stone room.
 * Fragments must glance off the walls, and none may bounce more than once.
 */
final class AutoTestFragments {
    static final int LENGTH = 70;

    static void script(int t0) {
        at(t0, () -> {
            cmd("tp @s 0.5 -60 0.5 0 0");
            cmd("fill 40 -61 -5 50 -55 5 minecraft:stone hollow");
        });
        at(t0 + 20, () -> {
            BulletEntity.serverRicochets = 0;
            BulletEntity.serverMostBounces = 0;
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> FragEffect.burst(server.overworld(), null, null, new Vec3(45.5, -58.0, 0.5), 0.5f, 300, -1, false));
        });
        at(t0 + 60, () -> {
            int n = BulletEntity.serverRicochets, most = BulletEntity.serverMostBounces;
            boolean ok = n >= 5 && most == 1;
            ModernArmory.LOGGER.info("[autotest] fragments: {} of 300 fragments ricocheted, most bounces by one fragment {} -> {}",
                    n, most, ok ? "PASS" : "FAIL");
            cmd("fill 40 -61 -5 50 -55 5 minecraft:air");
        });
    }

    private AutoTestFragments() {}
}
