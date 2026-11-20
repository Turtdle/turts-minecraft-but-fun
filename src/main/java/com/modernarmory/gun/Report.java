package com.modernarmory.gun;

import java.util.function.Supplier;

import com.modernarmory.registry.ModSounds;

import net.minecraft.sounds.SoundEvent;

/**
 * The sound of a shot for one calibre / class of gun: bare muzzle and suppressed. Shared by every
 * gun firing it; a gun picks one with {@link GunSpec.Builder#report(Report)} and gets the rest of
 * its sounds by convention ({@link SoundSets#forGun}). Files: tools/soundgen/make_sounds.py.
 * Calibres without a suppressor attachment play the bare report when "suppressed".
 */
public enum Report {
    /** 9x19 from a short pistol barrel: sharp, snappy crack, no ballistic crack (subsonic). */
    PISTOL_9MM(ModSounds.FIRE_9MM_PISTOL, ModSounds.FIRE_9MM_SUPPRESSED),
    /** 9x19 from an SMG: a little fuller, the bolt's clack in it. */
    SMG_9MM(ModSounds.FIRE_9MM_SMG, ModSounds.FIRE_9MM_SUPPRESSED),
    /** 5.56 NATO: the M4 recordings. */
    RIFLE_556(ModSounds.M4_FIRE, ModSounds.M4_FIRE_SUPPRESSED),
    /** 6.8x51 (80,000 psi): very sharp, loud blast even from short barrels. */
    RIFLE_68(ModSounds.FIRE_68, ModSounds.FIRE_68_SUPPRESSED),
    /** 7.62x51 NATO: deeper, heavier boom than 5.56. */
    RIFLE_762(ModSounds.FIRE_762, ModSounds.FIRE_762_SUPPRESSED),
    /** 7.62x39 from an AK: a flatter, harsher bark than 7.62 NATO. */
    RIFLE_762X39(ModSounds.FIRE_762X39, ModSounds.FIRE_762_SUPPRESSED),
    /** .45 ACP: a deep, subsonic thump, little crack; suppressed it is mostly the action. */
    PISTOL_45(ModSounds.FIRE_45ACP, ModSounds.FIRE_9MM_SUPPRESSED),
    /** .300 Win Mag (bare shot, without the M2010's built-in bolt cycle). */
    MAGNUM_300(ModSounds.FIRE_300WM, ModSounds.FIRE_300WM_SUPPRESSED),
    /** .338 Lapua / Norma Magnum: big magnum blast, long tail. */
    MAGNUM_338(ModSounds.FIRE_338, ModSounds.FIRE_338_SUPPRESSED),
    /** .50 BMG: a deep boom with a long rolling tail. */
    BMG_50(ModSounds.FIRE_50BMG, ModSounds.FIRE_50BMG),
    /** .50 Action Express from the Desert Eagle: a huge handgun blast. No suppressor fits. */
    PISTOL_50AE(ModSounds.FIRE_50AE, ModSounds.FIRE_50AE),
    /** .44 Magnum from a 6 in revolver: big blast, the cylinder gap adds bark. No suppressor fits. */
    MAGNUM_44(ModSounds.FIRE_44MAG, ModSounds.FIRE_44MAG),
    /** 12 gauge: a big low "boom", no supersonic crack. */
    GAUGE_12(ModSounds.FIRE_12GA, ModSounds.FIRE_12GA);

    private final Supplier<SoundEvent> fire, suppressed;

    Report(Supplier<SoundEvent> fire, Supplier<SoundEvent> suppressed) {
        this.fire = fire;
        this.suppressed = suppressed;
    }

    public Supplier<SoundEvent> fire() {
        return fire;
    }

    public Supplier<SoundEvent> suppressed() {
        return suppressed;
    }
}
