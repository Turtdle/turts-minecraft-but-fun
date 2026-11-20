package com.modernarmory.ballistics;

/**
 * The one damage scale of the mod: Minecraft damage grows with kinetic energy to the power
 * {@value #EXPONENT}, anchored on the M4 (M855 at 880 m/s = 1,557 J deals {@value #REFERENCE_DAMAGE}).
 * A gun's damage at the muzzle defaults to {@link #forEnergy(double)} of one projectile (bullet
 * or pellet), and {@code BulletEntity} scales it with the energy left at impact by the same law,
 * so heavier cartridges hit harder everywhere: a 9 mm pistol ~3.7, 7.62 NATO ~12, .338 Lapua ~23,
 * a .50 BMG ~48, one 00 buck pellet ~2.2 (x 9 pellets).
 */
public final class DamageModel {
    /** Damage grows with energy^EXPONENT (sub-linear: twice the energy is ~1.7x the damage). */
    public static final double EXPONENT = 0.75;
    /** Damage of the reference round (M4, M855). */
    public static final float REFERENCE_DAMAGE = 8.0f;
    /** Muzzle energy of the reference round: M855 (4.02 g) at 880 m/s, J. */
    public static final double REFERENCE_ENERGY = 0.5 * 0.00402 * 880.0 * 880.0;

    /**
     * Headshot damage at the muzzle that no bullet falls short of: a bullet to the head kills an unarmoured
     * 20-health target at point blank whatever the gun, as a 9 mm does in life. It shrinks with the energy left
     * like all damage, so pistols lose the one-shot first with range. Pellets don't get it (one buckshot
     * pellet is not a bullet). A little over 20 so a few metres of flight still kill.
     */
    public static final float LETHAL_HEADSHOT = 21.0f;

    /** Muzzle damage of one projectile with this kinetic energy (J). */
    public static float forEnergy(double joules) {
        return REFERENCE_DAMAGE * (float) Math.pow(joules / REFERENCE_ENERGY, EXPONENT);
    }

    /** Damage at impact: the muzzle damage scaled by the energy remaining. */
    public static float atImpact(float muzzleDamage, double impactEnergy, double muzzleEnergy) {
        return muzzleDamage * (float) Math.pow(impactEnergy / muzzleEnergy, EXPONENT);
    }

    /**
     * Damage of a hit to the head: the body damage times the headshot multiplier, but never under the
     * projectile's headshot floor scaled by the energy left ({@code headshotFloor} 0 = no floor).
     */
    public static float headshot(float bodyDamage, float multiplier, float headshotFloor, double impactEnergy, double muzzleEnergy) {
        return Math.max(bodyDamage * multiplier, atImpact(headshotFloor, impactEnergy, muzzleEnergy));
    }

    private DamageModel() {}
}
