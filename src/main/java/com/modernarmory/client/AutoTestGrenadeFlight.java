package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cameraFromStand;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeEntity;

import net.minecraft.client.Minecraft;

/**
 * A thrown grenade filmed from the side ("nadeflight" section): an M67 thrown overhand north, seen through a
 * side camera (an invisible armour stand), one screenshot a tick through the flight (autotest_51_nade_flight_NN),
 * its position and tumble logged each tick. Then, once it has settled (before the fuze runs out), straight down
 * onto it from above (autotest_51b_nade_resting_top): it must be drawn at the centre, where the entity is.
 */
final class AutoTestGrenadeFlight {
    static final int LENGTH = 76;
    private static final int FRAMES = 18;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:grenade]"); cmd("kill @e[type=minecraft:armor_stand,tag=nadecam]");
            cmd("clear @s"); cmd("give @s modernarmory:grenade_m67 4"); cmd("gamemode creative"); cmd("time set noon");
            cmd("tp @s 0.5 -60 0.5 180 0"); cmd("fill -4 -60 -14 8 -54 2 minecraft:air");
            cmd("summon minecraft:armor_stand 6.5 -60.2 -4.5 {Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"nadecam\"],Rotation:[90f,-4f]}");
        });
        at(t0 + 4, () -> { slot0(); look(180, -8); });
        at(t0 + 6, () -> Minecraft.getInstance().options.keyAttack.setDown(true));
        at(t0 + 18, () -> Minecraft.getInstance().options.keyAttack.setDown(false));
        at(t0 + 19, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.fov().set(60);
            mc.options.hideGui = true;
            cameraFromStand(e -> e.getX() > 5); // (entity tags are not synced to the client)
        });
        for (int i = 0; i < FRAMES; i++) {
            int k = i;
            at(t0 + 22 + i, () -> {
                log(k);
                shot(String.format("51_nade_flight_%02d", k));
            });
        }
        at(t0 + 22 + FRAMES + 2, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setCameraEntity(mc.player);
            mc.options.fov().set(70);
            mc.options.hideGui = false;
            cmd("kill @e[type=minecraft:armor_stand,tag=nadecam]");
        });
        at(t0 + 62, () -> {
            for (var e : Minecraft.getInstance().level.entitiesForRendering())
                if (e instanceof GrenadeEntity g) {
                    log(99);
                    cmd(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f 0 90", g.getX(), g.getY(), g.getZ()));
                }
            Minecraft.getInstance().options.hideGui = true;
        });
        at(t0 + 68, () -> shot("51b_nade_resting_top"));
        at(t0 + 70, () -> { Minecraft.getInstance().options.hideGui = false; cmd("kill @e[type=modernarmory:grenade]");
            cmd("tp @s 0.5 -60 0.5 180 0"); });
    }

    private static void slot0() {
        Minecraft.getInstance().player.getInventory().selected = 0;
    }

    private static void log(int frame) {
        for (var e : Minecraft.getInstance().level.entitiesForRendering())
            if (e instanceof GrenadeEntity g) {
                var t = g.tumble();
                ModernArmory.LOGGER.info("[autotest] nade frame {}: pos {} {} {} yaw {} tilt {} roll {} lean {}", frame,
                        String.format("%.2f", g.getX()), String.format("%.2f", g.getY()), String.format("%.2f", g.getZ()),
                        Math.round(t.yaw), Math.round(t.tilt), Math.round(t.roll), Math.round(t.lean));
            }
    }

    private AutoTestGrenadeFlight() {}
}
