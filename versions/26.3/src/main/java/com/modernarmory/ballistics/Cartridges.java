package com.modernarmory.ballistics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every cartridge in the mod, with real-world data (sources: docs/firearms/specs.json and
 * Wikipedia "5.56x45mm NATO" for the BRL-measured G7 BC). Each one gets an ammo item,
 * registered by {@code ModItems} from this catalog in declaration order (= creative tab order:
 * pistol, rifle, magnum, .50, then shotgun shells). Its {@link CaliberClass} decides which
 * calibre-specific attachments fit.
 */
public final class Cartridges {
    private static final Map<String, Cartridge> BY_ID = new LinkedHashMap<>();

    /**
     * 9x19mm NATO M882 ball: 124 gr (8.04 g) FMJ round nose, 375 m/s from the M9's 4.9 in barrel.
     * G7 ~0.075 (G1 ~0.15 converted, estimate); ~5.5 gr powder (estimate). Does not fragment.
     */
    public static final Cartridge NATO_9X19 = add(Cartridge.builder("9x19", "9x19mm NATO (M882)")
            .caliber(CaliberClass.PISTOL_9MM).bullet(0.00804, 0.075).powder(0.00036)
            .stoppedBy(ArmorLevel.IIIA)); // IIIA soft armour stops it

    /**
     * .50 Action Express (Desert Eagle): 300 gr (19.4 g) JHP, ~450 m/s from the 6 in barrel. G7 ~0.085
     * (G1 ~0.17 converted, estimate); ~30 gr (1.9 g) of powder (estimate). Expands, does not fragment.
     */
    public static final Cartridge AE_50 = add(Cartridge.builder("50ae", ".50 Action Express (300 gr JHP)")
            .caliber(CaliberClass.MAGNUM_PISTOL).bullet(0.0194, 0.085).powder(0.0019)
            .stoppedBy(ArmorLevel.III)); // heavier than the .44 Magnum IIIA is tested against: goes through soft armour, a rifle plate stops it

    /**
     * .44 Remington Magnum: 240 gr (15.6 g) JSP, ~440 m/s from a 6 in revolver barrel. G7 ~0.08 (G1 ~0.16
     * converted, estimate); ~22 gr (1.4 g) of powder (estimate).
     */
    public static final Cartridge MAG_44 = add(Cartridge.builder("44mag", ".44 Magnum (240 gr JSP)")
            .caliber(CaliberClass.MAGNUM_PISTOL).bullet(0.0156, 0.08).powder(0.0014)
            .stoppedBy(ArmorLevel.IIIA)); // the top IIIA test round: soft armour stops it

    /**
     * .45 ACP M1911 ball: 230 gr (14.9 g) FMJ round nose, ~260 m/s from a pistol barrel. G7 ~0.08 (G1 ~0.16
     * converted, estimate); ~5 gr (0.32 g) of powder. Subsonic; does not fragment. Same pistol class as 9 mm
     * (a .45 suppressor fits both).
     */
    public static final Cartridge ACP_45 = add(Cartridge.builder("45acp", ".45 ACP (230 gr FMJ)")
            .caliber(CaliberClass.PISTOL_9MM).bullet(0.0149, 0.08).powder(0.00032)
            .stoppedBy(ArmorLevel.IIIA)); // IIIA soft armour stops it

    /**
     * 5.56x45mm NATO M855 (SS109) ball: 62 gr (4.02 g) FMJBT with a steel penetrator tip,
     * G7 BC 0.151, ~25 gr (1.62 g) of powder. Fragments reliably above ~762 m/s (2,500 ft/s).
     */
    public static final Cartridge M855 = add(Cartridge.builder("556x45", "5.56x45mm NATO M855")
            .ammoItem("ammo_556").caliber(CaliberClass.RIFLE_556).bullet(0.00402, 0.151).powder(0.00162).fragmentsAbove(762.0)
            // M856 tracer: red tip, burns out ~800 m
            .tracer("M856", "5.56x45mm NATO M856 Tracer", 0xFF4A2A, 800)
            .stoppedBy(ArmorLevel.IV)); // its steel penetrator defeats many Level III plates; Level IV stops it

    /**
     * 7.62x39mm M43 (57-N-231): 123 gr (7.9 g) FMJ with a mild-steel core, ~715 m/s from the AK's 16.3 in
     * barrel. G7 ~0.14 (G1 ~0.28 converted, estimate); ~25 gr (1.6 g) of powder. Does not fragment.
     */
    public static final Cartridge M43 = add(Cartridge.builder("762x39", "7.62x39mm M43")
            .caliber(CaliberClass.RIFLE_30).bullet(0.0079, 0.14).powder(0.0016)
            .stoppedBy(ArmorLevel.III)); // mild-steel core ball: Level III stops it

    /**
     * 6.8x51mm Common Cartridge XM1186 General Purpose (NGSW, .277 SIG Fury hybrid case, 80,000 psi):
     * 121 gr (7.84 g) EPR-style bullet with a steel penetrator, ~915 m/s from the M7's 13 in barrel.
     * G7 ~0.215 and ~46 gr powder are estimates; yaws / breaks up above ~600 m/s (EPR estimate).
     */
    public static final Cartridge CC_68X51 = add(Cartridge.builder("68x51", "6.8x51mm Common Cartridge (XM1186)")
            .caliber(CaliberClass.RIFLE_30).bullet(0.00784, 0.215).powder(0.0030).fragmentsAbove(600.0)
            .stoppedBy(ArmorLevel.IV)); // built to defeat body armour: through Level III; Level IV stops it

