package com.modernarmory.gun;

import static com.modernarmory.attachment.AttachmentType.MUZZLE;
import static com.modernarmory.attachment.AttachmentType.OPTIC;
import static com.modernarmory.attachment.AttachmentType.SIDE;
import static com.modernarmory.attachment.AttachmentType.STOCK;
import static com.modernarmory.attachment.AttachmentType.UNDERBARREL;
import static com.modernarmory.gun.FireMode.AUTO;
import static com.modernarmory.gun.FireMode.BOLT;
import static com.modernarmory.gun.FireMode.PUMP;
import static com.modernarmory.gun.FireMode.SEMI;
import static com.modernarmory.gun.FireMode.STRAIGHT_PULL;

import com.modernarmory.ballistics.Cartridges;

/**
 * Family templates: {@link GunSpec.Builder}s pre-filled with what a whole family shares (view class,
 * action / fire modes, feed, attachment slots, typical shooter spread, sounds where the family
 * has them). A gun starts from its family and sets its own real-world numbers; a change for a whole
 * family is one edit here.
 *
 * <p>Gun-specific values (muzzle velocity, rate, magazine, reload times, damage, mass, sights)
 * are left to the gun unless the family really shares them; {@link GunSpec.Builder#build()}
 * names anything still missing. Damage follows the mod-wide energy scale unless set. Sounds: the
 * AR-15 and the M2010-style bolt gun have recorded sets; the others set a calibre {@link Report}
 * (a gun of a mixed-calibre family sets its own), and each gun gets its own reload / cycle sounds
 * by convention ({@link SoundSets#forGun}).
 */
public final class GunFamilies {

