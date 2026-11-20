package com.modernarmory.ballistics;

/**
 * One projectile in flight (a bullet, or one pellet of a shot load), as BulletEntity simulates it.
 *
 * @param massKg                 mass
 * @param bcG7                   G7 ballistic coefficient
 * @param fragmentationVelocity  impact speed above which it fragments (damage bonus); 0 = never
 * @param referenceEnergy        energy (J) at which it deals {@code baseDamage}: the nominal muzzle energy
 * @param baseDamage             Minecraft damage at {@code referenceEnergy}; scales with impact energy^0.75
 * @param ricochet               how it skips off hard surfaces
 * @param headshotDamage         least damage a hit to the head does at {@code referenceEnergy} (scales with energy
 *                               like {@code baseDamage}); 0 = just the headshot multiplier
 * @param stoppedBy              the lowest body-armour level that stops it at full power
 */
public record ProjectileSpec(double massKg, double bcG7, double fragmentationVelocity, double referenceEnergy,
                         float baseDamage, Ricochet ricochet, float headshotDamage, ArmorLevel stoppedBy) {

    /** No headshot floor; stopped by soft armour (fragments). */
    public ProjectileSpec(double massKg, double bcG7, double fragmentationVelocity, double referenceEnergy, float baseDamage,
                          Ricochet ricochet) {
        this(massKg, bcG7, fragmentationVelocity, referenceEnergy, baseDamage, ricochet, 0, ArmorLevel.IIIA);
    }

    /** Ricochets like a bullet ({@link Ricochet#BULLET}), no headshot floor. */
    public ProjectileSpec(double massKg, double bcG7, double fragmentationVelocity, double referenceEnergy, float baseDamage) {
        this(massKg, bcG7, fragmentationVelocity, referenceEnergy, baseDamage, Ricochet.BULLET);
    }

    /**
     * One projectile of this cartridge fired at {@code muzzleVelocity}, dealing {@code baseDamage} at the muzzle.
     * A bullet is lethal to the head at point blank ({@link DamageModel#LETHAL_HEADSHOT}); a pellet is not.
     */
    public static ProjectileSpec of(Cartridge c, double muzzleVelocity, float baseDamage) {
        return new ProjectileSpec(c.projectileMassKg(), c.bcG7(), c.fragmentationVelocity(),
                c.projectileEnergy(muzzleVelocity), baseDamage, Ricochet.BULLET, c.isShot() ? 0 : DamageModel.LETHAL_HEADSHOT,
                c.stoppedBy());
    }
}