    /**
     * 7.62x51mm NATO M80A1 EPR: 130 gr (8.42 g) lead-free bullet with a steel penetrator tip,
     * 930 m/s (22 in test barrel assumed). G7 ~0.195 (estimate; TFB used 0.185), ~45 gr powder
     * (estimate). Fully fragments down to ~588 m/s (Wound Channel / TFB test).
     */
    public static final Cartridge M80A1 = add(Cartridge.builder("762x51", "7.62x51mm NATO (M80A1 EPR)")
            .caliber(CaliberClass.RIFLE_30).bullet(0.00842, 0.195).powder(0.0029).fragmentsAbove(580.0)
            // M62 tracer: orange tip, burns out ~900 m
            .tracer("M62", "7.62x51mm NATO M62 Tracer", 0xFF7A28, 900)
            .stoppedBy(ArmorLevel.IV)); // steel-tipped EPR: through Level III; Level IV stops it

    /**
     * 7.62x51mm NATO M118LR: 175 gr (11.34 g) Sierra MatchKing OTM boat-tail, G7 0.243 (Litz /
     * Applied Ballistics), 783 m/s from the M110's 20 in barrel; ~43 gr powder (estimate). Match
     * bullets don't fragment reliably.
     */
    public static final Cartridge M118LR = add(Cartridge.builder("762_m118lr", "7.62x51mm NATO (M118LR)")
            .caliber(CaliberClass.RIFLE_30).bullet(0.01134, 0.243).powder(0.0028)
            .stoppedBy(ArmorLevel.IV)); // match bullet at full power: through Level III; Level IV stops it

    /**
     * .300 Winchester Magnum Mk 248 Mod 1 (the M2010's match load): 220 gr (14.26 g) Sierra
     * MatchKing OTM boat-tail at a nominal 869 m/s (2,850 ft/s). G7 BC ~0.31, typical of a 220 gr
     * .30-cal boat-tail (no published figure for this exact load). Powder ~72 gr (4.67 g, estimate).
     * Match bullets don't fragment reliably, so no fragmentation bonus.
     */
    public static final Cartridge MK248 = add(Cartridge.builder("300wm", ".300 Win Mag Mk 248 Mod 1")
            .ammoItem("ammo_300wm").caliber(CaliberClass.RIFLE_30).bullet(0.01426, 0.31).powder(0.00467)
            .stoppedBy(ArmorLevel.IV)); // .300 Win Mag match: through Level III; Level IV stops it

    /**
     * .338 Lapua Magnum, Lapua 250 gr (16.2 g) Scenar OTM: G7 0.322 (Lapua doppler), 905 m/s from a
     * 27.15 in test barrel; ~94 gr powder (estimate). Match bullet, no fragmentation.
     */
    public static final Cartridge LAPUA_338 = add(Cartridge.builder("338lm", ".338 Lapua Magnum (250 gr Scenar)")
            .caliber(CaliberClass.MAGNUM_338).bullet(0.0162, 0.322).powder(0.0061)
            .stoppedBy(ArmorLevel.JUGGERNAUT)); // over 6 kJ: through Level IV; only the juggernaut suit stops it

    /**
     * .338 Norma Magnum, 300 gr (19.44 g) OTM (M1162 class; the Army load's specs are unpublished):
     * G7 0.384 (Norma / Sierra MatchKing data), ~810-823 m/s; ~86 gr powder (estimate).
     */
    public static final Cartridge NORMA_338 = add(Cartridge.builder("338nm", ".338 Norma Magnum (300 gr OTM)")
            .caliber(CaliberClass.MAGNUM_338).bullet(0.01944, 0.384).powder(0.0056)
            .stoppedBy(ArmorLevel.JUGGERNAUT)); // over 6 kJ: through Level IV; only the juggernaut suit stops it

    /**
     * .50 BMG (12.7x99mm NATO) M33 ball: 661 gr (42.8 g) FMJBT, mild-steel core, 887 m/s from the
     * M2's 45 in barrel. G7 ~0.32 (G1 ~0.62-0.67 converted, estimate); ~235 gr powder (estimate).
     */
    public static final Cartridge BMG_50 = add(Cartridge.builder("50bmg", ".50 BMG (M33 Ball)")
            .caliber(CaliberClass.BMG_50).bullet(0.0428, 0.32).powder(0.0153)
            // M17 tracer: red tip, burns to ~1,600 m
            .tracer("M17", ".50 BMG M17 Tracer", 0xFF3A20, 1600)
            .stoppedBy(ArmorLevel.NOTHING)); // nothing wearable stops it

    /**
     * 12 gauge 2-3/4 in 00 buckshot (M162 / military 9-pellet): 9 x 0.33 in lead pellets of 53.8 gr
     * (3.49 g), 31.4 g payload, 404 m/s nominal (long test barrel). Pellet G7 ~0.022 (estimate from
     * a 0.33 in sphere's G1 0.04-0.05); ~27 gr powder (estimate). Pattern ~95 MOA full diameter
     * from a cylinder bore (about 1 in per yard).
     */
    public static final Cartridge BUCK_00 = add(Cartridge.builder("12ga_00", "12 Gauge 00 Buckshot")
            .caliber(CaliberClass.GAUGE_12).shot(9, 0.00349, 0.022, 95).powder(0.0018)
            .stoppedBy(ArmorLevel.IIIA)); // IIIA soft armour stops every pellet

    private static Cartridge add(Cartridge.Builder b) {
        Cartridge c = b.build();
        if (BY_ID.putIfAbsent(c.id(), c) != null) throw new IllegalStateException("duplicate cartridge id " + c.id());
        return c;
    }

    /** All cartridges, in declaration order. */
    public static Collection<Cartridge> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Lookup by catalog id (e.g. "556x45"), or null. */
    public static Cartridge byId(String id) {
        return BY_ID.get(id);
    }

    private Cartridges() {}
}
