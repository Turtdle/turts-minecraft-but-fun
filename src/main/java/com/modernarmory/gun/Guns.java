package com.modernarmory.gun;

import static com.modernarmory.attachment.AttachmentType.MAGAZINE;
import static com.modernarmory.attachment.AttachmentType.MUZZLE;
import static com.modernarmory.attachment.AttachmentType.OPTIC;
import static com.modernarmory.attachment.AttachmentType.SIDE;
import static com.modernarmory.attachment.AttachmentType.STOCK;
import static com.modernarmory.attachment.AttachmentType.UNDERBARREL;
import static com.modernarmory.gun.FireMode.AUTO;
import static com.modernarmory.gun.FireMode.BURST;
import static com.modernarmory.gun.FireMode.SEMI;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.ballistics.Cartridges;

/**
 * THE gun catalog. Every gun in the mod is one entry here; items, the creative tab, lang / framing
 * checks and the autotest tour all iterate {@link #all()}. Adding a gun = one {@code add(...)} line
 * (plus its model files from tools/modelgen and one line in tools/modelgen/display_items.json).
 * Real-world specs and sources: docs/firearms/specs.json; model numbers (sight line, eye position,
 * slots, factory-fitted parts, animation lengths): docs/firearms/models.json.
 *
 * <p>Declaration order is the creative-tab order, grouped by category: pistols, submachine guns,
 * carbines / assault rifles, battle rifles, designated marksman rifles, sniper rifles, shotguns,
 * machine guns, anti-materiel. Masses are as carried (loaded, with the issued optic / suppressor),
 * since they set the recoil. Damage follows the energy scale (ballistics.DamageModel) unless set.
 * Sights are {@code ironSights(sight line y, eye z)} from models.json; where the issued optic is
 * magnified, eye z is the cheek weld used with irons / reflex sights (a scope's own eye position
 * comes from its model locator plus eye relief, see client.GunViews). The shared
 * {@code sniper_scope} stands in for every issued scope; where the real one has another power
 * range, {@code issuedScopePower(min, max)} sets it (specs.json default_optic).
 */
public final class Guns {
    private static final Map<String, GunDefinition> BY_ID = new LinkedHashMap<>();

    // ---- pistols ---------------------------------------------------------------------------------

    /** SIG Sauer M17 (P320 MHS): 4.7 in barrel, M882 at ~373 m/s, 17 rounds, 0.83 kg empty / 1.03 kg loaded, slide cut for a red dot. */
    public static final GunDefinition M17 = add(GunDefinition.of("m17", "M17 Pistol", GunFamilies.pistol()
            .muzzleVelocity(373).rpm(180)
            .magazine(17).reloadSeconds(1.8, 2.3)
            .accuracyMoa(3.5)
            .mass(1.03)
            .sights(GunView.ironSights(6.75f, 13f))));

    /** Glock 17 Gen5: 4.49 in barrel, ~371 m/s, 17 rounds, 0.71 kg empty / 0.91 kg loaded. */
    public static final GunDefinition GLOCK17 = add(GunDefinition.of("glock17", "Glock 17", GunFamilies.pistol()
            .muzzleVelocity(371).rpm(185)
            .magazine(17).reloadSeconds(1.7, 2.2)
            .accuracyMoa(3.5)
            .mass(0.91)
            .sights(GunView.ironSights(6.5f, 13f))
            .slots(MUZZLE, SIDE)));

    /** Glock 19 Gen5 (compact): 4.02 in barrel, ~365 m/s, 15 rounds, 0.67 kg empty / 0.85 kg loaded. */
    public static final GunDefinition GLOCK19 = add(GunDefinition.of("glock19", "Glock 19", GunFamilies.pistol()
            .muzzleVelocity(365).rpm(190)
            .magazine(15).reloadSeconds(1.7, 2.2)
            .accuracyMoa(4.0)
            .mass(0.85)
            .sights(GunView.ironSights(6.5f, 13f))
            .slots(MUZZLE, SIDE)));

