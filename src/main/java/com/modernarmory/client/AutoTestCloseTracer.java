package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.network.GunActionPayload;
import com.modernarmory.server.GunHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A tracer fired at a wall 5.5 m away ("closetracer" section). The bullet stops inside its first tick of flight;
 * it must still be drawn: staying a tick streaking to the impact, then collapsing into it (BulletEntity
 * stoppedTicks), as our own predicted copy only (the server's isn't sent back).
 */
final class AutoTestCloseTracer {
    static final int LENGTH = 90;
    private static int maxStopped, serverCopies, sentBefore, predictedSeen;
    private static double stopDistance = -1, drawn;

    static void script(int t0) {
        Minecraft mc = Minecraft.getInstance();
        at(t0 + 1, () -> {
            cmd("gamemode creative"); cmd("clear @s"); cmd("time set midnight");
            cmd("give @s modernarmory:m4a1"); cmd("give @s modernarmory:ammo_556_tracer 64");
            cmd("tp @s 0.5 -60 0.5 0 0");
            cmd("fill -3 -61 6 3 -56 6 minecraft:stone"); // a wall 5.5 m in front
        });
        at(t0 + 4, () -> { mc.player.getInventory().selected = 0; look(0, 0); });
        at(t0 + 6, () -> PacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD))); // loads the tracers
        at(t0 + 74, () -> { sentBefore = ShotPredictor.sent; predictedSeen = 0; maxStopped = 0; serverCopies = 0; stopDistance = -1; drawn = 0; look(0, 0); });
        at(t0 + 76, () -> mc.options.keyAttack.setDown(true)); // one tick: one round
        at(t0 + 77, () -> mc.options.keyAttack.setDown(false));
        for (int t = t0 + 76; t < t0 + 82; t++) at(t, () -> {
            for (BulletEntity b : mc.level.getEntitiesOfClass(BulletEntity.class, mc.player.getBoundingBox().inflate(32))) {
                if (!ShotPredictor.isPredicted(b)) {
                    if (b.getOwner() == mc.player) serverCopies++;
                    continue;
                }
                predictedSeen++;
                if (b.stoppedTicks() > 0) {
                    maxStopped = Math.max(maxStopped, b.stoppedTicks());
                    stopDistance = b.distance();
                    // what BulletRenderer draws mid-frame
                    drawn = Math.max(drawn, Mth.clamp(b.distance(0.5f) - 0.8, 0, 16) * b.trailShare(0.5f));
                }
            }
        });
        at(t0 + 77, () -> shot("close_tracer"));
        at(t0 + 84, () -> {
            boolean ok = maxStopped >= 1 && stopDistance > 4.5 && stopDistance < 7 && drawn > 0.5 && serverCopies == 0;
            ModernArmory.LOGGER.info("[autotest] close tracer: stopped at {} m, kept {} tick(s) after the hit, streak {} m mid-frame, "
                            + "{} server copies; {} shot(s), {} predicted bullet sightings -> {}", String.format("%.2f", stopDistance), maxStopped,
                    String.format("%.2f", drawn), serverCopies, ShotPredictor.sent - sentBefore, predictedSeen, ok ? "PASS" : "FAIL");
            cmd("fill -3 -61 6 3 -56 6 minecraft:air"); cmd("time set noon");
        });
    }

    private AutoTestCloseTracer() {}
}
