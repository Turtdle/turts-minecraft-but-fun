package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.Config;
import com.modernarmory.ModernArmory;

import net.minecraft.client.Minecraft;

/**
 * Bullet holes ("holes" section): a test wall at x = 12 with one column per material (stone, oak planks,
 * iron, glass, dirt, wool) and one row per hole style (bottom 0 pixel, middle 1 crater, top 2 detailed). Every
 * block is shot twice with an M4, then the wall is photographed close up (autotest_54_bullet_holes).
 */
final class AutoTestHoles {
    private static final String[] BLOCKS = {"stone", "oak_planks", "iron_block", "glass", "dirt", "white_wool"};
    private static final int SHOTS = 2, SPACING = 4, ROWS = BulletHoles.STYLES;
    static final int LENGTH = 20 + ROWS * BLOCKS.length * SHOTS * SPACING + 40;

    static void script(int t0) {
        at(t0, () -> {
            cmd("clear @s"); cmd("gamemode creative"); cmd("time set noon"); cmd("give @s modernarmory:m4");
            cmd("fill 6 -60 -6 13 -55 6 minecraft:air");
            for (int row = 0; row < ROWS; row++)
                for (int col = 0; col < BLOCKS.length; col++)
                    cmd(String.format("setblock 12 %d %d minecraft:%s", -60 + row, col - 3, BLOCKS[col]));
            cmd("tp @s 5.5 -60 0.5 -90 0");
            Config.BULLETS_BREAK_GLASS.set(false); // keep the glass to show its holes
        });
        at(t0 + 4, () -> Minecraft.getInstance().player.getInventory().selected = 0);
        int t = t0 + 20;
        for (int row = 0; row < ROWS; row++)
            for (int col = 0; col < BLOCKS.length; col++)
                for (int k = 0; k < SHOTS; k++) {
                    int r = row, c = col, kk = k;
                    at(t, () -> {
                        BulletHoles.style = r;
                        // a little apart per shot so the two holes don't sit on top of each other
                        aimAt(12.0, -60 + r + 0.5 + (kk == 0 ? 0.18 : -0.18), c - 3 + 0.5 + (kk == 0 ? -0.18 : 0.18));
                        Minecraft.getInstance().options.keyAttack.setDown(true);
                    });
                    at(t + 1, () -> Minecraft.getInstance().options.keyAttack.setDown(false));
                    t += SPACING;
                }
        at(t + 4, () -> {
            ModernArmory.LOGGER.info("[autotest] holes: {} bullet holes on the wall", BulletHoles.count());
            cmd("tp @s 8.6 -60.2 0.0 -90 -8");
            Minecraft.getInstance().options.hideGui = true;
        });
        at(t + 14, () -> shot("54_bullet_holes"));
        at(t + 16, () -> {
            Minecraft.getInstance().options.hideGui = false;
            BulletHoles.style = BulletHoles.DEFAULT_STYLE;
            Config.BULLETS_BREAK_GLASS.set(true);
        });
    }

    private static void aimAt(double x, double y, double z) {
        var eye = Minecraft.getInstance().player.getEyePosition();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private AutoTestHoles() {}
}