    /** H&K USP45: .45 ACP, polymer frame, hammer-fired, 12-round magazine. */
    public static final GunDefinition USP = add(GunDefinition.of("usp", "H&K USP45", GunFamilies.pistol()
            .cartridge(Cartridges.ACP_45).report(Report.PISTOL_45)
            .muzzleVelocity(260).rpm(180)
            .magazine(12).reloadSeconds(1.8, 2.3)
            .accuracyMoa(3.0)
            .mass(0.98)
            .sights(GunView.ironSights(6.75f, 13f))
            .slots(MUZZLE, SIDE)));
    /** Beretta M9 (92FS): 9x19, open-top slide, the US service pistol from 1985 to 2017, 15-round magazine. */
    public static final GunDefinition M9 = add(GunDefinition.of("m9", "Beretta M9", GunFamilies.pistol()
            .muzzleVelocity(381).rpm(180)
            .magazine(15).reloadSeconds(1.8, 2.3)
            .accuracyMoa(3.5)
            .mass(1.1)
            .sights(GunView.ironSights(6.5f, 13f))
            .slots(MUZZLE, SIDE)));
    /**
     * Desert Eagle Mark XIX .50 AE: gas-operated, rotating bolt, 6 in barrel, 7 rounds, ~2.0 kg loaded.
     * A slab-sided barrel with a top rail (optic only; the barrel is not threaded). Heavy recoil: the
     * fire animation runs 0.16 s, so ~200 rpm at most.
     */
    public static final GunDefinition DEAGLE = add(GunDefinition.of("deagle", "Desert Eagle Mark XIX", GunFamilies.pistol()
            .cartridge(Cartridges.AE_50).report(Report.PISTOL_50AE)
            .muzzleVelocity(450).rpm(200)
            .magazine(7).reloadSeconds(2.0, 2.4)
            .accuracyMoa(3.0)
            .mass(2.0)
            .sights(GunView.ironSights(6.5f, 13f))
            .slots(OPTIC)));

    /**
     * Colt Anaconda (2021) .44 Magnum: double-action revolver, 6 in barrel, six-round cylinder reloaded with a
     * speedloader (no round "in the chamber" beyond the six), ~1.5 kg. No rails: no attachments.
     * Damage raised above the energy scale (7.8) to 12: a hand cannon, two body hits kill.
     */
    public static final GunDefinition ANACONDA = add(GunDefinition.of("anaconda", "Colt Anaconda", GunFamilies.pistol()
            .cartridge(Cartridges.MAG_44).report(Report.MAGNUM_44)
            .feed(Feed.CYLINDER)
            .muzzleVelocity(440).rpm(150)
            .magazine(6).reloadSeconds(2.6, 2.6)
            .damage(12.0f)
            .accuracyMoa(2.5)
            .mass(1.55)
            .sights(GunView.ironSights(6.75f, 13f))
            .slots()));

    // ---- submachine guns ---------------------------------------------------------------------------

