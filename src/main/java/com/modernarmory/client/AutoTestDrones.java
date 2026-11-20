package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.registry.ModEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Autotest section "drones" (part of the core script, {@code -Pscenario=drones}): the scout drone deployed
 * from the item (pairs with the controller and opens the camera view), flown up and out, a husk group
 * spotted (outlined for the pilot) and zoomed in on, the link cut by a thick wall (return-to-home brings
 * it back), the exit key, re-entering with the controller, landing and picking it up; then the FPV drone
 * hand-launched, refusing to fire before it is armed, flown into a husk (contact detonation, the pilot
 * back in their body), and a second one detonated in the air from the controller.
 * Screenshots autotest_drones_*.png; log lines "[autotest] drones ...".
 */
final class AutoTestDrones {
    /** Ticks the section takes. */
    static final int LENGTH = 960;

    /** The FPV's strike camera has been seen (it starts whenever the attack run hits). */
    private static boolean strikeSeen;

    static void script(int t0) {
        Minecraft mc = Minecraft.getInstance();
        at(t0, () -> {
            cmd("kill @e[type=modernarmory:drone]"); cmd("kill @e[type=minecraft:husk]"); cmd("clear @s");
            cmd("difficulty easy"); cmd("time set 6000"); cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("fill -6 -60 -16 6 -45 -2 minecraft:air"); // a clear flying area in front (keeps the backstop wall)
            cmd("give @s modernarmory:drone_controller");
            cmd("give @s modernarmory:drone_scout");
            cmd("give @s modernarmory:drone_fpv");
            cmd("give @s modernarmory:drone_fpv");
            cmd("summon minecraft:husk -4 -60 -12 {NoAI:1b,PersistenceRequired:1b}");
            cmd("summon minecraft:husk -5 -60 -13 {NoAI:1b,PersistenceRequired:1b}");
        });
        // ---- scout: deploy on the ground in front (controller in the inventory -> camera view at once)
        at(t0 + 6, () -> { mc.player.getInventory().selected = 1; look(180, 50); });
        at(t0 + 10, () -> mc.options.keyUse.setDown(true));
        at(t0 + 11, () -> mc.options.keyUse.setDown(false));
        at(t0 + 22, () -> { log("scout deployed"); shot("drones_01_scout_on_ground_camera"); });
        // acro: no self-levelling, so the test pilot works the sticks (AutoTestPilot) to climb and fly out
        at(t0 + 24, () -> { AutoTestPilot.install(); AutoTestPilot.face(null, 10); AutoTestPilot.flyTo(new Vec3(0.5, -53.1, -7.6), 6); });
        at(t0 + 90, () -> { log("scout flown up and out"); shot("drones_02_scout_hud"); });
        // spot the husk pair
        at(t0 + 94, () -> aimDroneAt(-4.5, -59, -12.5));
        at(t0 + 98, () -> shot("drones_03_scout_aimed"));
        at(t0 + 100, () -> mc.options.keyAttack.setDown(true));
        at(t0 + 101, () -> mc.options.keyAttack.setDown(false));
        at(t0 + 108, () -> {
            int glowing = 0;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof LivingEntity l && l.getType() == EntityType.HUSK && l.isCurrentlyGlowing()) glowing++;
            ModernArmory.LOGGER.info("[autotest] drones scout spot: {} tagged, {} husks glowing for the pilot", DronePilot.spottedCount(), glowing);
            shot("drones_04_scout_spotted");
        });
        at(t0 + 110, () -> DronePilot.setZoom(4));
        at(t0 + 116, () -> { aimDroneAt(-4.5, -59, -12.5); shot("drones_05_scout_zoom4"); });
        at(t0 + 118, () -> DronePilot.setZoom(1));
        // a 7-block wall between pilot and drone: a modern link is weaker but holds
        // jammed (an anti-drone jammer on it): no link, the sticks do nothing (it holds), static; back when the jamming stops
        for (int k = 120; k < 132; k++) at(t0 + k, () -> jamAll(5));
        at(t0 + 121, () -> AutoTestPilot.flyTo(new Vec3(0.5, -50, -7.6), 6)); // asks to climb 3 m: it must not
        at(t0 + 126, () -> { logJam("jammed"); shot("drones_05b_scout_jammed"); });
        at(t0 + 131, () -> logJam("jammed, still"));
        at(t0 + 150, () -> logJam("jamming stopped"));
        at(t0 + 152, () -> AutoTestPilot.flyTo(new Vec3(0.5, -53.1, -7.6), 6));
        at(t0 + 170, () -> { cmd("fill -6 -60 -8 6 -55 -2 minecraft:stone"); AutoTestPilot.flyTo(null, 6); });
        at(t0 + 180, () -> { log("7-block wall (weaker, still linked)"); shot("drones_06_scout_wall"); });
        at(t0 + 182, () -> cmd("fill -6 -60 -8 6 -55 -2 minecraft:air"));
        // down a tunnel into a hill and round the bend: the rock cuts the link; a relay antenna at the bend carries it
        at(t0 + 184, () -> {
            cmd("fill 20 -60 -32 50 -41 -6 minecraft:stone");
            cmd("fill 39 -60 -24 41 -58 -6 minecraft:air");  // the tunnel in
            cmd("fill 22 -60 -26 41 -58 -24 minecraft:air"); // the bend, going west
            cmd("tp @s 40.5 -60 3 180 0");
        });
        at(t0 + 190, () -> { cmd("tp @e[type=modernarmory:drone] 40.5 -59.4 -3"); AutoTestPilot.flyTo(new Vec3(40.5, -59.4, -3), 6); });
        at(t0 + 200, () -> AutoTestPilot.route(6, new Vec3(40.5, -59.3, -25), new Vec3(24.5, -59.3, -25))); // in, round the bend
        at(t0 + 370, () -> { log("deep in the tunnel, no relay (link should be lost)"); shot("drones_06b_tunnel_no_signal"); });
        at(t0 + 372, () -> cmd("setblock 41 -60 -26 modernarmory:drone_relay[facing=west]"));
        at(t0 + 392, () -> { log("relay antenna at the bend (link through it)"); shot("drones_07_tunnel_relay"); });
        at(t0 + 410, () -> {
            cmd("fill 20 -60 -32 50 -41 -6 minecraft:air");
            cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("tp @e[type=modernarmory:drone] 0.5 -55 -3");
            AutoTestPilot.flyTo(null, 6);
        });
        // exit to the body, look at the hovering drone, then back in with the controller
        at(t0 + 433, () -> net.minecraft.client.KeyMapping.click(mc.options.keyTogglePerspective.getKey())); // F5 in the drone view
        at(t0 + 435, () -> { ModernArmory.LOGGER.info("[autotest] drones f5 in drone view: camera {}, view {}, body will be {}",
                mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().toShortString(), mc.options.getCameraType(),
                DronePilot.bodyCamera());
            shot("drones_07b_scout_f5"); });
        at(t0 + 436, () -> KeyBindings.DRONE_EXIT.setDown(true));
        at(t0 + 437, () -> KeyBindings.DRONE_EXIT.setDown(false));
        at(t0 + 440, () -> {
            ModernArmory.LOGGER.info("[autotest] drones after exit key: piloting {}, camera is player {}, mode {}", DronePilot.active(),
                    mc.getCameraEntity() == mc.player, mc.options.getCameraType());
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            cmd("tp @e[type=modernarmory:drone] 0.5 -58.5 -3");
            cmd("tp @s 2.5 -60 1.5");
        });
        at(t0 + 444, () -> cmd("setblock 1 -60 -2 modernarmory:drone_relay[facing=south]")); // a relay antenna in view
        at(t0 + 446, () -> lookAtDrone());
        at(t0 + 452, () -> { log("hovering, nobody flying"); shot("drones_08_scout_hovering"); });
        at(t0 + 454, () -> { mc.player.getInventory().selected = 0; look(180, 15); });
        at(t0 + 464, () -> shot("drones_09_controller_held"));
        at(t0 + 466, () -> mc.options.keyUse.setDown(true));
        at(t0 + 467, () -> mc.options.keyUse.setDown(false));
        at(t0 + 476, () -> { log("re-entered with the controller"); AutoTestPilot.face(null, 10); AutoTestPilot.land(); });
        at(t0 + 510, () -> log("landed"));
        at(t0 + 512, () -> KeyBindings.DRONE_EXIT.setDown(true));
        at(t0 + 513, () -> KeyBindings.DRONE_EXIT.setDown(false));
        at(t0 + 560, () -> { log("idle on the ground (motors should be off)"); lookAtDrone(); });
        at(t0 + 564, () -> {
            DroneEntity d = clientDrone();
            if (d != null) mc.gameMode.interact(mc.player, d, InteractionHand.MAIN_HAND);
        });
        at(t0 + 570, () -> {
            var inv = mc.player.getInventory();
            int scouts = 0;
            float battery = -1;
            for (var s : inv.items) if (s.getItem() instanceof com.modernarmory.drone.DroneItem di && di.type().id().equals("drone_scout")) {
                scouts++;
                battery = com.modernarmory.drone.DroneItem.battery(s);
            }
            ModernArmory.LOGGER.info("[autotest] drones scout picked up: {} in inventory, battery {}", scouts, battery);
        });
        // ---- FPV: hand-launch, safe before arming, fly into a husk
        at(t0 + 574, () -> { cmd("tp @s 0.5 -60 0.5 180 0"); cmd("kill @e[type=minecraft:husk]");
            cmd("kill @e[type=minecraft:armor_stand]"); // the range's camera stands: a live charge would hit them
            cmd("summon minecraft:husk 4.5 -60 -11 {NoAI:1b,PersistenceRequired:1b}"); });
        at(t0 + 580, () -> { mc.player.getInventory().selected = 2; look(180, -5); });
        at(t0 + 590, () -> shot("drones_10_fpv_held"));
        at(t0 + 592, () -> mc.options.keyUse.setDown(true));
        at(t0 + 593, () -> mc.options.keyUse.setDown(false));
        at(t0 + 594, () -> { log("fpv launched"); AutoTestPilot.face(null, 0); AutoTestPilot.flyTo(null, 6); mc.options.keyAttack.setDown(true); });
        at(t0 + 595, () -> mc.options.keyAttack.setDown(false));
        at(t0 + 600, () -> { log("fpv after trigger while SAFE (must still exist)"); shot("drones_11_fpv_safe"); });
        at(t0 + 660, () -> { log("fpv armed?"); aimDroneAt(4.5, -59, -11); });
        at(t0 + 662, () -> shot("drones_12_fpv_armed_aimed"));
        at(t0 + 664, () -> { aimDroneAt(4.5, -59, -11); AutoTestPilot.flyTo(new Vec3(4.5, -59.4, -11), 25, 60); }); // attack run, tilting hard
        at(t0 + 666, () -> aimDroneAt(4.5, -59, -11));
        at(t0 + 668, () -> shot("drones_13_fpv_attack_run"));
        for (int k = 666; k < 750; k++) at(t0 + k, () -> { // the charge goes off: the strike camera shows it
            if (!DronePilot.strikeCam() || strikeSeen) return;
            strikeSeen = true;
            ModernArmory.LOGGER.info("[autotest] drones fpv strike cam: on {}, camera {}", DronePilot.strikeCam(),
                    mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().toShortString());
            shot("drones_14_fpv_strike_cam");
        });
        at(t0 + 760, () -> { // 2 s later: back in the body
            ModernArmory.LOGGER.info("[autotest] drones fpv impact: piloting {}, camera is player {}, husks alive {}", DronePilot.active(),
                    mc.getCameraEntity() == mc.player, husks());
            log("after fpv impact");
            look(160, 0);
            shot("drones_14b_fpv_crater");
        });
        // ---- second FPV: launch, exit to see it from outside, back in with the controller, detonate in the air
        at(t0 + 770, () -> { mc.player.getInventory().selected = 3; look(180, -5); });
        at(t0 + 772, () -> mc.options.keyUse.setDown(true));
        at(t0 + 773, () -> mc.options.keyUse.setDown(false));
        at(t0 + 780, () -> { AutoTestPilot.face(null, 0); AutoTestPilot.flyTo(new Vec3(0.5, -54, -0.7), 6); });
        at(t0 + 794, () -> KeyBindings.DRONE_EXIT.setDown(true));
        at(t0 + 795, () -> KeyBindings.DRONE_EXIT.setDown(false));
        at(t0 + 800, () -> { cmd("tp @s 2.5 -60 1.5"); });
        at(t0 + 804, () -> lookAtDrone());
        at(t0 + 810, () -> { log("fpv hovering"); shot("drones_15_fpv_hovering"); });
        at(t0 + 844, () -> { mc.player.getInventory().selected = 0; mc.options.keyUse.setDown(true); });
        at(t0 + 845, () -> mc.options.keyUse.setDown(false));
        at(t0 + 846, () -> { AutoTestPilot.install(); AutoTestPilot.flyTo(new Vec3(0.5, -54, -0.7), 6); });
        at(t0 + 870, () -> logAttitude("stabilised hover")); // the flight controller holds it level
        at(t0 + 884, () -> { log("fpv #2 re-entered, armed?"); shot("drones_16_fpv_armed_hud"); mc.options.keyAttack.setDown(true); });
        at(t0 + 885, () -> mc.options.keyAttack.setDown(false));
        at(t0 + 888, () -> { log("after air detonation"); ModernArmory.LOGGER.info("[autotest] drones air burst strike cam: on {}", DronePilot.strikeCam()); shot("drones_17_fpv_air_detonation"); });
        at(t0 + 944, () -> ModernArmory.LOGGER.info("[autotest] drones end: piloting {}, camera is player {}", DronePilot.active(),
                mc.getCameraEntity() == mc.player));
        at(t0 + LENGTH - 4, () -> { DronePilot.testPilot = null; DronePilot.testInput = null; cmd("kill @e[type=modernarmory:drone]"); cmd("kill @e[type=minecraft:husk]"); cmd("difficulty peaceful");
            // fill the craters back in (superflat: dirt under grass), so the sections after this one find flat ground
            cmd("fill -14 -60 -19 14 -45 -2 minecraft:air"); cmd("fill -14 -63 -19 14 -62 -2 minecraft:dirt");
            cmd("fill -14 -61 -19 14 -61 -2 minecraft:grass_block"); cmd("kill @e[type=minecraft:item]"); });
    }

    /** Server: jams every drone in a 160 m, 15 degree cone from the player's eyes toward the scout's spot (a jammer's beam). */
    private static void jamAll(int ticks) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var level = server.overworld();
            var p = server.getPlayerList().getPlayers().get(0);
            Vec3 eye = p.getEyePosition();
            for (DroneEntity d : com.modernarmory.drone.DroneJamming.inCone(level, eye, new Vec3(0.5, -53, -7.6).subtract(eye), 160, 15))
                com.modernarmory.drone.DroneJamming.jam(d, ticks, p);
        });
    }

    /** The jam as the server and the pilot's client see it. */
    private static void logJam(String when) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        StringBuilder sb = new StringBuilder();
        if (server != null) for (var e : server.overworld().getEntities(ModEntities.DRONE.get(), e -> true)) {
            DroneEntity d = (DroneEntity) e;
            sb.append(String.format(" [server jammed %s linked %s sig %.2f at y %.2f]", d.jammed(), d.linked(), d.signal(), d.getY()));
        }
        DroneEntity c = DronePilot.drone();
        ModernArmory.LOGGER.info("[autotest] drones jam {}: piloting {}{}{}", when, DronePilot.active(), sb,
                c == null ? " (no client drone)" : String.format(" client jammed %s, controls %s, y %.2f", c.jammed(), c.pilotControls(), c.getY()));
    }

    /** The first drone the client knows of. */
    private static DroneEntity clientDrone() {
        for (var e : Minecraft.getInstance().level.entitiesForRendering()) if (e instanceof DroneEntity d) return d;
        return null;
    }

    /** Point the drone camera at a spot: turn the nose to it and tilt the gimbal (scout). */
    private static void aimDroneAt(double x, double y, double z) {
        DroneEntity d = DronePilot.drone();
        if (d == null) return;
        var eye = d.getEyePosition();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        AutoTestPilot.face((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    /** The piloted drone's attitude: nose pitch, and whether it is upside down. */
    private static void logAttitude(String when) {
        DroneEntity d = DronePilot.drone();
        if (d == null) {
            ModernArmory.LOGGER.info("[autotest] drones attitude {}: no drone", when);
            return;
        }
        var q = d.attitude();
        var up = q.transform(new org.joml.Vector3f(0, 1, 0));
        var fw = q.transform(new org.joml.Vector3f(0, 0, -1));
        ModernArmory.LOGGER.info("[autotest] drones attitude {}: up.y {}, nose pitch {} deg, inverted {}, at {} {} {}", when,
                String.format("%.2f", up.y), Math.round(Math.toDegrees(Math.asin(fw.y))), up.y < 0,
                String.format("%.1f", d.getX()), String.format("%.1f", d.getY()), String.format("%.1f", d.getZ()));
    }

    /** Point the body's eyes at the drone. */
    private static void lookAtDrone() {
        DroneEntity d = clientDrone();
        var p = Minecraft.getInstance().player;
        if (d == null) return;
        double dx = d.getX() - p.getX(), dy = d.getY() + 0.1 - p.getEyeY(), dz = d.getZ() - p.getZ();
        look((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static int husks() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? -1 : server.overworld().getEntities(EntityType.HUSK, e -> e.isAlive()).size();
    }

    /** Server-side state of every drone, plus whether the client is piloting. */
    private static void log(String when) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        StringBuilder sb = new StringBuilder();
        if (server != null) for (var e : server.overworld().getEntities(ModEntities.DRONE.get(), e -> true)) {
            DroneEntity d = (DroneEntity) e;
            sb.append(String.format(" [%s at %.1f %.1f %.1f bat %.3f sig %.2f%s thr %.2f pilot %d arm %.1f hp %.1f ground %s]",
                    d.type().id(), d.getX(), d.getY(), d.getZ(), d.battery(), d.signal(), d.relayed() ? " via relay" : "", d.throttle(),
                    d.pilotId(), d.armSeconds(), d.health(), d.onGround()));
        }
        Minecraft mc = Minecraft.getInstance();
        DroneEntity c = clientDrone();
        String client = c == null ? "" : String.format(" client at %.1f %.1f %.1f", c.getX(), c.getY(), c.getZ());
        ModernArmory.LOGGER.info("[autotest] drones {}: piloting {}, camera {}{}{}", when, DronePilot.active(),
                mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().toShortString(), sb.isEmpty() ? " (no drones)" : sb, client);
    }

    private AutoTestDrones() {}
}
