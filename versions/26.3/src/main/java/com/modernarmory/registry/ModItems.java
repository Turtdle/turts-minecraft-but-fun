package com.modernarmory.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentItem;
import com.modernarmory.attachment.Attachments;
import com.modernarmory.ballistics.Cartridge;
import com.modernarmory.ballistics.Cartridges;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;
import com.modernarmory.item.AmmoItem;
import com.modernarmory.item.GunItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items, all registered from the catalogs: one per gun (Guns), cartridge (Cartridges) and attachment (Attachments). */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernArmory.MODID);

    /** Ammo items by cartridge id, in catalog order. */
    private static final Map<String, DeferredItem<AmmoItem>> AMMO = new LinkedHashMap<>();
    /** Tracer ammo by cartridge id, for cartridges that have a tracer variant. */
    private static final Map<String, DeferredItem<AmmoItem>> TRACER_AMMO = new LinkedHashMap<>();
    /** Gun items by gun id, in catalog order. */
    private static final Map<String, DeferredItem<GunItem>> GUNS = new LinkedHashMap<>();
    /** Drones by id, in catalog order. */
    private static final Map<String, DeferredItem<com.modernarmory.drone.DroneItem>> DRONES = new LinkedHashMap<>();
    /** Hand grenades by id, in catalog order. */
    private static final Map<String, DeferredItem<com.modernarmory.grenade.GrenadeItem>> GRENADES = new LinkedHashMap<>();
    /** One item per attachment, registered under the attachment's id. */
    public static final Map<String, DeferredItem<AttachmentItem>> ATTACHMENTS = new LinkedHashMap<>();

    // 26.x: registerItem creates the Item.Properties with the item id already set.
    static {
        for (Cartridge c : Cartridges.all()) {
            AMMO.put(c.id(), ITEMS.registerItem(c.ammoItemId(), p -> new AmmoItem(c, p), p -> p.stacksTo(64)));
            if (c.hasTracer()) TRACER_AMMO.put(c.id(), ITEMS.registerItem(c.tracer().ammoItemId(),
                    p -> new AmmoItem(c, true, p), p -> p.stacksTo(64)));
        }
        for (GunDefinition g : Guns.all()) {
            GUNS.put(g.id(), ITEMS.registerItem(g.id(), p -> new GunItem(g, p), p -> p.stacksTo(1)));
        }
        for (com.modernarmory.grenade.GrenadeType t : com.modernarmory.grenade.Grenades.all()) {
            GRENADES.put(t.id(), ITEMS.registerItem(t.id(), p -> new com.modernarmory.grenade.GrenadeItem(t, p), p -> p.stacksTo(16)));
        }
        for (com.modernarmory.drone.DroneType t : com.modernarmory.drone.Drones.all()) {
            DRONES.put(t.id(), ITEMS.registerItem(t.id(), p -> new com.modernarmory.drone.DroneItem(t, p), p -> p.stacksTo(1)));
        }
        for (Attachment a : Attachments.ALL.values()) {
            ATTACHMENTS.put(a.id(), ITEMS.registerItem(a.id(), p -> new AttachmentItem(a, p), p -> p.stacksTo(1)));
        }
    }

    public static final DeferredItem<BlockItem> GUNSMITH_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.GUNSMITH_TABLE);

    /** Steel: an iron ingot refined in the vanilla Blast Furnace (slow: 30 s per ingot). */
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");

    // ---- body armour (armor.Kevlar) and what it is made of
    /** Aramid fabric: woven para-aramid (Kevlar) fibre, the yellow cloth soft armour is layered from. */
    public static final DeferredItem<Item> ARAMID_FABRIC = ITEMS.registerSimpleItem("aramid_fabric");
    /** Level III rifle plate: a hardened steel / polyethylene trauma plate. */
    public static final DeferredItem<Item> RIFLE_PLATE = ITEMS.registerSimpleItem("rifle_plate");
    /** Level IV ceramic plate (ESAPI class): a ceramic strike face on a polyethylene backer. */
    public static final DeferredItem<Item> CERAMIC_PLATE = ITEMS.registerSimpleItem("ceramic_plate");
    /** Body armour items by id, tier by tier. */
    private static final Map<String, DeferredItem<com.modernarmory.armor.KevlarItem>> KEVLAR = new LinkedHashMap<>();

    static {
        for (com.modernarmory.armor.ArmorPiece p : com.modernarmory.armor.Kevlar.all())
            KEVLAR.put(p.id(), ITEMS.registerItem(p.id(), props -> new com.modernarmory.armor.KevlarItem(p, props), props -> props));
    }

    /** Body armour items by id, tier by tier. */
    public static Map<String, DeferredItem<com.modernarmory.armor.KevlarItem>> kevlar() {
        return KEVLAR;
    }

    /** The item for a catalog gun. */
    public static GunItem gun(GunDefinition def) {
        return GUNS.get(def.id()).get();
    }

    /** The ammo item of a cartridge. */
    public static AmmoItem ammo(Cartridge c) {
        DeferredItem<AmmoItem> item = AMMO.get(c.id());
        if (item == null) throw new IllegalArgumentException("cartridge " + c.id() + " is not in the Cartridges catalog");
        return item.get();
    }

    public static Map<String, DeferredItem<GunItem>> guns() {
        return Collections.unmodifiableMap(GUNS);
    }

    /** Drone Relay Antenna, placed like any block. */

    public static final DeferredItem<BlockItem> DRONE_RELAY = ITEMS.registerSimpleBlockItem(ModBlocks.DRONE_RELAY);

    /** HELWS laser turret: places the whole emplacement (laser.LaserTurretItem). */
    public static final DeferredItem<com.modernarmory.laser.LaserTurretItem> LASER_TURRET = ITEMS.registerItem("laser_turret",
            com.modernarmory.laser.LaserTurretItem::new, p -> p.stacksTo(1));

    /** Handheld ground control station: flies any drone it is paired with. */
    public static final DeferredItem<com.modernarmory.drone.DroneControllerItem> DRONE_CONTROLLER = ITEMS.registerItem("drone_controller",
            com.modernarmory.drone.DroneControllerItem::new, p -> p.stacksTo(1));

    /** The item of a drone type. */
    public static com.modernarmory.drone.DroneItem drone(com.modernarmory.drone.DroneType t) {
        return DRONES.get(t.id()).get();
    }

    public static Map<String, DeferredItem<com.modernarmory.drone.DroneItem>> drones() {
        return Collections.unmodifiableMap(DRONES);
    }

    /** The item of a grenade type, or null before registration. */
    public static com.modernarmory.grenade.GrenadeItem grenade(com.modernarmory.grenade.GrenadeType t) {
        var item = GRENADES.get(t.id());
        return item == null ? null : item.get();
    }

    public static Map<String, DeferredItem<com.modernarmory.grenade.GrenadeItem>> grenades() {
        return Collections.unmodifiableMap(GRENADES);
    }

    public static Map<String, DeferredItem<AmmoItem>> ammo() {
        return Collections.unmodifiableMap(AMMO);
    }

    /** The tracer ammo item of a cartridge, or null if it has none. */
    public static AmmoItem tracerAmmo(Cartridge c) {
        DeferredItem<AmmoItem> item = TRACER_AMMO.get(c.id());
        return item == null ? null : item.get();
    }

    public static Map<String, DeferredItem<AmmoItem>> tracerAmmo() {
        return Collections.unmodifiableMap(TRACER_AMMO);
    }

    private ModItems() {}
}
