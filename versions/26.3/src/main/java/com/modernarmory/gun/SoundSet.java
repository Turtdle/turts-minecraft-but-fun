package com.modernarmory.gun;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;

/**
 * Every sound one gun (or gun family) makes. Sound events themselves are registered in one list
 * in {@code ModSounds}; the shared sets live in {@link SoundSets}.
 *
 * @param fire           shot
 * @param fireSuppressed shot with a suppressor fitted
 * @param reload         tactical reload (round still chambered)
 * @param reloadEmpty    reload from empty (bolt release / charging handle / chambering a shell)
 * @param shellInsert    one shell pushed into a tube magazine (TUBE feed, looped per shell)
 * @param cycle          working the action after a shot (pump / bolt), or null when the fire sound
 *                       already contains it (e.g. the M2010's recordings)
 * @param cycleDelay     seconds after the shot at which {@code cycle} plays
 */
public record SoundSet(Supplier<SoundEvent> fire, Supplier<SoundEvent> fireSuppressed, Supplier<SoundEvent> reload,
                       Supplier<SoundEvent> reloadEmpty, Supplier<SoundEvent> shellInsert, Supplier<SoundEvent> cycle,
                       float cycleDelay) {

    public static Builder builder() {
        return new Builder();
    }

    /** A copy to change a few sounds (e.g. a family's set with this gun's own shot). */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.fire = fire;
        b.fireSuppressed = fireSuppressed;
        b.reload = reload;
        b.reloadEmpty = reloadEmpty;
        b.shellInsert = shellInsert;
        b.cycle = cycle;
        b.cycleDelay = cycleDelay;
        return b;
    }

    /**
     * Unset sounds fall back: suppressed -> fire, empty reload -> reload, shell insert -> reload;
     * fire and reload are required.
     */
    public static final class Builder {
        private Supplier<SoundEvent> fire, fireSuppressed, reload, reloadEmpty, shellInsert, cycle;
        private float cycleDelay = 0.3f;

        private Builder() {}

        public Builder fire(Supplier<SoundEvent> s) { this.fire = s; return this; }
        public Builder suppressed(Supplier<SoundEvent> s) { this.fireSuppressed = s; return this; }
        public Builder reload(Supplier<SoundEvent> s) { this.reload = s; return this; }
        public Builder reloadEmpty(Supplier<SoundEvent> s) { this.reloadEmpty = s; return this; }
        public Builder shellInsert(Supplier<SoundEvent> s) { this.shellInsert = s; return this; }
        public Builder cycle(Supplier<SoundEvent> s, float delaySeconds) { this.cycle = s; this.cycleDelay = delaySeconds; return this; }

        public SoundSet build() {
            Objects.requireNonNull(fire, "sound set: fire missing");
            Objects.requireNonNull(reload, "sound set: reload missing");
            return new SoundSet(fire, fireSuppressed != null ? fireSuppressed : fire, reload,
                    reloadEmpty != null ? reloadEmpty : reload, shellInsert != null ? shellInsert : reload, cycle, cycleDelay);
        }
    }
}
