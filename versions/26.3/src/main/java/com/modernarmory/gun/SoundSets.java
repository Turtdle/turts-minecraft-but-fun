package com.modernarmory.gun;

import com.modernarmory.registry.ModSounds;

/**
 * Sound sets. Most guns use {@link #forGun}: their calibre's {@link Report} plus their own
 * mechanical sounds named by convention and generated to their animation lengths. The M4 / M2010
 * keep their recorded sets ({@link #AR15}, {@link #M2010}). A gun can still override single sounds
 * via {@link SoundSet#toBuilder()}.
 */
public final class SoundSets {
    /** AR-15 / M4 family: 5.56 report, suppressed crack, mag swap, mag swap + bolt release. */
    public static final SoundSet AR15 = SoundSet.builder()
            .fire(ModSounds.M4_FIRE).suppressed(ModSounds.M4_FIRE_SUPPRESSED)
            .reload(ModSounds.M4_RELOAD).reloadEmpty(ModSounds.M4_RELOAD_EMPTY)
            .build();

    /**
     * M2010 (.300 Win Mag bolt gun). The recordings include working the bolt, so no separate cycle
     * sound. It ships suppressed: "fire" is the bare-muzzle report, "suppressed" the usual one.
     */
    public static final SoundSet M2010 = SoundSet.builder()
            .fire(ModSounds.M2010_FIRE_LOUD).suppressed(ModSounds.M2010_FIRE)
            .reload(ModSounds.M2010_RELOAD).reloadEmpty(ModSounds.M2010_RELOAD_EMPTY)
            .build();

    /**
     * The conventional set of gun {@code id}: the report's shot sounds and one mechanical sound per
     * animation, timed to it: "&lt;id&gt;_reload" (tube-fed: one shell into the tube, looped per
     * shell like the animation), "&lt;id&gt;_reload_empty", and for manually cycled actions (bolt /
     * pump / straight pull) "&lt;id&gt;_cycle", which starts with the shot and follows the "fire"
     * animation. tools/soundgen/make_sounds.py writes these files from the animation lengths in
     * docs/firearms/models.json.
     */
    public static SoundSet forGun(String id, Report report, boolean manualCycle, boolean tubeFed) {
        var reload = ModSounds.mechanical(id + "_reload");
        SoundSet.Builder b = SoundSet.builder()
                .fire(report.fire()).suppressed(report.suppressed())
                .reload(reload)
                .reloadEmpty(ModSounds.mechanical(id + "_reload_empty"));
        if (manualCycle) b.cycle(ModSounds.mechanical(id + "_cycle"), 0f);
        if (tubeFed) b.shellInsert(reload);
        return b.build();
    }

    private SoundSets() {}
}
