package com.modernarmory.armor;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.modernarmory.ballistics.ArmorLevel;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * The body-armour catalog: four tiers, each modelled on real gear and sharing one GeckoLib model.
 *
 * <ol>
 *   <li>Police / civilian (kevlar_police): concealable NIJ IIIA soft vest, IIIA ballistic helmet.</li>
 *   <li>Military light (kevlar_light): Crye JPC 2.0 plate carrier with Level III plates, Ops-Core FAST helmet.</li>
 *   <li>Military heavy (kevlar_heavy): US Army IOTV Gen IV with ESAPI (Level IV) and side plates, ECH helmet.</li>
 *   <li>Juggernaut (kevlar_eod): Med-Eng EOD 10 bomb suit with rifle plates: jacket, trousers, helmet.</li>
 * </ol>
 * Weights are the real ones (vest with plates, full suit ~36 kg); slowdowns follow them.
 */
public final class Kevlar {
    private static final Map<String, ArmorPiece> BY_ID = new LinkedHashMap<>();

    private static final EnumSet<BodyZone> HEAD = EnumSet.of(BodyZone.HEAD), TORSO = EnumSet.of(BodyZone.TORSO),
            LEGS_FEET = EnumSet.of(BodyZone.LEGS, BodyZone.FEET);

    // ---- 1. police / civilian ----------------------------------------------------------------------
    public static final ArmorPiece POLICE_VEST = add(new ArmorPiece("police_vest", "Level IIIA Concealable Vest", "kevlar_police",
            EquipmentSlot.CHEST, TORSO, ArmorLevel.IIIA, 0.30f, 2.5, 80, 3, 0f, 0.02, false,
            "Concealable aramid vest of the Point Blank / Safariland kind, NIJ 0101.06 Level IIIA", 0));
    public static final ArmorPiece POLICE_HELMET = add(new ArmorPiece("police_helmet", "Level IIIA Ballistic Helmet", "kevlar_police",
            EquipmentSlot.HEAD, HEAD, ArmorLevel.IIIA, 0.30f, 1.4, 50, 1, 0f, 0.01, false,
            "PASGT-cut aramid police helmet, Level IIIA", 0));

    // ---- 2. military light --------------------------------------------------------------------------
    public static final ArmorPiece PLATE_CARRIER = add(new ArmorPiece("plate_carrier", "JPC 2.0 Plate Carrier", "kevlar_light",
            EquipmentSlot.CHEST, TORSO, ArmorLevel.III, 0.18f, 5.5, 160, 5, 0.5f, 0.05, false,
            "Crye Precision Jumpable Plate Carrier 2.0, Level III polyethylene front and back plates", 0));
    public static final ArmorPiece FAST_HELMET = add(new ArmorPiece("fast_helmet", "Ops-Core FAST Helmet", "kevlar_light",
            EquipmentSlot.HEAD, HEAD, ArmorLevel.IIIA, 0.25f, 1.1, 60, 2, 0f, 0.01, false,
            "Ops-Core FAST SF high-cut helmet with rails and NVG shroud, Level IIIA", 0));

    // ---- 3. military heavy --------------------------------------------------------------------------
    public static final ArmorPiece IOTV = add(new ArmorPiece("iotv", "IOTV Gen IV with ESAPI Plates", "kevlar_heavy",
            EquipmentSlot.CHEST, TORSO, ArmorLevel.IV, 0.12f, 14.0, 260, 7, 1.5f, 0.12, false,
            "US Army Improved Outer Tactical Vest Gen IV: ESAPI front and back, ESBI side plates, collar and groin protector", 1));
    public static final ArmorPiece ECH = add(new ArmorPiece("ech_helmet", "Enhanced Combat Helmet", "kevlar_heavy",
            EquipmentSlot.HEAD, HEAD, ArmorLevel.III, 0.20f, 1.4, 100, 2, 1f, 0.02, false,
            "USMC / Army Enhanced Combat Helmet, polyethylene shell rated against some rifle rounds", 0));

    // ---- 4. juggernaut --------------------------------------------------------------------------------
    public static final ArmorPiece EOD_JACKET = add(new ArmorPiece("eod_jacket", "EOD 10 Bomb Suit Jacket", "kevlar_eod",
            EquipmentSlot.CHEST, TORSO, ArmorLevel.JUGGERNAUT, 0.08f, 20.0, 500, 9, 3f, 0.18, true,
            "Med-Eng EOD 10 bomb disposal suit jacket with its chest rifle plate and blast collar", 1));
    public static final ArmorPiece EOD_TROUSERS = add(new ArmorPiece("eod_trousers", "EOD 10 Bomb Suit Trousers", "kevlar_eod",
            EquipmentSlot.LEGS, LEGS_FEET, ArmorLevel.JUGGERNAUT, 0.08f, 11.0, 400, 7, 3f, 0.12, true,
            "Med-Eng EOD 10 bomb suit trousers and boot covers, blast plates down the shins", 0));
    public static final ArmorPiece EOD_HELMET = add(new ArmorPiece("eod_helmet", "EOD 10 Helmet", "kevlar_eod",
            EquipmentSlot.HEAD, HEAD, ArmorLevel.IV, 0.10f, 4.5, 300, 4, 3f, 0.05, false,
            "Med-Eng EOD 10 helmet with its thick blast visor (rifle-proof here: a game buff)", 0));

    private static ArmorPiece add(ArmorPiece p) {
        if (BY_ID.putIfAbsent(p.id(), p) != null) throw new IllegalStateException("duplicate armour id " + p.id());
        return p;
    }

    /** Every piece, tier by tier. */
    public static Collection<ArmorPiece> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Lookup by item id, or null. */
    public static ArmorPiece byId(String id) {
        return BY_ID.get(id);
    }

    private Kevlar() {}
}
