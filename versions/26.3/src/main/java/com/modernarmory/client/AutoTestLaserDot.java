package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cameraFromStand;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;
import static com.modernarmory.client.AutoTest.shot;

/**
 * The AN/PEQ-15 laser's dot ("laserdot" section): an M4 with the laser fitted, aimed at the range wall with the
 * HUD hidden (the crosshair would cover the dot), then the same dot seen from a side camera. The dot must sit
 * at the screen centre in autotest_53_laser_dot_fp and on the wall in autotest_53b_laser_dot_side.
 */
final class AutoTestLaserDot {
    static final int LENGTH = 40;

    static void script(int t0) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("time set noon"); cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("give @s modernarmory:m4[modernarmory:attachments={SIDE:\"laser\"}]");
            cmd("kill @e[type=minecraft:armor_stand,tag=dotcam]");
            cmd("summon minecraft:armor_stand 4.5 -60 -3.5 {Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"dotcam\"],Rotation:[150f,0f]}");
        });
        at(t0 + 4, () -> { mc().player.getInventory().setSelectedSlot(0); look(150, 25); }); // turned and looking down: a camera rotation that is not the identity
        at(t0 + 8, () -> AutoTest.setHudHidden(true));
        at(t0 + 14, () -> shot("53_laser_dot_fp"));
        at(t0 + 16, () -> cameraFromStand(e -> e.getX() > 3)); // (entity tags are not synced to the client)
        at(t0 + 26, () -> shot("53b_laser_dot_side"));
        at(t0 + 28, () -> {
            mc().setCameraEntity(mc().player);
            AutoTest.setHudHidden(false);
            cmd("kill @e[type=minecraft:armor_stand,tag=dotcam]");
        });
    }

    private AutoTestLaserDot() {}
}
