package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeEntity;
import com.modernarmory.gun.GunCarry;
import com.modernarmory.item.GunItem;
import com.modernarmory.network.GunActionPayload;
import com.modernarmory.server.GunHandler;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Multiplayer scenario ("mp"): the test client joins a DEDICATED server (runServer, offline mode, the test player
 * opped; see the autotestMp run) instead of making a world, and checks everything through what reaches a client over
 * the network: shots hurting a villager (synced health), mastery XP coming back (MasterySyncPayload), the magazine
 * emptying and refilling (item data), a thrown grenade appearing (entity spawn), a flashbang whiting out the screen
 * (FlashbangPayload), body armour worn (equipment), the gun limit and the heavy-load slowdown (inventory, attributes).
 * One PASS/FAIL line per check, then a summary.
 */
final class AutoTestMultiplayer {
    private static final List<String> results = new ArrayList<>();
    private static int ammoBefore, ammoFired;
    private static float flashMax;
    private static boolean grenadeSeen;

    static void script() {
        Minecraft mc = Minecraft.getInstance();
        at(20, () -> check("joined a dedicated server", mc.getSingleplayerServer() == null && mc.getConnection() != null,
                "integrated server " + (mc.getSingleplayerServer() != null)));
        at(22, () -> {
            cmd("gamemode survival"); cmd("clear @s"); cmd("kill @e[type=!player]");
            cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s saturation 99999 1 true");
            cmd("fill -8 -60 -14 8 -54 6 minecraft:air"); cmd("fill -8 -61 -14 8 -61 6 minecraft:stone");
            cmd("tp @s 0.5 -60 0.5 180 0"); cmd("time set noon"); cmd("gunmastery set @s m4 0");
        });
        at(26, () -> {
            cmd("give @s modernarmory:m4"); cmd("give @s modernarmory:ammo_556 64");
            cmd("summon villager 0.5 -60 -4.5 {NoAI:1b}");
        });
        // 1) shoot the villager
        at(34, () -> { select(0); look(180, 8); ammoBefore = ammo(); });
        at(36, () -> mc.options.keyAttack.setDown(true));
        at(44, () -> mc.options.keyAttack.setDown(false));
        at(60, () -> {
            LivingEntity v = villager();
            check("shots hurt the villager (server hit, synced health)", v == null || !v.isAlive() || v.getHealth() < 20,
                    v == null ? "villager gone (killed)" : "health " + v.getHealth());
            ammoFired = ammo();
            check("magazine emptied by firing", ammoFired < ammoBefore, ammoBefore + " -> " + ammoFired);
            int xp = ClientMastery.data().xp("m4");
            check("mastery XP synced back", xp > 0, "m4 xp " + xp);
        });
        // 2) reload
        at(62, () -> PacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD)));
        at(130, () -> check("reload refilled the magazine", ammo() > ammoFired, ammoFired + " -> " + ammo()));
        // 3) a frag grenade, thrown overhand
        at(132, () -> { cmd("kill @e[type=villager]"); cmd("give @s modernarmory:grenade_m67"); });
        at(136, () -> { select(2); look(180, -10); });
        at(138, () -> mc.options.keyAttack.setDown(true));
        at(150, () -> mc.options.keyAttack.setDown(false));
        for (int t = 152; t < 175; t++) at(t, () -> grenadeSeen |= !mc.level.getEntitiesOfClass(GrenadeEntity.class,
                mc.player.getBoundingBox().inflate(40)).isEmpty());
        at(176, () -> check("thrown grenade seen by the client", grenadeSeen, ""));
        // 4) a flashbang at your feet, looking at it
        at(240, () -> { cmd("clear @s modernarmory:grenade_m67"); cmd("give @s modernarmory:grenade_m84"); });
        at(244, () -> { select(2); look(180, 60); });
        at(246, () -> mc.options.keyUse.setDown(true));
        at(256, () -> mc.options.keyUse.setDown(false));
        for (int t = 256; t < 330; t++) at(t, () -> flashMax = Math.max(flashMax, FlashbangOverlay.current()));
        at(332, () -> check("flashbang whited the screen out (server packet)", flashMax > 0.9f, "peak " + flashMax));
        // 5) armour, gun limit, heavy load
        at(334, () -> { cmd("clear @s"); cmd("item replace entity @s armor.chest with modernarmory:iotv");
            cmd("item replace entity @s armor.head with modernarmory:ech_helmet"); });
        at(338, () -> { cmd("give @s modernarmory:m4"); cmd("give @s modernarmory:m590"); cmd("give @s modernarmory:glock17"); });
        at(370, () -> {
            check("IOTV worn (equipment synced)", mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    instanceof com.modernarmory.armor.KevlarWearable, "");
            int n = GunCarry.count(mc.player);
            check("heavy vest carries 3 guns", n == 3, n + " guns");
            check("two long guns in a heavy vest slow you (synced attribute)", GunCarry.loaded(mc.player), "");
        });
        at(372, () -> { select(0); look(180, 0); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); });
        at(385, () -> shot("mp_third_person"));
        at(387, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); cmd("item replace entity @s armor.chest with air"); });
        at(420, () -> {
            int n = GunCarry.count(mc.player);
            check("vest off: back to 2 guns (server dropped one)", n == 2, n + " guns");
            check("vest off: no slowdown", !GunCarry.loaded(mc.player), "");
        });
        at(422, () -> {
            long passed = results.stream().filter(r -> r.endsWith("PASS")).count();
            ModernArmory.LOGGER.info("[autotest] mp: {} of {} checks -> {}", passed, results.size(), passed == results.size() ? "PASS" : "FAIL");
            cmd("clear @s"); cmd("kill @e[type=item]"); cmd("effect clear @s"); cmd("gamemode creative");
        });
        at(426, AutoTest::finish);
    }

    private static void check(String what, boolean ok, String detail) {
        String line = what + (detail.isEmpty() ? "" : " (" + detail + ")") + " -> " + (ok ? "PASS" : "FAIL");
        results.add(line);
        ModernArmory.LOGGER.info("[autotest] mp {}", line);
    }

    private static LivingEntity villager() {
        var mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(-8, -62, -14, 8, -54, 6),
                e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("villager")).stream().findFirst().orElse(null);
    }

    private static int ammo() {
        var s = Minecraft.getInstance().player.getMainHandItem();
        return s.getItem() instanceof GunItem g ? g.ammo(s) : -1;
    }

    private static void select(int slot) {
        Minecraft.getInstance().player.getInventory().selected = slot;
    }

    private AutoTestMultiplayer() {}
}
