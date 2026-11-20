package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Thermite fire ("thermite" section): an AN-M14 thrown next to some oak planks must burst at once into a
 * patch of thermite fire (photographed), set nothing else alight, and be completely burnt out ~25 s later.
 */
final class AutoTestThermite {
    static final int LENGTH = 560;
    private static int burst, peak, vanilla;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:grenade]"); cmd("clear @s"); cmd("gamemode creative"); cmd("time set noon");
            cmd("give @s modernarmory:grenade_anm14");
            cmd("fill -8 -60 -16 8 -55 3 minecraft:air");
            cmd("fill -4 -60 -11 4 -60 -11 minecraft:oak_planks"); // a wooden kerb just past where it lands
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> Minecraft.getInstance().player.getInventory().selected = 0);
        at(t0 + 10, () -> Minecraft.getInstance().options.keyUse.setDown(true)); // a quick right-click: underhand
        at(t0 + 11, () -> Minecraft.getInstance().options.keyUse.setDown(false));
        for (int k = 20; k <= 80; k += 2) {
            int kk = k;
            at(t0 + k, () -> {
                int n = count(ModBlocks.THERMITE_FIRE.get());
                if (n > 0 && burst == 0) burst = n; // the first tick any fire shows: the burst
                peak = Math.max(peak, n);
            });
        }
        at(t0 + 84, () -> { look(180, 25); Minecraft.getInstance().options.hideGui = true; });
        at(t0 + 90, () -> shot("57_thermite_fire"));
        at(t0 + 92, () -> { cmd("tp @s 4.5 -59 -3.5 135 30"); });
        at(t0 + 98, () -> { shot("57b_thermite_fire_close"); Minecraft.getInstance().options.hideGui = false; });
        at(t0 + 100, () -> cmd("tp @s 0.5 -60 0.5 180 0"));
        at(t0 + 200, () -> vanilla = count(Blocks.FIRE));
        at(t0 + 540, () -> {
            int left = count(ModBlocks.THERMITE_FIRE.get()), planks = count(Blocks.OAK_PLANKS);
            vanilla = Math.max(vanilla, count(Blocks.FIRE));
            boolean ok = burst >= 15 && burst >= peak * 0.9 && vanilla == 0 && planks == 9 && left == 0;
            ModernArmory.LOGGER.info("[autotest] thermite: burst {} fire blocks at once (most {}), vanilla fire {}, planks left {}/9, "
                    + "thermite fire left after 25 s {} -> {}", burst, peak, vanilla, planks, left, ok ? "PASS" : "FAIL");
        });
    }

    /** How many of {@code block} are in the test area. */
    private static int count(Block block) {
        int n = 0;
        var level = Minecraft.getInstance().level;
        for (BlockPos p : BlockPos.betweenClosed(-8, -61, -16, 8, -56, 3))
            if (level.getBlockState(p).is(block)) n++;
        return n;
    }

    private AutoTestThermite() {}
}
