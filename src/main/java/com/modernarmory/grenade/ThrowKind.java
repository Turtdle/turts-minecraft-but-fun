package com.modernarmory.grenade;

/**
 * How a grenade is thrown (CS-style controls, GrenadeHandler): hold left click and release = overhand,
 * hold right click and release = underhand, hold both and release both = medium.
 *
 * @param speed     release speed, m/s, the same for every grenade so each throw carries the same distance
 *                  (overhand ~37 m aimed 30 degrees up, a strong soldier's throw)
 * @param loft      how far above the look direction it is aimed (added to the look vector's y)
 * @param animation the throw animation (tools/modelgen/grenades.py)
 * @param under     whether the release follows the underhand animation's timing
 */
public enum ThrowKind {
    OVERHAND(20.0, 0.08, "throw", false),
    MEDIUM(10.5, 0.12, "throw", false),
    UNDERHAND(7.0, 0.25, "throw_under", true);

    public final double speed, loft;
    public final String animation;
    public final boolean under;

    ThrowKind(double speed, double loft, String animation, boolean under) {
        this.speed = speed;
        this.loft = loft;
        this.animation = animation;
        this.under = under;
    }
}
