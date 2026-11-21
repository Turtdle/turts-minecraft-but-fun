package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.network.FlashCoverPayload;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * A flashed player covers their eyes ("flashcover" section): seen from the front in third person with an M4, the
 * pose is photographed rising and fully up (FlashCover, started by hand so no white-out hides it); then a real M84
 * dropped at your feet must make the server send the cover to this client too.
 */
final class AutoTestFlashCover {
    static final int LENGTH = 150;
    private static int startedBefore;
    private static float peak;

    static void script(int t0) {
        Minecraft mc = Minecraft.getInstance();
        at(t0 + 1, () -> {
            cmd("gamemode creative"); cmd("clear @s"); cmd("time set noon"); cmd("give @s modernarmory:m4");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> { mc.player.getInventory().selected = 0; look(180, 0); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); });
        at(t0 + 10, () -> shot("flashcover_0_normal"));
        at(t0 + 12, () -> FlashCover.onCover(new FlashCoverPayload(mc.player.getId(), 4f)));
        at(t0 + 13, () -> shot("flashcover_1_raising"));
        at(t0 + 20, () -> shot("flashcover_2_covering"));
        at(t0 + 22, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        at(t0 + 26, () -> shot("flashcover_3_from_behind"));
        // a real flashbang at your feet, looking at it
        at(t0 + 70, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); cmd("give @s modernarmory:grenade_m84"); });
        at(t0 + 74, () -> { mc.player.getInventory().selected = 1; look(180, 60); startedBefore = FlashCover.started; peak = 0; });
        at(t0 + 76, () -> mc.options.keyUse.setDown(true));
        at(t0 + 86, () -> mc.options.keyUse.setDown(false));
        for (int t = t0 + 86; t < t0 + 146; t++) at(t, () -> peak = Math.max(peak, FlashCover.progress(mc.player.getId())));
        at(t0 + 146, () -> {
            int got = FlashCover.started - startedBefore;
            boolean ok = got >= 1 && peak > 0.9f;
            ModernArmory.LOGGER.info("[autotest] flash cover: {} cover message(s) from a real M84, hand up to {} -> {}", got,
                    String.format("%.2f", peak), ok ? "PASS" : "FAIL");
            look(180, 0);
        });
    }

    private AutoTestFlashCover() {}
}