    /**
     * AR-15 / M4 pattern carbines and rifles (5.56 NATO, 30-round STANAG, direct impingement or
     * short-stroke piston): all six slots, iron sights of the M4 model, M4 sounds and reload times
     * (2.2 s tactical, 2.6 s from empty with the bolt release). Damage follows the energy scale.
     */
    public static GunSpec.Builder ar15() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.RIFLE)
                .cartridge(Cartridges.M855)
                .fireModes(SEMI, AUTO)
                .feed(Feed.MAGAZINE).magazine(30)
                .reloadSeconds(2.2, 2.6)
                .accuracyMoa(3.0)
                .spread(2.5, 0.12)
                .sights(GunView.ironSights(8.0f, 10.5f))
                .sounds(SoundSets.AR15)
                .allSlots();
    }

    /**
     * Full-power (6.8 / 7.62) select-fire battle rifles: harder hitting, more recoil, a bit slower
     * to swing than an AR. Folding front BUIS (SCAR, M7), laid flat under an optic.
     */
    public static GunSpec.Builder battleRifle() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.RIFLE)
                .fireModes(SEMI, AUTO)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(1.5)
                .spread(3.0, 0.1)
                .adsSeconds(0.22)
                .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE);
    }

    /** Bolt-action precision rifles: one round per press, bolt worked between shots, sub-MOA, steady when aimed. */
    public static GunSpec.Builder boltSniper() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.SNIPER)
                .fireModes(BOLT)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(1.0)
                .spread(5.0, 0.02)
                .sounds(SoundSets.M2010)
                .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE);
    }

    /** Semi-automatic 7.62 designated marksman rifles; the folding front BUIS lies flat under the scope. */
    public static GunSpec.Builder dmr() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.MARKSMAN)
                .fireModes(SEMI)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(1.5)
                .spread(3.5, 0.05)
                .adsSeconds(0.25)
                .report(Report.RIFLE_762)
                .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE);
    }

    /**
     * Semi-automatic 9 mm service pistols: held out one-handed (PISTOL framing), light so they flip
     * (the recoil uses the pistol's own ~0.7-0.9 kg), quick to the sights.
     */
    public static GunSpec.Builder pistol() {
        return GunSpec.builder()
                .view(ViewClass.PISTOL)
                .category(GunCategory.PISTOL)
                .cartridge(Cartridges.NATO_9X19)
                .fireModes(SEMI)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(3.5)
                .spread(2.0, 0.25)
                .adsSeconds(0.15)
                .report(Report.PISTOL_9MM)
                .slots(OPTIC, MUZZLE, SIDE);
    }

    /** 9 mm submachine guns / PDWs. */
    public static GunSpec.Builder smg() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.SMG)
                .cartridge(Cartridges.NATO_9X19)
                .fireModes(SEMI, AUTO)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(4.0)
                .spread(2.0, 0.15)
                .adsSeconds(0.18)
                .report(Report.SMG_9MM)
                .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE, STOCK);
    }

    /** Pump-action 12 gauge shotguns with a tube magazine, loaded shell by shell; the pump is racked between shots. */
    public static GunSpec.Builder pumpShotgun() {
        return GunSpec.builder()
                .view(ViewClass.RIFLE)
                .category(GunCategory.SHOTGUN)
                .cartridge(Cartridges.BUCK_00)
                .fireModes(PUMP)
                .feed(Feed.TUBE)
                .accuracyMoa(6.0)
                .spread(2.0, 0.15)
                .report(Report.GAUGE_12)
                .slots(OPTIC, SIDE);
    }

    /** Semi-automatic (inertia / gas) shotguns with a tube magazine. */
    public static GunSpec.Builder tubeSemiShotgun() {
        return pumpShotgun().fireModes(SEMI);
    }

    /** Self-loading shotguns fed from a detachable box or drum magazine (Saiga-12, AA-12). */
    public static GunSpec.Builder magShotgun() {
        return pumpShotgun().fireModes(SEMI).feed(Feed.MAGAZINE);
    }

    /** Straight-pull shotguns with a detachable box magazine (M26 MASS). */
    public static GunSpec.Builder straightPullShotgun() {
        return pumpShotgun().fireModes(STRAIGHT_PULL).feed(Feed.MAGAZINE);
    }

    /**
     * Belt-fed light / medium machine guns: open bolt (belt swap, no round stays chambered), long
     * reloads, integral bipod (crouch to deploy), carried low and slow to aim.
     */
    public static GunSpec.Builder beltFedMg() {
        return GunSpec.builder()
                .view(ViewClass.HEAVY)
                .category(GunCategory.MACHINE_GUN)
                .fireModes(AUTO)
                .feed(Feed.BELT)
                .accuracyMoa(4.0)
                .spread(4.0, 0.2)
                .support(Support.BIPOD)
                .adsSeconds(0.35)
                .slots(OPTIC, MUZZLE, UNDERBARREL, SIDE);
    }

    /**
     * Tripod-mounted heavy machine guns (.50 BMG): crouch to get behind the tripod (see
     * {@link Support#TRIPOD}). The tripod is only drawn in first person; carried (third person,
     * icons, dropped) the gun is shown without it.
     */
    public static GunSpec.Builder heavyMg() {
        return beltFedMg()
                .cartridge(Cartridges.BMG_50)
                .report(Report.BMG_50)
                .spread(5.0, 0.3)
                .support(Support.TRIPOD)
                .adsSeconds(0.45)
                .part("tripod", PartVisibility.FIRST_PERSON_ONLY)
                .slots();
    }

    /** Semi-automatic .50 BMG anti-materiel rifles: integral bipod, muzzle brake, folding BUIS, heavy and slow to aim. */
    public static GunSpec.Builder antiMateriel() {
        return GunSpec.builder()
                .view(ViewClass.HEAVY)
                .category(GunCategory.SNIPER)
                .cartridge(Cartridges.BMG_50)
                .fireModes(SEMI)
                .feed(Feed.MAGAZINE)
                .accuracyMoa(1.5)
                .spread(6.0, 0.05)
                .support(Support.BIPOD)
                .adsSeconds(0.4)
                .report(Report.BMG_50)
                .slots(OPTIC, MUZZLE);
    }

    private GunFamilies() {}
}
