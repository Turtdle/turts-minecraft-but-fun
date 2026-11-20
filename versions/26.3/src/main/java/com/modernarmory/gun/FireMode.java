package com.modernarmory.gun;

/**
 * A selector position / action type. Each mode combines a {@link Trigger} behaviour (what one
 * trigger pull does) with whether the action is cycled by hand (bolt, pump, straight pull), which
 * matters for mastery XP and the "cycle" sound. A gun lists its modes in {@link GunSpec#fireModes()}
 * in switch order; the fire-mode key steps through them.
 */
public enum FireMode {
    SEMI("Semi", Trigger.SINGLE, false),
    BURST("Burst", Trigger.BURST, false),
    AUTO("Auto", Trigger.AUTO, false),
    /** One round per trigger press; the bolt is worked (the gun's cycle time) before the next. */
    BOLT("Bolt", Trigger.SINGLE, true),
    /** Pump action: the fore-end is racked (the cycle time) between shots. */
    PUMP("Pump", Trigger.SINGLE, true),
    /** Straight-pull bolt (e.g. M26 MASS): pulled straight back and pushed home between shots. */
    STRAIGHT_PULL("Straight-Pull", Trigger.SINGLE, true);

    private final String label;
    private final Trigger trigger;
    private final boolean manualCycle;

    FireMode(String label, Trigger trigger, boolean manualCycle) {
        this.label = label;
        this.trigger = trigger;
        this.manualCycle = manualCycle;
    }

    public String label() {
        return label;
    }

    public Trigger trigger() {
        return trigger;
    }

    /** The shooter cycles the action by hand between shots (bolt / pump / straight pull). */
    public boolean manualCycle() {
        return manualCycle;
    }

    /** What a trigger pull does: the strategy GunHandler asks before and after every round. */
    public enum Trigger {
        /** One round per pull. */
        SINGLE {
            @Override public boolean wantsShot(TriggerState t) { return t.held && t.semiReady; }
            @Override public void onShot(TriggerState t) { t.semiReady = false; }
        },
        /** {@link GunSpec#burstCount()} rounds per pull (stops early when released). */
        BURST {
            @Override public void onPress(TriggerState t, GunSpec spec) { t.burstLeft = spec.burstCount(); }
            @Override public boolean wantsShot(TriggerState t) { return t.held && t.burstLeft > 0; }
            @Override public void onShot(TriggerState t) { t.burstLeft--; }
        },
        /** Fires at the cyclic rate while held. */
        AUTO {
            @Override public boolean wantsShot(TriggerState t) { return t.held; }
        };

        /** The trigger was just pressed. */
        public void onPress(TriggerState t, GunSpec spec) {}

        /** Whether the gun should fire another round now (cyclic rate permitting). */
        public abstract boolean wantsShot(TriggerState t);

        /** A round was just fired. */
        public void onShot(TriggerState t) {}
    }
}
