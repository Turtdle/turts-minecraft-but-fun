package com.modernarmory.ballistics;

/**
 * How a projectile skips off hard surfaces (stone, metal: Penetration#canRicochet).
 *
 * @param maxBounces   how many times it can ricochet in its flight
 * @param maxGrazeDeg  steepest angle between its path and the surface that still skips it off
 * @param speedKept    share of its speed a skim (0 deg) keeps
 * @param speedKeptMax share of its speed a hit at {@code maxGrazeDeg} keeps (steeper hits lose more)
 */
public record Ricochet(int maxBounces, double maxGrazeDeg, double speedKept, double speedKeptMax) {
    /** Bullets: only grazing hits skip, as often as they keep grazing. */
    public static final Ricochet BULLET = new Ricochet(Integer.MAX_VALUE, 12, 0.55, 0.55);
    /** Grenade fragments: small, hard and irregular, they glance off at steeper angles, but only once. */
    public static final Ricochet FRAGMENT = new Ricochet(1, 40, 0.5, 0.25);

    /** Share of its speed a projectile keeps after skipping off at {@code grazeDeg}. */
    public double speedAfter(double grazeDeg) {
        return speedKept + (speedKeptMax - speedKept) * Math.min(1, grazeDeg / maxGrazeDeg);
    }
}
