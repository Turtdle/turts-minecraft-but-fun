package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.buildRange;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.logScope;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;
import static com.modernarmory.client.AutoTest.setHudHidden;
import static com.modernarmory.client.AutoTest.shot;

import com.modernarmory.ModernArmory;
import com.modernarmory.network.GunActionPayload;
import com.modernarmory.server.GunHandler;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Autotest scenario "core" (the default): the M4 (idle / aiming / burst fire / reload / third
 * person / impacts / item frame), M4A1 full auto, the M2010 through its scope (mil scaling, eye
 * box), attachments and the Gunsmith Table, and gun mastery. Screenshot names and timings are
 * the regression baseline; don't renumber them.
 */
final class AutoTestCore {
    static void script() {
        // Superflat surface is y=-61; stand at y=-60 facing north (-Z).
        at(20, () -> {
            buildRange();
            cmd("clear @s");
            cmd("give @s modernarmory:m4");
            cmd("give @s modernarmory:ammo_556 64");
            cmd("forceload add 0 140"); // mil-scaling target, placed once the chunk has loaded
        });
        at(30, () -> mc().player.getInventory().setSelectedSlot(0));
        at(60, () -> { mc().options.setCameraType(CameraType.FIRST_PERSON); look(180, 0); });
        at(80, () -> shot("01_fp_idle"));
        at(85, () -> mc().options.keyUse.setDown(true));
        at(100, () -> shot("02_fp_aim"));
        at(102, () -> mc().options.keyUse.setDown(false));
        // switch to burst and fire two bursts at the wall
        at(110, () -> selectMode(com.modernarmory.gun.FireMode.BURST));
        at(120, () -> { look(180, 2); mc().options.keyAttack.setDown(true); });
        at(121, () -> shot("03_fp_firing"));
        at(126, () -> mc().options.keyAttack.setDown(false));
        at(136, () -> { look(176, 1); mc().options.keyAttack.setDown(true); });
        at(142, () -> mc().options.keyAttack.setDown(false));
        at(150, () -> shot("04_after_firing"));
        at(155, () -> ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD)));
        at(161, () -> shot("05a_reload_0.3s"));
        at(165, () -> shot("05b_reload_0.5s"));
        at(171, () -> shot("05c_reload_0.8s"));
        at(177, () -> shot("06_reload_1.1s"));
        at(183, () -> shot("06b_reload_1.4s"));
        at(189, () -> shot("07_reload_1.7s"));
        at(215, () -> { mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc().options.fov().set(30); });
        at(225, () -> shot("08_third_person_front"));
        at(230, () -> { mc().options.setCameraType(CameraType.THIRD_PERSON_BACK); look(150, 10); });
        at(240, () -> shot("09_third_person_back"));
        at(245, () -> { mc().options.setCameraType(CameraType.FIRST_PERSON); mc().options.fov().set(70); cmd("tp @s 0.5 -60 -17 180 10"); });
        at(265, () -> shot("10_impacts_closeup"));
        at(270, () -> { cmd("tp @s 6 -58 -12 125 5"); });
        at(285, () -> shot("11_range_side_view"));
        at(290, () -> { mc().player.getInventory().setSelectedSlot(5); cmd("tp @s 4.75 -60 0.2 0 12"); mc().options.fov().set(45); });
        at(310, () -> shot("12_frame_and_ground"));
        // third person from the side, through the eyes of an invisible armour stand
        at(312, () -> { mc().player.getInventory().setSelectedSlot(0); cmd("tp @s 0.5 -60 0.5 180 0"); mc().options.fov().set(30); });
        at(322, () -> {
            for (var e : mc().level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && e.getY() < -58) mc().setCameraEntity(e);
            }
            setHudHidden(true); // also hides the first-person hand
        });
        at(332, () -> shot("13_third_person_side"));
        at(334, () -> mc().options.keyUse.setDown(true));
        at(344, () -> shot("14_third_person_side_aim"));
        at(346, () -> { mc().options.keyUse.setDown(false);
            for (var e : mc().level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && e.getY() > -58) mc().setCameraEntity(e);
            }
        });
        at(356, () -> shot("15_third_person_top"));
        at(358, () -> { mc().setCameraEntity(mc().player); mc().options.fov().set(70); setHudHidden(false); });
        // M4A1 full auto: survival so ammo counts down, hold the trigger 0.5 s (~6-7 rounds at 800 rpm)
        at(360, () -> { cmd("give @s modernarmory:m4a1"); cmd("gamemode survival"); cmd("tp @s 0.5 -60 0.5 180 0"); });
        at(366, () -> { mc().player.getInventory().setSelectedSlot(2); });
        at(372, () -> selectMode(com.modernarmory.gun.FireMode.AUTO));
        at(380, () -> mc().options.keyAttack.setDown(true));
        at(390, () -> mc().options.keyAttack.setDown(false));
        at(398, () -> { shot("16_m4a1_after_auto"); cmd("gamemode creative"); });
        // M2010: hip, scoped, shot through the scope, bolt cycle, third person (survival: 5-round mag)
        at(405, () -> { cmd("give @s modernarmory:m2010"); cmd("give @s modernarmory:ammo_300wm 20"); cmd("gamemode survival"); cmd("tp @s 0.5 -60 0.5 180 0"); });
        at(412, () -> { mc().player.getInventory().setSelectedSlot(3); look(180, 1); });
        at(420, () -> shot("17_m2010_hip"));
        at(422, () -> mc().options.keyUse.setDown(true));
        // the scope (x0.75) and suppressor (x0.95) slow the 0.2 s raise to 0.28 s: behind the glass
        // one tick later than a bare gun, so the blend frames are taken one tick later too
        at(429, () -> shot("18a_m2010_scope_in_blend_1"));
        at(430, () -> shot("18b_m2010_scope_in_blend_2"));
        at(434, () -> shot("18_m2010_scoped"));
        at(436, () -> mc().options.keyAttack.setDown(true));
        at(437, () -> mc().options.keyAttack.setDown(false));
        at(440, () -> shot("19_m2010_after_shot"));
        at(441, () -> mc().options.keyUse.setDown(false));
        at(450, () -> shot("20_m2010_bolt_cycle"));
        at(470, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        at(478, () -> shot("21_m2010_third_person"));
        at(480, () -> { mc().options.setCameraType(CameraType.FIRST_PERSON); cmd("gamemode creative"); });
        // Mil scaling: a 2-block red pillar whose front face is exactly 100 m from the eye, aimed
        // at its foot, must reach 20 mils up the reticle at any power (6.5x and 20x).
        at(482, () -> {
            cmd("fill 0 -60 140 0 -59 140 minecraft:red_concrete");
            cmd("tp @s 0.5 -60 40.0 0 0");
        });
        at(486, () -> { look(0, (float) Math.toDegrees(Math.atan2(1.62, 100))); mc().options.keyUse.setDown(true); });
        at(490, () -> ClientState.setScopeZoom(6.5f));
        at(500, () -> { logScope(); shot("22_m2010_6.5x_pillar_100m"); });
        at(502, () -> ClientState.setScopeZoom(20f));
        at(514, () -> { logScope(); shot("23_m2010_20x_pillar_100m"); });
        // eye box: the kick of a .300 Win Mag at 20x throws the eye out of the 2.5 mm exit pupil
        at(516, () -> mc().options.keyAttack.setDown(true));
        at(517, () -> mc().options.keyAttack.setDown(false));
        at(519, () -> shot("24a_m2010_20x_shot_+0.1s"));
        at(523, () -> shot("24b_m2010_20x_shot_+0.3s"));
        at(532, () -> shot("24c_m2010_20x_shot_+0.75s"));
        // a fast swing at 20x (~50 deg/s): the head lags the rifle, a crescent creeps in
        for (int i = 0; i < 8; i++) {
            final int k = i;
            at(560 + i, () -> { var pl = mc().player; look(pl.getYRot() + 2.5f, pl.getXRot()); });
            if (k == 6) at(560 + i, () -> shot("25_m2010_20x_swing"));
        }
        at(575, () -> ClientState.setScopeZoom(6.5f));
        at(590, () -> shot("26_m2010_6.5x_settled"));
        at(592, () -> mc().options.keyUse.setDown(false));
        // ---- attachments: pre-fitted guns via item components, aiming through each optic, Gunsmith Table
        at(600, () -> {
            giveLoadouts();
            mc().options.setCameraType(CameraType.FIRST_PERSON);
        });
        at(608, () -> { mc().player.getInventory().setSelectedSlot(0); look(180, 0); });
        at(620, () -> shot("27_m4_loadout_a_hip"));
        at(622, () -> mc().options.keyUse.setDown(true));
        at(636, () -> shot("28_m4_red_dot_ads"));
        at(638, () -> mc().options.keyUse.setDown(false));
        at(642, () -> mc().player.getInventory().setSelectedSlot(3));
        at(650, () -> mc().options.keyUse.setDown(true));
        at(664, () -> shot("29_m4_holographic_ads"));
        at(666, () -> mc().options.keyUse.setDown(false));
        at(670, () -> mc().player.getInventory().setSelectedSlot(1));
        at(676, () -> shot("30a_m4_loadout_b_hip"));
        at(678, () -> mc().options.keyUse.setDown(true));
        at(696, () -> shot("30_m4_acog_scoped"));
        at(698, () -> mc().options.keyUse.setDown(false));
        at(702, () -> mc().player.getInventory().setSelectedSlot(2));
        at(712, () -> shot("31_m2010_bare_hip"));
        // Gunsmith Table
        at(716, () -> { cmd("setblock 0 -60 -1 modernarmory:gunsmith_table"); mc().player.getInventory().setSelectedSlot(0); look(180, 60); });
        at(724, () -> mc().gameMode.useItemOn(mc().player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -59.0, -0.5),
                        net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(0, -60, -1), false)));
        // hotbar slot c is menu slot INV_START + 27 + c
        at(732, () -> gunsmithClick(com.modernarmory.menu.GunsmithMenu.INV_START + 27)); // M4 (loadout A) onto the bench
        at(740, () -> shot("32_gunsmith_m4_loadout_a"));
        at(742, () -> gunsmithClick(com.modernarmory.menu.GunsmithMenu.RESULT_SLOT));   // take the (unchanged) result
        at(743, () -> ModernArmory.LOGGER.info("[autotest] gunsmith m4 after taking result: {} / {}",
                mc().player.getInventory().getItem(0).getItem(), attsOf(mc().player.getInventory().getItem(0))));
        at(744, () -> cmd("give @s modernarmory:m2010"));                                // factory M2010 (scope + can) -> hotbar 4
        at(748, () -> gunsmithClick(com.modernarmory.menu.GunsmithMenu.INV_START + 31)); // M2010 onto the bench
        at(754, () -> shot("33_gunsmith_m2010_loaded"));
        at(755, () -> gunsmithClick(com.modernarmory.menu.GunsmithMenu.ATT_START));     // optic off
        at(758, () -> shot("33b_gunsmith_m2010_no_optic"));
        at(760, () -> mc().player.closeContainer());                                     // the gun leaves with the parts left
        at(764, () -> ModernArmory.LOGGER.info("[autotest] gunsmith m2010 after close: {} / {}; scope back in inventory: {}",
                mc().player.getInventory().getItem(4).getItem(), attsOf(mc().player.getInventory().getItem(4)),
                countOf(com.modernarmory.registry.ModItems.ATTACHMENTS.get("sniper_scope").get())));
        // ---- gun mastery: XP from shots + a hostile kill, then a level-up to V
        at(770, () -> {
            cmd("clear @s"); cmd("give @s modernarmory:m4"); cmd("difficulty easy"); cmd("tp @s 0.5 -60 0.5 180 0"); cmd("gunmastery set @s m4 0");
            cmd("summon minecraft:zombie 0.5 -60 -9 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            mc().player.getInventory().setSelectedSlot(0); look(180, 0);
        });
        for (int k = 0; k < 4; k++) {
            int t0 = 784 + k * 4;
            at(t0, () -> mc().options.keyAttack.setDown(true));
            at(t0 + 1, () -> mc().options.keyAttack.setDown(false));
        }
        at(806, () -> ModernArmory.LOGGER.info("[autotest] mastery m4 after zombie: {} xp, level {}",
                ClientMastery.data().xp("m4"), ClientMastery.data().level("m4")));
        at(808, () -> shot("34_mastery_after_kill"));
        at(810, () -> cmd("gunmastery set @s m4 3999"));
        at(816, () -> mc().options.keyAttack.setDown(true));
        at(817, () -> mc().options.keyAttack.setDown(false));
        at(820, () -> { ModernArmory.LOGGER.info("[autotest] mastery m4 after level-up shot: {} xp, level {}",
                ClientMastery.data().xp("m4"), ClientMastery.data().level("m4")); shot("35_mastery_level_up"); });
        // endermen: the first bullet makes them teleport (no damage), the next one within the cooldown hits
        at(824, () -> { cmd("kill @e[type=zombie]");
            cmd("summon minecraft:enderman 0.5 -60 -8 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}"); look(180, 2); });
        at(826, () -> mc().options.keyUse.setDown(true)); // aim: hip spread can miss a thin enderman at 8 m
        at(832, () -> ModernArmory.LOGGER.info("[autotest] enderman before: {}", endermanState()));
        at(834, () -> mc().options.keyAttack.setDown(true));
        at(835, () -> mc().options.keyAttack.setDown(false));
        at(842, () -> ModernArmory.LOGGER.info("[autotest] enderman after 1st bullet (should teleport, full health): {}", endermanState()));
        at(844, () -> cmd("tp @e[type=minecraft:enderman,limit=1] 0.5 -60 -8 0 0"));
        at(850, () -> mc().options.keyAttack.setDown(true));
        at(851, () -> mc().options.keyAttack.setDown(false));
        at(858, () -> ModernArmory.LOGGER.info("[autotest] enderman after 2nd bullet (should be hurt, still here): {}", endermanState()));
        at(860, () -> { mc().options.keyUse.setDown(false); cmd("kill @e[type=minecraft:enderman]"); cmd("difficulty peaceful"); });
        // mastery tab on the survival inventory, then the mastery screen (the M4 is at mastery V by now)
        at(860 + 1, () -> cmd("gamemode survival")); // the creative inventory has its own tabs; the mastery tab is on the survival one
        at(862, () -> { for (String i : new String[] {"m4", "deagle", "m2010", "m249", "m590", "scorpion_evo3", "anaconda",
                "grenade_m67", "grenade_m84", "grenade_m18", "grenade_m7a3", "grenade_anm14"}) cmd("give @s modernarmory:" + i); }); // 2D inventory sprites
        at(864, () -> mc().gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)));
        at(866, () -> mc().gui.toastManager().clear()); // advancement pop-ups would cover the tab
        at(868, () -> shot("36_inventory_mastery_tab"));
        at(870, () -> mc().gui.setScreen(new MasteryScreen()));
        at(876, () -> shot("37_mastery_screen"));
        at(878, () -> { mc().gui.setScreen(null); cmd("gamemode creative"); });
        // aiming a scoped rifle in third person: the character aims, but no scope picture or zoom
        at(880, () -> { cmd("clear @s"); cmd("give @s modernarmory:m2010"); cmd("tp @s 0.5 -60 0.5 180 0"); });
        at(884, () -> { mc().player.getInventory().setSelectedSlot(0); mc().options.setCameraType(CameraType.THIRD_PERSON_BACK); });
        at(888, () -> mc().options.keyUse.setDown(true));
        at(904, () -> { ModernArmory.LOGGER.info("[autotest] third person aim: scopedIn={}", ClientState.scopedIn()); shot("38_m2010_aim_third_person"); });
        at(906, () -> { mc().options.keyUse.setDown(false); mc().options.setCameraType(CameraType.FIRST_PERSON); });
        // tracer rounds at night: an M4A1 on full auto loaded from tracer ammo (first in the hotbar), so
        // a stream of tracers is in the air for the screenshots. (Creative: ammo is not used up.)
        at(912, () -> { cmd("clear @s"); cmd("give @s modernarmory:m4a1"); cmd("give @s modernarmory:ammo_556_tracer 64");
            cmd("time set midnight"); cmd("tp @s 0.5 -60 0.5 0 0"); });
        at(916, () -> { mc().player.getInventory().setSelectedSlot(0); look(0, -2); }); // facing away from the range: open field, nothing to stop the tracers
        at(918, () -> selectMode(com.modernarmory.gun.FireMode.AUTO));
        at(922, () -> ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD)));
        at(970, () -> { var st = mc().player.getMainHandItem();
            ModernArmory.LOGGER.info("[autotest] tracer loaded: {}, mode {}", st.getItem() instanceof com.modernarmory.item.GunItem g && g.loadedTracer(st),
                    st.getItem() instanceof com.modernarmory.item.GunItem g2 ? g2.fireMode(st).label() : "-"); });
        at(972, () -> mc().options.keyAttack.setDown(true));
        at(976, () -> { logBullets(); shot("39_tracer_first_person"); });
        at(978, () -> shot("39b_tracer_first_person"));
        at(980, () -> mc().options.keyAttack.setDown(false));
        at(982, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        at(986, () -> mc().options.keyAttack.setDown(true));
        at(990, () -> shot("40_tracer_third_person"));
        at(992, () -> mc().options.keyAttack.setDown(false));
        at(994, () -> { mc().options.setCameraType(CameraType.FIRST_PERSON); cmd("time set noon"); });
        // crosshair editor: settings hub, editor, a custom style on the HUD, then back to defaults
        at(998, () -> { cmd("clear @s"); cmd("give @s modernarmory:m4"); cmd("time set noon"); cmd("tp @s 0.5 -60 0.5 180 0");
            mc().player.getInventory().setSelectedSlot(0); look(180, 0); });
        at(1002, () -> mc().gui.setScreen(new SettingsScreen(net.neoforged.fml.ModList.get().getModContainerById(ModernArmory.MODID).orElseThrow(), null)));
        at(1006, () -> shot("41_settings"));
        at(1008, () -> mc().gui.setScreen(new CrosshairEditorScreen(null)));
        at(1014, () -> shot("42_crosshair_editor"));
        at(1016, () -> { mc().gui.setScreen(null);
            new CrosshairConfig.Style(5, 2, 3, 100, 0x55FF55, true, true, false, true).save(); });
        at(1022, () -> shot("43_crosshair_custom_hud"));
        at(1024, () -> { CrosshairConfig.Style.DEFAULT.save();
            ModernArmory.LOGGER.info("[autotest] crosshair config restored: {}", CrosshairConfig.Style.current()); });
        // hand grenades, CS-style controls: hold a button (pin out), let go to throw; left = overhand,
        // right = underhand, both = medium. Then each effect
        at(1030, () -> { cmd("clear @s"); cmd("gamemode creative"); cmd("difficulty easy"); cmd("tp @s 0.5 -60 0.5 180 0");
            for (String g : new String[] {"grenade_m67", "grenade_m84", "grenade_m18", "grenade_anm14"}) cmd("give @s modernarmory:" + g + " 4");
            cmd("summon minecraft:husk 0.5 -60 -7 {NoAI:1b,PersistenceRequired:1b}"); }); // husks don't burn in daylight
        at(1034, () -> { mc().player.getInventory().setSelectedSlot(0); look(180, -5); });
        // M67, underhand toward the zombie
        at(1036, () -> mc().options.keyUse.setDown(true));
        at(1044, () -> shot("44_grenade_pin_pulled"));
        at(1050, () -> mc().options.keyUse.setDown(false));
        at(1052, () -> logGrenades("m67 thrown"));
        at(1054, () -> shot("45_grenade_in_flight"));
        at(1080, () -> logGrenades("m67 landed"));
        at(1082, () -> { // close up: it should lie on its side, not stand on its end
            for (var e : mc().level.entitiesForRendering())
                if (e instanceof com.modernarmory.grenade.GrenadeEntity g)
                    cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 90 45", g.getX() + 1.4, g.getY(), g.getZ())); });
        at(1090, () -> { logGrenades("m67 resting"); shot("45b_grenade_resting"); });
        at(1092, () -> cmd("tp @s 0.5 -60 0.5 180 -5"));
        at(1146, () -> { logZombies("after m67"); shot("46_frag_explosion"); });
        // M84, medium (both buttons), looking at it
        at(1150, () -> { mc().player.getInventory().setSelectedSlot(1); look(180, 0); });
        at(1152, () -> { mc().options.keyUse.setDown(true); mc().options.keyAttack.setDown(true); });
        at(1164, () -> { mc().options.keyUse.setDown(false); mc().options.keyAttack.setDown(false); });
        at(1176, () -> logGrenades("m84 medium throw, in flight"));
        at(1180, () -> look(180, 20)); // watching where it landed
        at(1202, () -> { ModernArmory.LOGGER.info("[autotest] flashbang white-out {}", FlashbangOverlay.current()); shot("47_flashbang"); });
        // M18 smoke, overhand (left click); its tumble logged through the flight (one heading, steady spin)
        at(1300, () -> { mc().player.getInventory().setSelectedSlot(2); look(180, -8); });
        at(1302, () -> mc().options.keyAttack.setDown(true));
        at(1314, () -> mc().options.keyAttack.setDown(false));
        for (int k = 1318; k <= 1330; k += 2) at(k, () -> logGrenades("m18 overhand, in flight"));
        at(1330, () -> look(180, 0));
        at(1460, () -> { logGrenades("m18 burning"); shot("48_smoke"); });
        // AN-M14 at a second zombie, underhand, from a single quick click
        at(1466, () -> { cmd("kill @e[type=minecraft:husk]"); cmd("summon minecraft:husk 0.5 -60 -5 {NoAI:1b,PersistenceRequired:1b}");
            mc().player.getInventory().setSelectedSlot(3); look(180, 0); });
        at(1470, () -> mc().options.keyUse.setDown(true)); // a single quick right-click: thrown once the pin is out
        at(1471, () -> mc().options.keyUse.setDown(false));
        at(1490, () -> logGrenades("an-m14 quick click, in flight"));
        at(1540, () -> { logZombies("after an-m14"); shot("49_thermite"); });
        at(1546, () -> { cmd("kill @e[type=minecraft:husk]"); cmd("kill @e[type=modernarmory:grenade]"); cmd("difficulty peaceful"); });
        // drones: scout (deploy, fly, spot, link loss, controller, pickup) and FPV (arming, impact, air burst)
        AutoTestDrones.script(DRONES_START);
        // hit registration on a moving target, with and without lag compensation
        AutoTestHitreg.script(HITREG_START);
        // third-person gun pose while strafing
        AutoTestPose.script(POSE_START);
        // a thrown grenade filmed from the side
        AutoTestGrenadeFlight.script(NADEFLIGHT_START);
        // the HELWS laser turret: placement (hologram, refused when blocked), seat, burning drones down
        AutoTestLaser.script(LASER_START);
        // the PEQ-15 laser dot, from the shooter and from the side
        AutoTestLaserDot.script(LASERDOT_START);
        // bullet holes: one column per material, one row per style
        AutoTestHoles.script(HOLES_START);
        // mastery: less view bob while aiming
        AutoTestAimBob.script(AIMBOB_START);
        // recoil recovers all the way back to the aim point
        AutoTestRecoil.script(RECOIL_START);
        // one creative tab per kind of gun
        AutoTestTabs.script(TABS_START);
        // grenade fragments glance off stone, once
        AutoTestFragments.script(FRAGMENTS_START);
        // M7A3 tear gas: blindness that lasts longer the longer you stood in it
        AutoTestTearGas.script(TEARGAS_START);
        // AN-M14: bursts into thermite fire that never spreads and burns out
        AutoTestThermite.script(THERMITE_START);
        // mastery: tighter hip fire
        AutoTestHipSpread.script(HIPSPREAD_START);
        // which attachments fit which guns; the newly opened slots photographed
        AutoTestAttachFit.script(ATTFIT_START);
        // every bullet gun kills with a point-blank headshot; pellets don't
        AutoTestHeadshot.script(HEADSHOT_START);
        // flashbang: walls stop the flash, facing away weakens it, the bang still rings
        AutoTestFlash.script(FLASH_START);
        // overhand throws: far, and the same for every grenade
        AutoTestThrowDistance.script(THROWDIST_START);
        // smoke hides you from mobs; a flash dazes them
        AutoTestMobSenses.script(MOBSENSES_START);
        // body armour: what each piece stops, by body zone; the tiers photographed
        AutoTestKevlar.script(KEVLAR_START);
        // frag grenade: lethal close in, survivable far out
        AutoTestFragLethal.script(FRAGLETHAL_START);
        // iron sights fold under a red dot
        AutoTestIronSights.script(IRONSIGHTS_START);
        // how many guns you can carry, the heavy-load slowdown, the inventory highlight
        AutoTestGunCarry.script(GUNCARRY_START);
        at(GUNCARRY_START + AutoTestGunCarry.LENGTH + 2, AutoTest::finish);
    }

    private AutoTestCore() {}

    /** Puts the held gun on {@code mode}, flicking the selector (the server switches it) as many times as it takes. */
    private static void selectMode(com.modernarmory.gun.FireMode mode) {
        var st = mc().player.getMainHandItem();
        if (!(st.getItem() instanceof com.modernarmory.item.GunItem g)) return;
        var modes = g.spec().fireModes();
        int steps = Math.floorMod(modes.indexOf(mode) - modes.indexOf(g.fireMode(st)), modes.size());
        for (int i = 0; i < steps; i++) ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_FIRE_MODE));
    }

    private static void gunsmithClick(int slot) {
        if (mc().player.containerMenu instanceof com.modernarmory.menu.GunsmithMenu gm)
            mc().gameMode.handleContainerInput(gm.containerId, slot, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc().player);
    }

    private static int countOf(net.minecraft.world.item.Item item) {
        var inv = mc().player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    private static String attsOf(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof com.modernarmory.item.GunItem g ? g.attachments(stack).slots().toString() : "-";
    }

    /** Health and position of the test enderman, read from the integrated server. */
    private static String endermanState() {
        var server = mc().getSingleplayerServer();
        if (server == null) return "no server";
        var list = server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.ENDERMAN, e -> true);
        if (list.isEmpty()) return "none";
        var e = list.get(0);
        return String.format("health %.1f at %.1f %.1f %.1f", e.getHealth(), e.getX(), e.getY(), e.getZ());
    }

    /** Slots 0-3: M4 loadout A (every slot filled), M4 loadout B (ACOG), a bare M2010, M4 with a holo. */
    static void giveLoadouts() {
        cmd("clear @s"); cmd("gamemode creative"); cmd("tp @s 0.5 -60 0.5 180 0");
        cmd("give @s modernarmory:m4[modernarmory:attachments={OPTIC:\"red_dot\",MUZZLE:\"suppressor_556\",UNDERBARREL:\"vertical_grip\",SIDE:\"laser\",STOCK:\"stock_ctr\",MAGAZINE:\"pmag_40\"}]");
        cmd("give @s modernarmory:m4[modernarmory:attachments={OPTIC:\"acog\",MUZZLE:\"compensator_556\",UNDERBARREL:\"angled_grip\"}]");
        cmd("give @s modernarmory:m2010[modernarmory:attachments={}]");
        cmd("give @s modernarmory:m4[modernarmory:attachments={OPTIC:\"holographic\"}]");
    }

    /** What every section needs when it runs on its own: the range, creative, a known spot and view. */
    private static void baseSetup() {
        buildRange();
        cmd("gamemode creative"); cmd("difficulty peaceful"); cmd("tp @s 0.5 -60 0.5 180 0");
        mc().options.setCameraType(CameraType.FIRST_PERSON);
        mc().options.keyUse.setDown(false);
        mc().options.keyAttack.setDown(false);
    }

    private static void withRifles(boolean m4a1) {
        baseSetup();
        cmd("clear @s"); cmd("give @s modernarmory:m4"); cmd("give @s modernarmory:ammo_556 64");
        if (m4a1) cmd("give @s modernarmory:m4a1");
    }

    /**
     * The core script cut into sections ([start, end) ticks of the script above) for
     * {@code -Pscenario=<section>[,<section>...]}: only those steps run, packed back to back, each after its
     * setup, which recreates what earlier sections would have left behind (guns in the right slots, ...).
     */
    /** First tick of the drone steps (AutoTestDrones). */
    private static final int DRONES_START = 1560;
    /** First tick of the hit-registration steps (AutoTestHitreg). */
    private static final int HITREG_START = DRONES_START + AutoTestDrones.LENGTH + 10;
    /** First tick of the third-person pose steps (AutoTestPose). */
    private static final int POSE_START = HITREG_START + AutoTestHitreg.LENGTH + 10;
    /** First tick of the grenade-flight film (AutoTestGrenadeFlight). */
    private static final int NADEFLIGHT_START = POSE_START + AutoTestPose.LENGTH + 10;
    /** First tick of the laser turret steps (AutoTestLaser). */
    private static final int LASER_START = NADEFLIGHT_START + AutoTestGrenadeFlight.LENGTH + 10;
    /** First tick of the PEQ-15 laser-dot steps (AutoTestLaserDot). */
    private static final int LASERDOT_START = LASER_START + AutoTestLaser.LENGTH + 10;
    /** First tick of the bullet-hole steps (AutoTestHoles). */
    private static final int HOLES_START = LASERDOT_START + AutoTestLaserDot.LENGTH + 10;
    /** First tick of the mastery aim-bob steps (AutoTestAimBob). */
    private static final int AIMBOB_START = HOLES_START + AutoTestHoles.LENGTH + 10;
    /** First tick of the recoil-recovery steps (AutoTestRecoil). */
    private static final int RECOIL_START = AIMBOB_START + AutoTestAimBob.LENGTH + 10;
    /** First tick of the creative-tab check (AutoTestTabs). */
    private static final int TABS_START = RECOIL_START + AutoTestRecoil.LENGTH + 10;
    /** First tick of the fragment-ricochet check (AutoTestFragments). */
    private static final int FRAGMENTS_START = TABS_START + AutoTestTabs.LENGTH + 10;
    /** First tick of the tear gas steps (AutoTestTearGas). */
    private static final int TEARGAS_START = FRAGMENTS_START + AutoTestFragments.LENGTH + 10;
    /** First tick of the thermite fire steps (AutoTestThermite). */
    private static final int THERMITE_START = TEARGAS_START + AutoTestTearGas.LENGTH + 10;
    /** First tick of the hip-fire mastery check (AutoTestHipSpread). */
    private static final int HIPSPREAD_START = THERMITE_START + AutoTestThermite.LENGTH + 10;
    /** First tick of the attachment-fit check (AutoTestAttachFit). */
    private static final int ATTFIT_START = HIPSPREAD_START + AutoTestHipSpread.LENGTH + 10;
    /** First tick of the point-blank headshot check (AutoTestHeadshot). */
    private static final int HEADSHOT_START = ATTFIT_START + AutoTestAttachFit.LENGTH + 10;
    /** First tick of the flashbang exposure check (AutoTestFlash). */
    private static final int FLASH_START = HEADSHOT_START + AutoTestHeadshot.LENGTH + 10;
    /** First tick of the overhand throw-distance check (AutoTestThrowDistance). */
    private static final int THROWDIST_START = FLASH_START + AutoTestFlash.LENGTH + 10;
    /** First tick of the mob senses check (AutoTestMobSenses). */
    private static final int MOBSENSES_START = THROWDIST_START + AutoTestThrowDistance.LENGTH + 10;
    /** First tick of the body armour check (AutoTestKevlar). */
    private static final int KEVLAR_START = MOBSENSES_START + AutoTestMobSenses.LENGTH + 10;
    /** First tick of the frag lethality check (AutoTestFragLethal). */
    private static final int FRAGLETHAL_START = KEVLAR_START + AutoTestKevlar.LENGTH + 10;
    /** First tick of the iron-sight folding photos (AutoTestIronSights). */
    private static final int IRONSIGHTS_START = FRAGLETHAL_START + AutoTestFragLethal.LENGTH + 10;
    /** First tick of the gun carry check (AutoTestGunCarry). */
    private static final int GUNCARRY_START = IRONSIGHTS_START + AutoTestIronSights.LENGTH + 10;

    static final java.util.List<AutoTest.Section> SECTIONS = java.util.List.of(
            new AutoTest.Section("range", 20, 360, () -> {}),                 // FP idle/aim/fire/reload, third person, impacts
            new AutoTest.Section("auto", 360, 405, () -> withRifles(false)),  // M4A1 full auto
            new AutoTest.Section("m2010", 405, 600, () -> { withRifles(true); cmd("forceload add 0 140"); }), // scope, mils, eye box
            new AutoTest.Section("attachments", 600, 716, AutoTestCore::baseSetup), // aiming through each optic
            new AutoTest.Section("gunsmith", 716, 770, () -> { baseSetup(); giveLoadouts(); }),
            new AutoTest.Section("mastery", 770, 824, AutoTestCore::baseSetup),
            new AutoTest.Section("enderman", 824, 861, () -> { baseSetup(); cmd("clear @s"); cmd("give @s modernarmory:m4"); }),
            new AutoTest.Section("ui", 861, 880, AutoTestCore::baseSetup),     // inventory mastery tab, mastery screen
            new AutoTest.Section("f5aim", 880, 912, AutoTestCore::baseSetup),  // scoped rifle aimed in third person
            new AutoTest.Section("tracers", 912, 996, AutoTestCore::baseSetup), // tracer ammo, tracer from the muzzle
            new AutoTest.Section("crosshair", 998, 1026, AutoTestCore::baseSetup), // settings, crosshair editor, custom crosshair
            new AutoTest.Section("grenades", 1030, 1550, AutoTestCore::baseSetup), // M67, M84, M18, AN-M14
            new AutoTest.Section("drones", DRONES_START, DRONES_START + AutoTestDrones.LENGTH, AutoTestCore::baseSetup), // scout + FPV drones
            new AutoTest.Section("hitreg", HITREG_START, HITREG_START + AutoTestHitreg.LENGTH, AutoTestCore::baseSetup), // moving target, lag compensation
            new AutoTest.Section("tpwalk", POSE_START, POSE_START + AutoTestPose.LENGTH, AutoTestCore::baseSetup), // third-person pose strafing
            new AutoTest.Section("nadeflight", NADEFLIGHT_START, NADEFLIGHT_START + AutoTestGrenadeFlight.LENGTH, AutoTestCore::baseSetup), // grenade filmed in flight
            new AutoTest.Section("laser", LASER_START, LASER_START + AutoTestLaser.LENGTH, AutoTestCore::baseSetup), // laser turret
            new AutoTest.Section("laserdot", LASERDOT_START, LASERDOT_START + AutoTestLaserDot.LENGTH, AutoTestCore::baseSetup), // PEQ-15 dot
            new AutoTest.Section("holes", HOLES_START, HOLES_START + AutoTestHoles.LENGTH, AutoTestCore::baseSetup), // bullet holes
            new AutoTest.Section("aimbob", AIMBOB_START, AIMBOB_START + AutoTestAimBob.LENGTH, AutoTestCore::baseSetup), // mastery aim bob
            new AutoTest.Section("recoil", RECOIL_START, RECOIL_START + AutoTestRecoil.LENGTH, AutoTestCore::baseSetup), // recoil recovery
            new AutoTest.Section("tabs", TABS_START, TABS_START + AutoTestTabs.LENGTH, AutoTestCore::baseSetup), // creative tabs
            new AutoTest.Section("fragments", FRAGMENTS_START, FRAGMENTS_START + AutoTestFragments.LENGTH, AutoTestCore::baseSetup), // fragment ricochet
            new AutoTest.Section("teargas", TEARGAS_START, TEARGAS_START + AutoTestTearGas.LENGTH, AutoTestCore::baseSetup), // M7A3 tear gas
            new AutoTest.Section("thermite", THERMITE_START, THERMITE_START + AutoTestThermite.LENGTH, AutoTestCore::baseSetup), // AN-M14 thermite fire
            new AutoTest.Section("hipspread", HIPSPREAD_START, HIPSPREAD_START + AutoTestHipSpread.LENGTH, AutoTestCore::baseSetup), // mastery hip-fire spread
            new AutoTest.Section("attfit", ATTFIT_START, ATTFIT_START + AutoTestAttachFit.LENGTH, AutoTestCore::baseSetup), // attachment fit
            new AutoTest.Section("headshot", HEADSHOT_START, HEADSHOT_START + AutoTestHeadshot.LENGTH, AutoTestCore::baseSetup), // point-blank headshots
            new AutoTest.Section("flash", FLASH_START, FLASH_START + AutoTestFlash.LENGTH, AutoTestCore::baseSetup), // flashbang exposure
            new AutoTest.Section("throwdist", THROWDIST_START, THROWDIST_START + AutoTestThrowDistance.LENGTH, AutoTestCore::baseSetup), // overhand throw distance
            new AutoTest.Section("mobsenses", MOBSENSES_START, MOBSENSES_START + AutoTestMobSenses.LENGTH, AutoTestCore::baseSetup), // smoke and flash on mobs
            new AutoTest.Section("kevlar", KEVLAR_START, KEVLAR_START + AutoTestKevlar.LENGTH, AutoTestCore::baseSetup), // body armour
            new AutoTest.Section("fraglethal", FRAGLETHAL_START, FRAGLETHAL_START + AutoTestFragLethal.LENGTH, AutoTestCore::baseSetup), // frag lethality
            new AutoTest.Section("ironsights", IRONSIGHTS_START, IRONSIGHTS_START + AutoTestIronSights.LENGTH, AutoTestCore::baseSetup), // iron sights under optics
            new AutoTest.Section("guncarry", GUNCARRY_START, GUNCARRY_START + AutoTestGunCarry.LENGTH, AutoTestCore::baseSetup)); // gun carry limit

    /** Bullets the client is simulating right now (tracer test). */
    private static void logBullets() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (var e : mc().level.entitiesForRendering()) {
            if (!(e instanceof com.modernarmory.entity.BulletEntity b)) continue;
            if (n++ < 4) sb.append(String.format(" [%.0f,%.0f,%.0f flown %.0f glow %06x off %s]", b.getX(), b.getY(), b.getZ(), b.distance(), b.tracerGlow() & 0xFFFFFF, b.tracerOffset()));
        }
        ModernArmory.LOGGER.info("[autotest] bullets in flight: {}{}", n, sb);
    }

    /** Thrown grenades the client sees: count, where, and whether they are burning. */
    private static void logGrenades(String when) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (var e : mc().level.entitiesForRendering()) {
            if (!(e instanceof com.modernarmory.grenade.GrenadeEntity g)) continue;
            n++;
            var t = g.tumble();
            sb.append(String.format(" [%s at %.1f %.1f %.1f burn %d yaw %.0f roll %.0f lean %.0f]", g.type().id(), g.getX(), g.getY(), g.getZ(),
                    g.burnTicks(), t.yaw, t.roll, t.lean));
        }
        ModernArmory.LOGGER.info("[autotest] grenades {}: {}{}", when, n, sb);
    }

    /** Zombies (test targets): health and whether they are burning. */
    private static void logZombies(String when) {
        var server = mc().getSingleplayerServer();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (var z : server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.HUSK, e -> true)) {
            n++;
            sb.append(String.format(" [health %.1f fire %d]", ((net.minecraft.world.entity.LivingEntity) z).getHealth(), z.getRemainingFireTicks()));
        }
        ModernArmory.LOGGER.info("[autotest] zombies {}: {} alive{}", when, n, sb);
    }
}
