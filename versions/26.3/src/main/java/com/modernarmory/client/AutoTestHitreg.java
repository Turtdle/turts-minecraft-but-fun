package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import com.modernarmory.ModernArmory;
import com.modernarmory.ballistics.LagCompensation;
import com.modernarmory.entity.BulletEntity;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Hit registration on a moving target ("hitreg" section): a husk strafes side to side 14 m away at 4 m/s,
 * the player tracks it as the client draws it and fires single M4 shots, and the hits are counted with
 * lag compensation off and with each candidate client view delay (LagCompensation.VIEW_DELAY).
 * Log lines "[autotest] hitreg ...": the best view delay is the one to keep.
 */
final class AutoTestHitreg {
    /** Lag compensation off (-1), then these view delays. */
    private static final int[] MODES = {-1, 1, 2, 3, 4, 5};
    private static final int SHOTS = 16, SPACING = 6, BLOCK = 20 + SHOTS * SPACING + 10;
    static final int LENGTH = 20 + MODES.length * BLOCK + 10;

    private static volatile boolean moving;
    private static boolean moverRegistered;
    private static int hitsBefore;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=minecraft:husk]"); cmd("clear @s"); cmd("give @s modernarmory:m4"); cmd("gamemode creative");
            cmd("time set noon"); cmd("difficulty easy"); cmd("tp @s 0.5 -60 0.5 0 0");
            cmd("fill -9 -60 3 9 -50 22 minecraft:air");
            cmd("summon minecraft:husk 0.5 -60 14.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"hitreg\"],"
                    + "attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}");
            registerMover();
            moving = true;
        });
        at(t0 + 4, () -> mc().player.getInventory().setSelectedSlot(0));
        for (int t = t0 + 6; t < t0 + LENGTH - 6; t++) at(t, AutoTestHitreg::track);
        for (int m = 0; m < MODES.length; m++) {
            int base = t0 + 20 + m * BLOCK, mode = MODES[m];
            at(base, () -> {
                LagCompensation.enabled = mode >= 0;
                if (mode >= 0) LagCompensation.VIEW_DELAY = mode;
                hitsBefore = BulletEntity.entityHits;
            });
            for (int i = 0; i < SHOTS; i++) {
                at(base + 10 + i * SPACING, () -> mc().options.keyAttack.setDown(true));
                at(base + 11 + i * SPACING, () -> mc().options.keyAttack.setDown(false));
            }
            at(base + BLOCK - 2, () -> ModernArmory.LOGGER.info("[autotest] hitreg {}: {}/{} hits; hit markers sent so far: {} body, {} head",
                    mode < 0 ? "no lag compensation" : "view delay " + mode, BulletEntity.entityHits - hitsBefore, SHOTS,
                    com.modernarmory.server.HitMarkers.sentBody, com.modernarmory.server.HitMarkers.sentHead));
        }
        at(t0 + LENGTH - 4, () -> {
            moving = false;
            LagCompensation.enabled = true;
            LagCompensation.VIEW_DELAY = LagCompensation.DEFAULT_VIEW_DELAY;
            cmd("kill @e[type=minecraft:husk]");
        });
    }

    /** Aim at the husk's chest where the client draws it, a tick ahead (the aim reaches the server next tick). */
    private static void track() {
        Husk h = null;
        for (var e : mc().level.entitiesForRendering()) if (e instanceof Husk husk) h = husk;
        if (h == null) return;
        double x = h.getX() + (h.getX() - h.xo), y = h.getY() + 1.1, z = h.getZ() + (h.getZ() - h.zo);
        var eye = mc().player.getEyePosition();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    /** Server side: the tagged husk strafes along X, 0.2 blocks a tick, turning every 60 ticks. */
    private static void registerMover() {
        if (moverRegistered) return;
        moverRegistered = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!moving) return;
            int phase = e.getServer().getTickCount() % 120;
            double x = phase < 60 ? -5.5 + phase * 0.2 : 6.5 - (phase - 60) * 0.2;
            var level = e.getServer().overworld();
            for (Husk h : level.getEntities(EntityTypes.HUSK, new AABB(-20, -64, 0, 20, -40, 30), h -> h.entityTags().contains("hitreg")))
                h.setPos(x, -60, 14.5);
        });
    }

    private AutoTestHitreg() {}
}
