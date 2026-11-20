package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;
import static com.modernarmory.client.AutoTest.shot;

import net.minecraft.client.CameraType;

/**
 * Third-person gun pose while strafing ("tpwalk" section): seen from the front, walking right then left with
 * an M4. The body should face where the head looks, the gun across the chest between both hands.
 * Then aiming, still from the front: the hands should stay together on the gun.
 * Screenshots autotest_50_tp_walk_right / 50b_tp_walk_left / 50c_tp_aim_front.
 */
final class AutoTestPose {
    static final int LENGTH = 52;

    static void script(int t0) {
        at(t0, () -> { cmd("clear @s"); cmd("give @s modernarmory:m4"); cmd("gamemode creative"); cmd("time set noon");
            cmd("tp @s 0.5 -60 0.5 180 0"); });
        at(t0 + 4, () -> { SLOT0.run(); mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT); look(180, 0); });
        at(t0 + 6, () -> mc().options.keyRight.setDown(true));
        at(t0 + 18, () -> shot("50_tp_walk_right"));
        at(t0 + 20, () -> { mc().options.keyRight.setDown(false); mc().options.keyLeft.setDown(true); });
        at(t0 + 32, () -> shot("50b_tp_walk_left"));
        at(t0 + 34, () -> { mc().options.keyLeft.setDown(false); mc().options.keyUse.setDown(true); }); // aiming
        at(t0 + 46, () -> shot("50c_tp_aim_front"));
        at(t0 + 48, () -> { mc().options.keyUse.setDown(false); mc().options.setCameraType(CameraType.FIRST_PERSON); });
    }

    private static final Runnable SLOT0 = () -> mc().player.getInventory().setSelectedSlot(0);

    private AutoTestPose() {}
}
