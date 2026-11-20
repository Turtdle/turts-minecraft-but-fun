package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.laser.LaserTurretBlock;
import com.modernarmory.laser.TurretSeat;
import com.modernarmory.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Autotest section "laser" ({@code -Pscenario=laser}): the HELWS laser turret. Placement refused with a
 * block in the way (hologram red), placed once it is cleared (hologram green, 51 cells); seen from outside;
 * boarded (sensor camera, drone boxes); a hovering scout locked with a click while the crosshair is off it (the head
 * tracks it, the view stays put), the lock broken by looking away until it leaves the screen, locked again and
 * burned down; an armed FPV burned down (its charge goes off in the air); leaving the seat; a cell removed collapses the turret and
 * gives the item back. Screenshots autotest_laser_*.png; log lines "[autotest] laser ...".
 */
final class AutoTestLaser {
    static final int LENGTH = 320;
    /** The turret's core when placed from the start spot (seat row 2 blocks ahead, the turret north of it). */
    private static final BlockPos CORE = new BlockPos(0, -60, -4);
    private static final Vec3 SCOUT = new Vec3(0.5, -46, -38), FPV = new Vec3(14.5, -50, -42);
    private static int engagedAt = -1;

    static void script(int t0) {
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:drone]"); cmd("kill @e[type=modernarmory:turret_seat]");
            cmd("fill -16 -60 -48 16 -40 6 minecraft:air"); cmd("fill -16 -61 -48 16 -61 6 minecraft:grass_block");
            cmd("gamemode creative"); cmd("time set noon"); cmd("weather clear"); cmd("difficulty peaceful");
            cmd("clear @s"); cmd("give @s modernarmory:laser_turret"); cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("setblock 0 -60 -4 minecraft:stone"); // in the way
        });
        at(t0 + 4, () -> { mc().player.getInventory().selected = 0; look(180, 45); });
        at(t0 + 8, () -> { logPreview("blocked"); shot("laser_01_hologram_blocked"); });
        at(t0 + 10, () -> mc().options.keyUse.setDown(true));
        at(t0 + 11, () -> mc().options.keyUse.setDown(false));
        at(t0 + 16, () -> logCells("after the refused placement"));
        at(t0 + 18, () -> cmd("setblock 0 -60 -4 minecraft:air"));
        at(t0 + 22, () -> { logPreview("cleared"); shot("laser_02_hologram_fits"); });
        at(t0 + 24, () -> mc().options.keyUse.setDown(true));
        at(t0 + 25, () -> mc().options.keyUse.setDown(false));
        at(t0 + 30, () -> logCells("placed"));
        at(t0 + 32, () -> { mc().player.getInventory().selected = 1; cmd("tp @s 6.5 -59.5 -3 90 10"); }); // empty hand: no hologram
        at(t0 + 38, () -> shot("laser_03_side"));
        at(t0 + 40, () -> { cmd("tp @s -4.5 -57 -9 -40 22"); });
        at(t0 + 46, () -> shot("laser_04_front_left"));
        // ---- targets: a hovering scout ahead, an armed FPV off to the right
        at(t0 + 48, () -> {
            cmd("tp @s 0.5 -60 0.5 180 0");
            cmd(String.format(java.util.Locale.ROOT, "summon modernarmory:drone %.1f %.1f %.1f {drone:\"drone_scout\",battery:1.0f,throttle:0.55f}", SCOUT.x, SCOUT.y, SCOUT.z));
            cmd(String.format(java.util.Locale.ROOT, "summon modernarmory:drone %.1f %.1f %.1f {drone:\"drone_fpv\",battery:1.0f,throttle:0.55f,arm:0}", FPV.x, FPV.y, FPV.z));
        });
        at(t0 + 50, () -> { mc().player.getInventory().selected = 1; look(180, 10); });
        at(t0 + 54, () -> mc().options.keyUse.setDown(true)); // right-click the turret: take the seat
        at(t0 + 55, () -> mc().options.keyUse.setDown(false));
        at(t0 + 62, () -> { logSeat("boarded"); shot("laser_05_turret_view"); });
        at(t0 + 64, () -> aimAt(SCOUT));
        at(t0 + 76, () -> { aimAt(SCOUT.add(3, 1.5, 0)); logSeat("aimed near the scout"); shot("laser_06_scout_boxed"); });
        at(t0 + 78, () -> clickFire(t0 + 78)); // lock the nearest drone and fire: the head tracks it, the view stays
        at(t0 + 90, () -> { logSeat("engaging the scout"); shot("laser_07_lasing"); });
        at(t0 + 94, () -> { // the beam seen from off to the side, while it burns
            var cam = new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, mc().level);
            cam.moveTo(13.5, -53, -16, 111, 2);
            LaserOperator.testView = cam;
        });
        at(t0 + 97, () -> shot("laser_07b_beam_outside"));
        at(t0 + 98, () -> LaserOperator.testView = null);
        // looking away until the scout leaves the screen: the lock breaks; looking back and clicking again finishes it
        at(t0 + 100, () -> look(90, 0));
        at(t0 + 108, () -> { logSeat("looking away"); logScoutHeat("looking away"); });
        at(t0 + 110, () -> aimAt(SCOUT));
        at(t0 + 114, () -> net.minecraft.client.KeyMapping.click(mc().options.keyAttack.getKey()));
        for (int k = 80; k < 170; k++) {
            int kk = k;
            at(t0 + k, () -> watchKill("scout", t0, kk));
        }
        at(t0 + 172, () -> { logSeat("after the scout"); shot("laser_08_scout_down"); });
        at(t0 + 174, () -> aimAt(FPV));
        at(t0 + 186, () -> { aimAt(FPV); shot("laser_09_fpv_boxed"); });
        at(t0 + 188, () -> clickFire(t0 + 188));
        for (int k = 190; k < 240; k++) {
            int kk = k;
            at(t0 + k, () -> watchKill("fpv", t0, kk));
        }
        at(t0 + 242, () -> { logSeat("after the fpv"); shot("laser_10_fpv_down"); });
        // ---- the operator is on the seat: seen from the sensor (looking back) and from the side
        at(t0 + 244, () -> aimAt(mc().player.getEyePosition().add(0, -0.4, 0)));
        at(t0 + 248, () -> shot("laser_11_operator_from_sensor"));
        at(t0 + 249, () -> {
            Vec3 seat = mc().player.position().add(0, 1, 0), from = seat.add(4, 1.5, 3), d = seat.subtract(from);
            var cam = new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, mc().level);
            cam.moveTo(from.x, from.y, from.z, (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                    (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            LaserOperator.testView = cam;
        });
        at(t0 + 252, () -> shot("laser_12_operator_seated"));
        at(t0 + 253, () -> LaserOperator.testView = null);
        // ---- leave, then lose a cell: the vehicle collapses and gives its item back
        at(t0 + 256, () -> mc().options.keyShift.setDown(true));
        at(t0 + 260, () -> mc().options.keyShift.setDown(false));
        at(t0 + 266, () -> {
            var mc = mc();
            ModernArmory.LOGGER.info("[autotest] laser left the seat: operating {}, camera is player {}, player at {} {} {}",
                    LaserOperator.active(), mc.getCameraEntity() == mc.player, String.format("%.1f", mc.player.getX()),
                    String.format("%.1f", mc.player.getY()), String.format("%.1f", mc.player.getZ()));
            cmd("kill @e[type=minecraft:item]"); cmd("tp @s 0.5 -60 4.5 180 10");
        });
        at(t0 + 270, () -> cmd("setblock 1 -59 -3 minecraft:air")); // one cell of the turret gone
        at(t0 + 276, () -> logCells("after a cell was removed"));
        at(t0 + LENGTH - 4, () -> {
            cmd("kill @e[type=modernarmory:drone]"); cmd("kill @e[type=modernarmory:turret_seat]"); cmd("kill @e[type=minecraft:item]");
            cmd("fill -16 -60 -48 16 -40 6 minecraft:air"); cmd("fill -16 -61 -48 16 -61 6 minecraft:grass_block"); cmd("clear @s");
        });
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Point the operator's look (the sensor camera) at a world spot. */
    private static void aimAt(Vec3 at) {
        TurretSeat s = LaserOperator.seat();
        Vec3 from = s != null ? LaserOperator.cameraPos(s, 1) : mc().player.getEyePosition();
        Vec3 d = at.subtract(from);
        look((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    /** One left click: lock the drone nearest the crosshair and fire. */
    private static void clickFire(int now) {
        engagedAt = now;
        net.minecraft.client.KeyMapping.click(mc().options.keyAttack.getKey());
    }

    /** The scout's burn on the server (it cools while the beam is off it). */
    private static void logScoutHeat(String when) {
        var server = mc().getSingleplayerServer();
        if (server == null) return;
        for (DroneEntity d : server.overworld().getEntitiesOfClass(DroneEntity.class, new AABB(CORE).inflate(80),
                d -> d.isAlive() && d.type().id().equals("drone_scout")))
            ModernArmory.LOGGER.info("[autotest] laser scout {}: alive, burned {}", when, String.format("%.2f", d.laserHeat()));
    }

    private static boolean[] down = new boolean[2];

    /** Logs once when the drone of this run is gone (burned down), with how long it took from the click. */
    private static void watchKill(String what, int t0, int k) {
        int idx = what.equals("scout") ? 0 : 1;
        if (k == (idx == 0 ? 80 : 190)) down[idx] = false;
        if (down[idx]) return;
        var server = mc().getSingleplayerServer();
        if (server == null) return;
        String id = idx == 0 ? "drone_scout" : "drone_fpv";
        boolean alive = !server.overworld().getEntitiesOfClass(DroneEntity.class, new AABB(CORE).inflate(80),
                d -> d.isAlive() && d.type().id().equals(id)).isEmpty();
        if (!alive) {
            down[idx] = true;
            ModernArmory.LOGGER.info("[autotest] laser {} burned down {} ticks after the first click", what, t0 + k - engagedAt);
        }
    }

    private static void logPreview(String when) {
        var p = LaserOperator.preview;
        ModernArmory.LOGGER.info("[autotest] laser hologram {}: {}", when, p == null ? "none"
                : String.format("ok %s, problem %s, %d blocked, %d over a drop, core %s facing %s", p.ok(), p.problem(), p.blocked().size(),
                p.unsupported().size(), p.core().toShortString(), p.facing()));
    }

    private static void logCells(String when) {
        var server = mc().getSingleplayerServer();
        if (server == null) return;
        var level = server.overworld();
        int cells = 0;
        for (BlockPos p : BlockPos.betweenClosed(-4, -61, -12, 4, -56, 2))
            if (level.getBlockState(p).is(ModBlocks.LASER_TURRET.get())) cells++;
        var core = level.getBlockState(CORE);
        int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(CORE).inflate(8)).stream()
                .mapToInt(e -> e.getItem().is(com.modernarmory.registry.ModItems.LASER_TURRET.get()) ? e.getItem().getCount() : 0).sum();
        ModernArmory.LOGGER.info("[autotest] laser cells {}: {} cells, core {}, items dropped {}", when, cells,
                core.getBlock() instanceof LaserTurretBlock ? "facing " + core.getValue(LaserTurretBlock.FACING) + " cell "
                        + core.getValue(LaserTurretBlock.CELL) : "none", items);
    }

    private static void logSeat(String when) {
        TurretSeat s = LaserOperator.seat();
        var mc = mc();
        if (s == null) {
            ModernArmory.LOGGER.info("[autotest] laser {}: not at the controls (camera {})", when,
                    mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().toShortString());
            return;
        }
        var server = mc.getSingleplayerServer();
        String srv = "";
        if (server != null) for (TurretSeat ss : server.overworld().getEntitiesOfClass(TurretSeat.class, new AABB(CORE).inflate(6)))
            srv = String.format(" server: target %d firing %s heat %.2f burn %.2f power %.2f yaw %.1f el %.1f", ss.targetId(), ss.firing(),
                    ss.heat(), ss.burn(), ss.power(), ss.turretYaw(), ss.elevation());
        ModernArmory.LOGGER.info("[autotest] laser {}: operating {}, camera {}, tracks {}{}", when, LaserOperator.active(),
                mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().toShortString(), LaserOperator.tracks(s, 1).size(), srv);
    }

    private AutoTestLaser() {}
}
