package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;

import com.modernarmory.ModernArmory;


/**
 * Switching weapons drops the scope ("scopeswitch" section): scoped in with an M2010, switch to an AWM with the
 * number keys while still holding aim. The view must leave the scope at once and stay out until aim is pressed
 * again, then scope in with the AWM.
 */
final class AutoTestScopeSwitch {
    static final int LENGTH = 70;
    private static boolean scopedBefore, scopedAfterSwitch, scopedAfterRepress;
    private static float aimAfterSwitch;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("gamemode creative"); cmd("clear @s"); cmd("time set noon");
            cmd("give @s modernarmory:m2010"); cmd("give @s modernarmory:awm");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> { AutoTest.mc().player.getInventory().setSelectedSlot(0); look(180, 0); });
        at(t0 + 8, () -> AutoTest.mc().options.keyUse.setDown(true));
        at(t0 + 30, () -> scopedBefore = ClientState.scopedIn());
        at(t0 + 31, () -> AutoTest.mc().player.getInventory().setSelectedSlot(1)); // the "2" key, aim still held
        at(t0 + 40, () -> { scopedAfterSwitch = ClientState.scopedIn(); aimAfterSwitch = ClientState.aimProgress(); });
        at(t0 + 42, () -> AutoTest.mc().options.keyUse.setDown(false));
        at(t0 + 44, () -> AutoTest.mc().options.keyUse.setDown(true));
        at(t0 + 66, () -> {
            scopedAfterRepress = ClientState.scopedIn();
            AutoTest.mc().options.keyUse.setDown(false);
            boolean ok = scopedBefore && !scopedAfterSwitch && aimAfterSwitch == 0 && scopedAfterRepress;
            ModernArmory.LOGGER.info("[autotest] scope switch: scoped {} -> after switching (aim held) scoped {}, aim {} -> "
                    + "aim pressed again: scoped {} -> {}", scopedBefore, scopedAfterSwitch, aimAfterSwitch, scopedAfterRepress,
                    ok ? "PASS" : "FAIL");
        });
    }

    private AutoTestScopeSwitch() {}
}
