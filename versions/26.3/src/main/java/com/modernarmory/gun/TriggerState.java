package com.modernarmory.gun;

/** A shooter's trigger finger, as the {@link FireMode.Trigger} strategies see it (server side). */
public final class TriggerState {
    /** Trigger currently held. */
    public boolean held;
    /** A single-shot mode may fire (reset by every new press). */
    public boolean semiReady = true;
    /** Rounds left in the current burst. */
    public int burstLeft;

    /** A new trigger press with this fire mode. */
    public void press(FireMode mode, GunSpec spec) {
        semiReady = true;
        mode.trigger().onPress(this, spec);
    }

    /** Click: nothing fired (empty gun); wait for the next press. */
    public void dryFire() {
        semiReady = false;
        burstLeft = 0;
    }

    public void stopBurst() {
        burstLeft = 0;
    }
}