    /** B&T APC9K Pro (US Army Sub Compact Weapon): 4.3 in barrel, ~368 m/s, 1,080 rpm, 30 rounds, 2.7 kg empty, red dot. */
    public static final GunDefinition APC9 = add(GunDefinition.of("apc9", "APC9K Sub Compact Weapon", GunFamilies.smg()
            .muzzleVelocity(368).rpm(1080)
            .magazine(30).reloadSeconds(2.3, 2.9)
            .accuracyMoa(4.0)
            .mass(3.1)
            .sights(GunView.ironSights(7.75f, 9f))
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE)
            .fitted(OPTIC, "red_dot")));

    /** SIG MPX (8 in barrel): ~400 m/s, 850 rpm, 30 rounds, 2.7 kg, no iron sights (red dot issued). */
    public static final GunDefinition MPX = add(GunDefinition.of("mpx", "SIG MPX", GunFamilies.smg()
            .muzzleVelocity(400).rpm(850)
            .magazine(30).reloadSeconds(2.3, 2.9)
            .accuracyMoa(3.0)
            .mass(3.2)
            .sights(GunView.ironSights(8.25f, 8.5f))
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE)
            .fitted(OPTIC, "red_dot")));

    /**
     * CZ Scorpion EVO 3 A1: 7.7 in barrel, ~380 m/s, 1,150 rpm, 30-round polymer magazine, 2.77 kg empty /
     * ~3.1 kg loaded, folding stock, left-side non-reciprocating charging handle, flip-up irons.
     */
    public static final GunDefinition SCORPION_EVO3 = add(GunDefinition.of("scorpion_evo3", "CZ Scorpion EVO 3 A1", GunFamilies.smg()
            .muzzleVelocity(380).rpm(1150)
            .magazine(30).reloadSeconds(2.2, 2.6)
            .accuracyMoa(3.5)
            .mass(3.1)
            .sights(GunView.ironSights(7.75f, 9f))
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE)));

    /**
     * H&K MP5A5: 225 mm barrel, ~400 m/s, 800 rpm, roller-delayed blowback firing from a closed bolt (famously
     * accurate), 30-round curved steel magazine, ~3.1 kg loaded, retractable stock, "Navy" trigger group
     * (semi / 3-round burst / full auto). No rails: optics go on the HK claw mount, a suppressor on the 3-lug
     * barrel. Reloads from empty with the "HK slap" (charging handle locked back, magazine swapped, handle slapped down).
     */
    public static final GunDefinition MP5 = add(GunDefinition.of("mp5", "H&K MP5A5", GunFamilies.smg()
            .muzzleVelocity(400).rpm(800)
            .fireModes(SEMI, BURST, AUTO).burst(3)
            .magazine(30).reloadSeconds(2.2, 2.8)
            .accuracyMoa(2.5)
            .mass(3.1)
            .sights(GunView.ironSights(7.5f, 9f)) // drum rear sight / hooded front post, sight_line y 7.5 (tools/modelgen/guns/mp5.py)
            .slots(OPTIC, MUZZLE)));

    // ---- carbines / assault rifles (5.56) ----------------------------------------------------------

    /** KRISS Vector Gen II: .45 ACP, the Super V recoil system, ~1,200 rpm, semi / 2-round burst / auto, 25-round magazine. */
    public static final GunDefinition VECTOR = add(GunDefinition.of("vector", "KRISS Vector", GunFamilies.smg()
            .cartridge(Cartridges.ACP_45).report(Report.PISTOL_45)
            .muzzleVelocity(290).rpm(1200)
            .fireModes(SEMI, BURST, AUTO).burst(2)
            .magazine(25).reloadSeconds(2.2, 2.7)
            .accuracyMoa(3.5)
            .mass(3.2)
            .sights(GunView.ironSights(8.25f, 10f)) // flip-up irons on the top rail (tools/modelgen/guns/vector.py)
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE)));
    /**
     * M4 carbine: 14.5 in barrel, M855 at ~880 m/s, 700-970 rpm cyclic (we use 800), selector
     * Safe / Semi / 3-round Burst (the M4A1 has full-auto instead), 30-round STANAG magazine,
     * 3.52 kg loaded. Source: Wikipedia "M4 carbine".
     */
    public static final GunDefinition M4 = add(GunDefinition.of("m4", "M4 Carbine", GunFamilies.ar15()
            .muzzleVelocity(880.0).rpm(800.0)
            .fireModes(SEMI, BURST).burst(3)
            .mass(3.52)));

    /**
     * M4A1: the same carbine with a Safe / Semi / Auto selector and a heavier (SOCOM profile)
     * barrel. The US Army converted its M4s to this configuration. Same 14.5 in barrel and M855
     * ballistics; cyclic rate 700-970 rpm (we use 800); ~3.6 kg loaded. Rendered with the M4 model.
     */
    public static final GunDefinition M4A1 = add(GunDefinition.of("m4a1", "M4A1 Carbine", M4.spec().toBuilder()
            .fireModes(SEMI, AUTO)
            .mass(3.6)).model("m4"));

    /** M16A4: 20 in barrel, M855 at ~945 m/s, Semi / 3-round Burst, 30 rounds, 4.0 kg loaded, A2 sights on the carry handle. */
    public static final GunDefinition M16A4 = add(GunDefinition.of("m16a4", "M16A4 Rifle", GunFamilies.ar15()
            .muzzleVelocity(945).rpm(800)
            .fireModes(SEMI, BURST).burst(3)
            .reloadSeconds(2.4, 2.9)
            .accuracyMoa(3.5)
            .mass(4.0)
            .sights(GunView.ironSights(8f, 9.5f))
            .report(Report.RIFLE_556)
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE, MAGAZINE)));

    /** HK416 D14.5RS: short-stroke piston, ~884 m/s, 850 rpm, 30 rounds, 3.49 kg without magazine. */
    public static final GunDefinition HK416 = add(GunDefinition.of("hk416", "HK416", GunFamilies.ar15()
            .muzzleVelocity(884).rpm(850)
            .reloadSeconds(2.3, 2.8)
            .accuracyMoa(2.0)
            .mass(4.0)
            .sights(GunView.ironSights(8f, 10.5f))
            .report(Report.RIFLE_556)));

    /** SIG MCX Virtus 11.5 in SBR: ~810 m/s, 850 rpm, 30 rounds, ~3.0 kg, no irons (red dot), folding stock. */
    public static final GunDefinition MCX = add(GunDefinition.of("mcx", "SIG MCX Virtus", GunFamilies.ar15()
            .muzzleVelocity(810).rpm(850)
            .reloadSeconds(2.3, 2.8)
            .accuracyMoa(2.0)
            .mass(3.5)
            .sights(GunView.ironSights(8.25f, 8.5f))
            .report(Report.RIFLE_556)
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE, MAGAZINE)
            .fitted(OPTIC, "red_dot")));
    /** FAMAS F1 (France): 5.56 bullpup, carry handle over the top, 25-round magazine behind the grip, ~1,000 rpm and a 3-round burst. */
    public static final GunDefinition FAMAS = add(GunDefinition.of("famas", "FAMAS F1", GunFamilies.ar15()
            .muzzleVelocity(960).rpm(1000)
            .fireModes(SEMI, BURST, AUTO).burst(3)
            .magazine(25).reloadSeconds(2.6, 3.1)
            .accuracyMoa(2.5)
            .mass(3.8)
            .sights(GunView.ironSights(9.0f, 12.25f)) // rear aperture on the carry handle, sight_line y 9.0 (tools/modelgen/guns/famas.py)
            .report(Report.RIFLE_556)
            .slots(OPTIC, MUZZLE)));

    // ---- battle rifles (6.8 / 7.62) -----------------------------------------------------------------

    /** AKM / AK-47: 7.62x39, wood furniture, curved 30-round magazine, ~600 rpm. */
    public static final GunDefinition AK47 = add(GunDefinition.of("ak47", "AK-47", GunFamilies.ar15()
            .cartridge(Cartridges.M43).report(Report.RIFLE_762X39)
            .muzzleVelocity(715).rpm(600)
            .magazine(30).reloadSeconds(2.4, 2.9)
            .accuracyMoa(3.5)
            .mass(3.9)
            .sights(GunView.ironSights(7.5f, 9.5f)) // rear tangent notch far forward on the receiver (tools/modelgen/guns/ak47.py)
            .slots(OPTIC, MUZZLE))); // optics on a side-rail mount
    /** Steyr AUG A1: 5.56 bullpup with its integral 1.5x optic, 30-round magazine behind the grip, ~750 rpm. */
    public static final GunDefinition AUG = add(GunDefinition.of("aug", "Steyr AUG A1", GunFamilies.ar15()
            .muzzleVelocity(940).rpm(750)
            .magazine(30).reloadSeconds(2.6, 3.1)
            .accuracyMoa(2.0)
            .mass(4.1)
            .sights(GunView.scope(8.25f, 7f, 1.5f, 1.5f, ModernArmory.id("textures/misc/scope_m2010.png"), Attachment.Reticle.CHEVRON)) // the integral 1.5x optic (tools/modelgen/guns/aug.py)
            .report(Report.RIFLE_556)
            .slots(MUZZLE)));
    /**
     * SIG M7 (NGSW-Rifle): 13 in barrel, 6.8x51 XM1186 at ~915 m/s, 750 rpm, 20 rounds; 4.46 kg with
     * the issued suppressor, ~5.8 kg with the M157 fire control and a loaded magazine (the ACOG
     * stands in for the M157).
     */
    public static final GunDefinition M7 = add(GunDefinition.of("m7", "M7 Rifle", GunFamilies.battleRifle()
            .cartridge(Cartridges.CC_68X51)
            .muzzleVelocity(915).rpm(750)
            .magazine(20).reloadSeconds(2.6, 3.2)
            .accuracyMoa(1.5)
            .mass(5.8)
            .sights(GunView.ironSights(8.25f, 10f))
            .report(Report.RIFLE_68)
            .fitted(OPTIC, "acog").fitted(MUZZLE, "suppressor_762")));

    /** SIG XM8 (carbine variant of the M7): 10.5 in barrel, ~884 m/s, 750 rpm, 25 rounds, 3.92 kg suppressed. */
    public static final GunDefinition XM8 = add(GunDefinition.of("xm8", "XM8 Carbine", GunFamilies.battleRifle()
            .cartridge(Cartridges.CC_68X51)
            .muzzleVelocity(884).rpm(750)
            .magazine(25).reloadSeconds(2.5, 3.1)
            .accuracyMoa(2.0)
            .mass(5.4)
            .sights(GunView.ironSights(8.25f, 10f))
            .report(Report.RIFLE_68)
            .fitted(OPTIC, "acog").fitted(MUZZLE, "suppressor_762")));

    /** FN SCAR-H Mk 17 Mod 0 (16 in): 7.62 M80A1 at ~870 m/s, 600 rpm, 20 rounds, 3.58 kg empty. */
    public static final GunDefinition MK17 = add(GunDefinition.of("mk17", "Mk 17 SCAR-H", GunFamilies.battleRifle()
            .cartridge(Cartridges.M80A1)
            .muzzleVelocity(870).rpm(600)
            .magazine(20).reloadSeconds(2.5, 3.0)
            .accuracyMoa(1.5)
            .mass(4.2)
            .sights(GunView.ironSights(8.5f, 11f))
            .report(Report.RIFLE_762)));

    // ---- designated marksman rifles (7.62 M118LR) ---------------------------------------------------

    /** M14: 7.62x51 select-fire battle rifle, full-length walnut stock, 20-round magazine, ~750 rpm. */
    public static final GunDefinition M14 = add(GunDefinition.of("m14", "M14", GunFamilies.battleRifle()
            .cartridge(Cartridges.M80A1).report(Report.RIFLE_762)
            .muzzleVelocity(850).rpm(750)
            .magazine(20).reloadSeconds(2.4, 2.9)
            .accuracyMoa(1.5)
            .mass(5.1)
            .sights(GunView.ironSights(7.25f, 9.25f)) // rear aperture (tools/modelgen/guns/m14.py)
            .slots(OPTIC, MUZZLE, UNDERBARREL)));
    /** FN Mk 20 SSR: 20 in barrel, M118LR at ~783 m/s, 20 rounds, 4.85 kg bare, scope issued. */
    public static final GunDefinition MK20 = add(GunDefinition.of("mk20", "Mk 20 SSR", GunFamilies.dmr()
            .cartridge(Cartridges.M118LR)
            .muzzleVelocity(783).rpm(120)
            .magazine(20).reloadSeconds(2.5, 3.3)
            .accuracyMoa(1.0)
            .mass(6.2)
            .sights(GunView.ironSights(8.5f, 11f))
            .fitted(OPTIC, "sniper_scope").issuedScopePower(3.5f, 15f)));

    /** KAC M110 SASS: 20 in barrel, M118LR at 783 m/s, 20 rounds, 6.27 kg bare; issued with scope and QD suppressor. */
    public static final GunDefinition M110 = add(GunDefinition.of("m110", "M110 SASS", GunFamilies.dmr()
            .cartridge(Cartridges.M118LR)
            .muzzleVelocity(783).rpm(120)
            .magazine(20).reloadSeconds(2.5, 3.3)
            .accuracyMoa(1.0)
            .mass(7.9)
            .sights(GunView.ironSights(8f, 10f))
            .fitted(OPTIC, "sniper_scope").issuedScopePower(3.5f, 10f).fitted(MUZZLE, "suppressor_762")));

    /** HK M110A1 CSASS: 16.3 in barrel, ~750 m/s, 20 rounds, 6.8 kg with scope and suppressor; no irons. */
    public static final GunDefinition M110A1 = add(GunDefinition.of("m110a1", "M110A1 CSASS", GunFamilies.dmr()
            .cartridge(Cartridges.M118LR)
            .muzzleVelocity(750).rpm(120)
            .magazine(20).reloadSeconds(2.4, 3.2)
            .accuracyMoa(1.5)
            .mass(7.4)
            .sights(GunView.ironSights(8f, 11f))
            .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE, STOCK)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(3f, 20f).fitted(MUZZLE, "suppressor_762")));

    // ---- sniper rifles ------------------------------------------------------------------------------

    /**
     * M2010 Enhanced Sniper Rifle (Remington): bolt action, .300 Win Mag Mk 248 Mod 1 at 869 m/s
     * from a 24 in barrel, 5-round box magazine, <= 1 MOA, AAC Titan-QD suppressor, Leupold
     * Mark 4 6.5-20x50 scope. 5.5 kg bare; ~7 kg with scope, suppressor and loaded mag.
     * Cycle time 1.3 s (fire + working the bolt), matching the "fire" animation. Scope axis
     * height and eye position (eye relief behind the eyepiece) from m2010.py.
     */
    public static final GunDefinition M2010 = add(GunDefinition.of("m2010", "M2010 Enhanced Sniper Rifle", GunFamilies.boltSniper()
            .cartridge(Cartridges.MK248)
            .muzzleVelocity(869.0).cycleSeconds(1.3)
            .magazine(5).reloadSeconds(2.6, 3.2)
            .damage(20.0f)
            .mass(7.0)
            .sights(GunView.scope(7.5f, 5.0f, 6.5f, 20f, ModernArmory.id("textures/misc/scope_m2010.png"), Attachment.Reticle.MIL_DOT))
            .fitted(OPTIC, "sniper_scope").fitted(MUZZLE, "suppressor_300")));

    /** Mk 13 Mod 7: bolt action, .300 Win Mag at 869 m/s from 26.5 in, 5 rounds, 0.6 MOA, 6.1 kg bare; cycle 1.5 s. */
    public static final GunDefinition MK13 = add(GunDefinition.of("mk13", "Mk 13 Mod 7", GunFamilies.boltSniper()
            .cartridge(Cartridges.MK248)
            .muzzleVelocity(869).cycleSeconds(1.5)
            .magazine(5).reloadSeconds(3.5, 4.5)
            .accuracyMoa(0.6)
            .mass(7.3)
            .sights(GunView.ironSights(7.5f, 4.75f))
            .report(Report.MAGNUM_300)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(5f, 25f)));

    /** Remington Mk 21 PSR: bolt action, .338 Lapua 250 gr at ~905 m/s from 27 in, 10 rounds, 1 MOA, ~6.5 kg bare; cycle 1.5 s. */
    public static final GunDefinition MK21 = add(GunDefinition.of("mk21", "Mk 21 PSR", GunFamilies.boltSniper()
            .cartridge(Cartridges.LAPUA_338)
            .muzzleVelocity(905).cycleSeconds(1.5)
            .magazine(10).reloadSeconds(3.5, 4.5)
            .accuracyMoa(1.0)
            .mass(8.0)
            .sights(GunView.ironSights(7.5f, 4.75f))
            .report(Report.MAGNUM_338)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(5f, 25f)));

    /** Barrett Mk 22 MRAD: bolt action, .338 Norma 300 gr at ~818 m/s from 27 in, 10 rounds, 0.8 MOA, 7.0 kg bare; cycle 1.5 s. */
    public static final GunDefinition MK22 = add(GunDefinition.of("mk22", "Mk 22 MRAD", GunFamilies.boltSniper()
            .cartridge(Cartridges.NORMA_338)
            .muzzleVelocity(818).cycleSeconds(1.5)
            .magazine(10).reloadSeconds(3.5, 4.5)
            .accuracyMoa(0.8)
            .mass(8.5)
            .sights(GunView.ironSights(7.5f, 4.75f))
            .report(Report.MAGNUM_338)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(5f, 25f)));

    // ---- shotguns (12 gauge 00 buckshot, 9 pellets) ------------------------------------------------

    /** Accuracy International AWM: .338 Lapua Magnum bolt action, folding thumbhole stock, 5-round magazine, S&B 3-12x scope. */
    public static final GunDefinition AWM = add(GunDefinition.of("awm", "Accuracy International AWM", GunFamilies.boltSniper()
            .cartridge(Cartridges.LAPUA_338)
            .muzzleVelocity(936).cycleSeconds(1.3)
            .magazine(5).reloadSeconds(2.6, 3.2)
            .accuracyMoa(0.5)
            .mass(8.0)
            .sights(GunView.ironSights(7.5f, 4.75f))
            .report(Report.MAGNUM_338)
            .slots(OPTIC, MUZZLE, UNDERBARREL)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(3f, 12f)));
    /** M24 SWS: 7.62x51 (M118LR) Remington 700 bolt action, internal 5-round magazine loaded from the top, Leupold 10x scope. */
    public static final GunDefinition M24 = add(GunDefinition.of("m24", "M24 SWS", GunFamilies.boltSniper()
            .cartridge(Cartridges.M118LR)
            .muzzleVelocity(790).cycleSeconds(1.3)
            .magazine(5).reloadSeconds(2.5, 3.34)
            .accuracyMoa(0.8)
            .mass(7.3)
            .sights(GunView.ironSights(7.5f, 4.75f))
            .report(Report.RIFLE_762)
            .slots(OPTIC, MUZZLE, UNDERBARREL)
            .fitted(OPTIC, "sniper_scope").issuedScopePower(10f, 10f)));
    /**
     * Mossberg M590A1: pump action, 20 in cylinder bore, ~400 m/s, 8 shells in the tube (+1
     * chambered), 3.4 kg; 0.65 s per shell, 1.7 s to chamber one from empty; 1.0 s pump cycle.
     */
    public static final GunDefinition M590 = add(GunDefinition.of("m590", "Mossberg M590A1", GunFamilies.pumpShotgun()
            .muzzleVelocity(400).cycleSeconds(1.0)
            .magazine(8).reloadSeconds(0.65, 1.7)
            .accuracyMoa(6.0)
            .mass(3.8)
            .sights(GunView.ironSights(7.5f, 9.5f))
            .slots()));

    /** Benelli M4 Super 90 (M1014): gas semi-auto, 18.5 in, ~395 m/s, 7 + 1 shells, 3.82 kg; 0.6 s per shell. */
    public static final GunDefinition BENELLI_M4 = add(GunDefinition.of("benelli_m4", "Benelli M1014", GunFamilies.tubeSemiShotgun()
            .muzzleVelocity(395).rpm(180)
            .magazine(7).reloadSeconds(0.6, 1.5)
            .accuracyMoa(5.0)
            .mass(4.1)
            .sights(GunView.ironSights(8f, 9.5f))
            .slots(OPTIC)));

    /** M26 MASS (stand-alone): straight-pull, 7.75 in barrel (~335 m/s with 00 buck), 5-round box, 2.27 kg; 1.5 s cycle. */
    public static final GunDefinition M26MASS = add(GunDefinition.of("m26mass", "M26 MASS", GunFamilies.straightPullShotgun()
            .muzzleVelocity(335).cycleSeconds(1.5)
            .magazine(5).reloadSeconds(2.8, 3.8)
            .accuracyMoa(8.0)
            .mass(2.5)
            .sights(GunView.ironSights(8f, 10.5f))
            .slots(OPTIC, STOCK)));

    // ---- machine guns -------------------------------------------------------------------------------

    /** Franchi SPAS-12: 12 gauge, pump or semi-auto at the flick of a button, folding stock with its arm hook, 8-shell tube. */
    public static final GunDefinition SPAS12 = add(GunDefinition.of("spas12", "Franchi SPAS-12", GunFamilies.pumpShotgun()
            .fireModes(FireMode.PUMP, SEMI)
            .muzzleVelocity(400).cycleSeconds(0.9)
            .magazine(8).reloadSeconds(0.7, 1.7)
            .accuracyMoa(6.0)
            .mass(4.4)
            .sights(GunView.ironSights(7.5f, 7f))
            .slots()));
    /**
     * MPS AA-12 (Atchisson Assault Shotgun): open-bolt, gas-operated, select fire at ~300 rpm; its long recoil
     * spring and gas system soak up most of the kick, so it can be fired on full auto. 18 in barrel (~395 m/s with
     * 00 buck), 20-round drum (8-round box and 32-round drum also exist), 5.2 kg empty, ~6.5 kg with a loaded drum.
     * Top Picatinny rail over the receiver; the iron sights fold under an optic.
     */
    public static final GunDefinition AA12 = add(GunDefinition.of("aa12", "MPS AA-12", GunFamilies.magShotgun()
            .fireModes(AUTO, SEMI)
            .muzzleVelocity(395).rpm(300)
            .magazine(20).reloadSeconds(3.0, 3.8)
            .accuracyMoa(6.0)
            .mass(6.5)
            .sights(GunView.ironSights(8.75f, 10f))
            .slots(OPTIC)));

    /**
     * Kalashnikov Saiga-12: AK-pattern gas semi-auto 12 gauge, 19 in barrel (~400 m/s with 00 buck), 8-round box
     * rocked in like an AK magazine, 3.6 kg empty. Optics go on the AK side rail (side mount), as on the AK-47.
     */
    public static final GunDefinition SAIGA12 = add(GunDefinition.of("saiga12", "Kalashnikov Saiga-12", GunFamilies.magShotgun()
            .muzzleVelocity(400).rpm(200)
            .magazine(8).reloadSeconds(2.4, 3.0)
            .accuracyMoa(6.0)
            .mass(4.0)
            .sights(GunView.ironSights(7.5f, 9.5f))
            .slots(OPTIC)));

    /** M249 SAW: 5.56 at ~915 m/s, 850 rpm, 200-round belt, 7.5 kg empty / 10 kg with the pouch, M145 (ACOG stand-in). */
    public static final GunDefinition M249 = add(GunDefinition.of("m249", "M249 SAW", GunFamilies.beltFedMg()
            .cartridge(Cartridges.M855)
            .muzzleVelocity(915).rpm(850)
            .magazine(200).reloadSeconds(9, 7)
            .accuracyMoa(4.0)
            .mass(10.0)
            .sights(GunView.ironSights(8f, 9.5f))
            .report(Report.RIFLE_556)
            .fitted(OPTIC, "acog")));

    /** SIG M250 (NGSW-AR): 6.8 at ~945 m/s, 800 rpm, 100-round belt, 6.6 kg with suppressor and bipod. */
    public static final GunDefinition M250 = add(GunDefinition.of("m250", "M250 Automatic Rifle", GunFamilies.beltFedMg()
            .cartridge(Cartridges.CC_68X51)
            .muzzleVelocity(945).rpm(800)
            .magazine(100).reloadSeconds(7, 6)
            .accuracyMoa(3.0)
            .mass(9.5)
            .sights(GunView.ironSights(8.25f, 10f))
            .report(Report.RIFLE_68)
            .support(Support.SHOULDER) // no integral bipod modelled: the fitted Harris bipod deploys when crouched
            .fitted(OPTIC, "acog").fitted(MUZZLE, "suppressor_762").fitted(UNDERBARREL, "bipod")));

    /** FN M240B: 7.62 at ~935 m/s, 700 rpm (650-950), 100-round belt, 12.5 kg. */
    public static final GunDefinition M240 = add(GunDefinition.of("m240", "M240B", GunFamilies.beltFedMg()
            .cartridge(Cartridges.M80A1)
            .muzzleVelocity(935).rpm(700)
            .magazine(100).reloadSeconds(9, 7)
            .accuracyMoa(3.5)
            .mass(14.5)
            .sights(GunView.ironSights(8f, 9f))
            .report(Report.RIFLE_762)
            .fitted(OPTIC, "acog")));

    /**
     * M2A1 .50 heavy machine gun: 45 in barrel, M33 at 887 m/s, 550 rpm (auto / single shot),
     * 100-round belt, 38 kg gun on a 20 kg M3 tripod. Fire it crouched (on the tripod); standing it
     * kicks harder and sprays ({@link Support#TRIPOD}). Eye 8 units (~19 cm) behind the spade grips.
     */
    public static final GunDefinition M2A1 = add(GunDefinition.of("m2a1", "M2A1 Machine Gun", GunFamilies.heavyMg()
            .fireModes(AUTO, SEMI)
            .muzzleVelocity(887).rpm(550)
            .magazine(100).reloadSeconds(8, 10)
            .accuracyMoa(4.0)
            .mass(38.2)
            .sights(GunView.ironSights(8.5f, 8f))));

    // ---- anti-materiel -----------------------------------------------------------------------------

    /** Barrett M107: short-recoil semi-auto, 29 in barrel, M33 at ~853 m/s, 10 rounds, 13 kg bare, arrowhead brake, Leupold scope. */
    public static final GunDefinition M107 = add(GunDefinition.of("m107", "Barrett M107", GunFamilies.antiMateriel()
            .muzzleVelocity(853).rpm(60)
            .magazine(10).reloadSeconds(4.0, 5.5)
            .accuracyMoa(2.0)
            .mass(15.0)
            .sights(GunView.ironSights(8f, 9.5f))
            .fitted(OPTIC, "sniper_scope").issuedScopePower(4.5f, 14f).fitted(MUZZLE, "muzzle_brake_50")));

    private static GunDefinition add(GunDefinition def) {
        if (BY_ID.putIfAbsent(def.id(), def) != null) throw new IllegalStateException("duplicate gun id " + def.id());
        return def;
    }

    /** Every gun, in catalog (= creative tab) order. */
    public static Collection<GunDefinition> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Lookup by item id, or null. */
    public static GunDefinition byId(String id) {
        return BY_ID.get(id);
    }

    private Guns() {}
}
