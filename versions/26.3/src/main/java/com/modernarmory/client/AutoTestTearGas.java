package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;
import static com.modernarmory.client.AutoTest.mc;

import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeEntity;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Tear gas ("teargas" section): throws an M7A3, films the cloud, then stands in it for 5 s and for 1 s.
 * Both must blind, and the longer stay must leave the longer blindness after stepping out.
 */
final class AutoTestTearGas {
    static final int LENGTH = 440;
    private static Vec3 cloud;
    private static int insideBlind, afterLong, afterShort;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:grenade]"); cmd("effect clear @s");
            cmd("clear @s"); cmd("gamemode creative"); cmd("time set noon"); cmd("give @s modernarmory:grenade_m7a3");
            cmd("fill -6 -60 -16 6 -55 3 minecraft:air"); cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + 4, () -> mc().player.getInventory().setSelectedSlot(0));
        at(t0 + 10, () -> mc().options.keyUse.setDown(true)); // a quick right-click: underhand, once the pin is out
        at(t0 + 11, () -> mc().options.keyUse.setDown(false));
        at(t0 + 80, () -> {
            var gs = mc().level.getEntitiesOfClass(GrenadeEntity.class, mc().player.getBoundingBox().inflate(40));
            cloud = gs.isEmpty() ? null : gs.get(0).position();
            ModernArmory.LOGGER.info("[autotest] teargas: grenade at {}", cloud);
            look(180, 10);
        });
        at(t0 + 100, () -> shot("55_tear_gas"));
        // 5 s in the gas
        at(t0 + 104, () -> into());
        at(t0 + 150, () -> { insideBlind = blind(); shot("56_tear_gas_inside"); });
        at(t0 + 204, () -> cmd("tp @s 0.5 -60 0.5 180 0"));
        at(t0 + 206, () -> afterLong = blind());
        // wait for that to wear off, then 1 s in the gas
        at(t0 + 400, () -> into());
        at(t0 + 420, () -> cmd("tp @s 0.5 -60 0.5 180 0"));
        at(t0 + 422, () -> afterShort = blind());
        at(t0 + 424, () -> {
            boolean ok = cloud != null && insideBlind > 0 && afterLong > 150 && afterShort > 40 && afterShort < 100;
            ModernArmory.LOGGER.info("[autotest] teargas: blind inside {} ticks left; after 5 s in the gas {} ticks, after 1 s {} ticks -> {}",
                    insideBlind, afterLong, afterShort, ok ? "PASS" : "FAIL");
            cmd("kill @e[type=modernarmory:grenade]"); cmd("effect clear @s");
        });
    }

    private static void into() {
        if (cloud != null) cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 180 0", cloud.x + 0.6, cloud.y, cloud.z));
    }

    /** Ticks of blindness left on the player (0 = none). */
    private static int blind() {
        var e = mc().player.getEffect(MobEffects.BLINDNESS);
        return e == null ? 0 : e.getDuration();
    }

    private AutoTestTearGas() {}
}
