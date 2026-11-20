package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.buildRange;
import static com.modernarmory.client.AutoTest.cameraFromStand;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;
import static com.modernarmory.client.AutoTest.setHudHidden;
import static com.modernarmory.client.AutoTest.shot;

import java.util.List;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.gun.FireMode;
import com.modernarmory.gun.Feed;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.GunSpec;
import com.modernarmory.gun.Guns;
import com.modernarmory.item.GunItem;
import com.modernarmory.network.GunActionPayload;
import com.modernarmory.server.GunHandler;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Autotest scenario "tour": the same five shots for every listed gun, in survival with real ammo
 * (timings derived from each gun's spec, so any catalog gun works without editing this file):
 * <ol>
 *   <li>{@code autotest_tour_<gun>_1_hip}: first person, hip</li>
 *   <li>{@code _2_aim}: aiming (iron sights / ADS / scoped)</li>
 *   <li>{@code _3_fire}: 3 rounds fired aimed at the 20 m wall, then lowered to the hip (taken
 *       once the gun's ADS time has passed) so the impacts (bullet holes / pellet pattern dust)
 *       are not hidden behind the sights</li>
 *   <li>{@code _4_reload}: halfway through the (first step of the) reload</li>
 *   <li>{@code _5_third_person_side}: third person from the side camera, head and body levelled</li>
 * </ol>
 * Logs {@code [autotest] tour <gun>: ...} with the rounds loaded before / after firing and after the reload.
 */
final class AutoTestTour {
    private static final int SHOTS = 3;

    static void script(List<String> gunIds) {
        at(20, () -> {
            buildRange();
            cmd("clear @s");
        });
        int t = 60;
        for (String id : gunIds) {
            GunDefinition def = Guns.byId(id);
            if (def == null) {
                at(t, () -> ModernArmory.LOGGER.error("[autotest] tour: unknown gun '{}'", id));
                continue;
            }
            t = gun(def, t) + 5;
        }
        at(t, () -> {
            cmd("gamemode creative");
            AutoTest.finish();
        });
    }

    private static int loaded() {
        ItemStack st = Minecraft.getInstance().player.getMainHandItem();
        return st.getItem() instanceof GunItem g ? g.ammo(st) : -1;
    }

    /** Schedules one gun's tour starting at tick {@code t0}; returns the tick it ends on. */
    private static int gun(GunDefinition def, int t0) {
        GunSpec spec = def.spec();
        String id = def.id(), name = "tour_" + id + "_";
        int cycleTicks = (int) Math.ceil(spec.cycleTime() * 20);
        int gap = Math.max(3, cycleTicks + 4);                   // between trigger pulls (a pull during the cycle is ignored)
        FireMode mode = spec.fireModes().get(spec.defaultFireModeIndex());
        int hold = mode == FireMode.BURST                          // one pull = one burst
                ? (int) Math.ceil(spec.burstCount() * spec.cycleTime() * 20) + 1
                : mode.trigger() == FireMode.Trigger.SINGLE ? 2   // one round per pull: a 1-tick tap can reach the server as press + release in one tick and be lost
                : 1;
        int ammo = spec.magazineSize() * 2 + 10;
        int[] before = new int[1];

        at(t0, () -> {
            cmd("clear @s");
            cmd("gamemode survival");
            cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("give @s modernarmory:" + id);
            cmd("give @s modernarmory:" + spec.cartridge().ammoItemId() + " " + ammo);
            mc().setCameraEntity(mc().player);
            setHudHidden(false);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().options.fov().set(70);
        });
        at(t0 + 6, () -> { mc().player.getInventory().setSelectedSlot(0); look(180, 0); });
        at(t0 + 12, () -> mc().gui.hud.getChat().clearMessages(false));
        at(t0 + 20, () -> shot(name + "1_hip"));
        at(t0 + 22, () -> mc().options.keyUse.setDown(true));
        at(t0 + 42, () -> { shot(name + "2_aim"); before[0] = loaded(); });
        int fire = t0 + 44;
        for (int i = 0; i < SHOTS; i++) {
            int p = fire + i * (gap + hold - 1);
            at(p, () -> mc().options.keyAttack.setDown(true));
            at(p + hold, () -> mc().options.keyAttack.setDown(false));
        }
        int last = fire + (SHOTS - 1) * (gap + hold - 1) + hold - 1;
        at(last + 1, () -> mc().options.keyUse.setDown(false));
        // wait until the gun is fully back at the hip (its ADS time with the factory-fitted parts)
        int lower = (int) Math.ceil(GunStats.of(spec, new GunAttachments(spec.defaultAttachments())).adsSeconds() * 20) + 2;
        at(last + 1 + lower, () -> shot(name + "3_fire"));

        int reload = Math.max(last + Math.max(4, gap), last + 2 + lower);
        int[] fired = new int[1];
        at(reload, () -> {
            fired[0] = loaded();
            ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD));
        });
        at(reload + Math.max(2, spec.reloadTicks() / 2), () -> shot(name + "4_reload"));
        // TUBE loads one shell per reload step; whole-unit feeds take one reload
        int reloadEnd = reload + (spec.feed() == Feed.TUBE ? spec.reloadTicks() * SHOTS : spec.reloadTicks()) + 6;
        at(reloadEnd, () -> ModernArmory.LOGGER.info("[autotest] tour {}: {} feed, {} loaded, {} after firing {} pulls, {} after reload",
                id, spec.feed().name(), before[0], fired[0], SHOTS, loaded()));

        at(reloadEnd + 1, () -> {
            // level the head and body again: recoil leaves the view turned, and the third-person
            // arms follow the head
            look(180, 0);
            mc().player.setYHeadRot(180);
            mc().player.yHeadRotO = 180;
            mc().player.yBodyRot = mc().player.yBodyRotO = 180;
            cameraFromStand(e -> e.getY() < -58);
            setHudHidden(true); // also hides the first-person hand
            mc().options.fov().set(30);
        });
        at(reloadEnd + 11, () -> shot(name + "5_third_person_side"));
        at(reloadEnd + 13, () -> { mc().setCameraEntity(mc().player); setHudHidden(false); mc().options.fov().set(70); });
        return reloadEnd + 13;
    }

    private AutoTestTour() {}
}
