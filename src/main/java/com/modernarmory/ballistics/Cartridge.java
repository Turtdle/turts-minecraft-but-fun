package com.modernarmory.ballistics;

import java.util.Objects;

/**
 * Physical data for a cartridge (one round of ammunition) and the ammo item it is carried as.
 * Build one with {@link #builder(String, String)} and add it to {@link Cartridges}.
 *
 * <p>A cartridge fires either one bullet ({@code pellets == 1}) or a shot load of
 * {@code pellets} identical pellets that spread in a pattern (buckshot). For a shot load,
 * {@code bulletMassKg} is the whole payload (it sets recoil and the energy shown in tooltips),
 * while {@code pelletMassKg} and {@code bcG7} describe each pellet in flight.
 *
 * @param id                     catalog id, e.g. "556x45" (see docs/firearms/specs.json)
 * @param name                   display name
 * @param ammoItemId             registry path of the ammo item, e.g. "ammo_556"
 * @param caliberClass           bore / pressure class that calibre-specific attachments are matched against
 * @param bulletMassKg           projectile mass (shot: whole payload)
 * @param powderMassKg           propellant charge (used for recoil impulse)
 * @param bcG7                   G7 ballistic coefficient of one projectile (bullet or pellet), lb/in^2
 * @param fragmentationVelocity  impact speed (m/s) above which the bullet yaws and fragments
 *                               in tissue, wounding far more severely; 0 = never
 * @param pellets                projectiles per round: 1 = bullet, >1 = shot load
 * @param pelletMassKg           mass of one pellet (equals bulletMassKg for bullets)
 * @param patternMoa             full pattern diameter of a shot load, minutes of angle (0 for bullets)
 * @param tracer                 its tracer variant (its own ammo item), or null when none is fielded
 * @param stoppedBy              the lowest body-armour level that stops it at full power ({@link ArmorLevel})
 */
public record Cartridge(String id, String name, String ammoItemId, CaliberClass caliberClass, double bulletMassKg, double powderMassKg,
                        double bcG7, double fragmentationVelocity, int pellets, double pelletMassKg,
                        double patternMoa, Tracer tracer, ArmorLevel stoppedBy) {

    public boolean hasTracer() {
        return tracer != null;
    }

    /** Rifle gases leave the muzzle at roughly 1.75x bullet speed, a standard small-arms approximation. */
    private static final double GAS_VELOCITY_FACTOR = 1.75;

    public boolean isShot() {
        return pellets > 1;
    }

    /** Mass of one projectile in flight (a bullet, or one pellet of a shot load). */
    public double projectileMassKg() {
        return isShot() ? pelletMassKg : bulletMassKg;
    }

    /** Kinetic energy of the whole payload in joules at the given speed. */
    public double energy(double speed) {
        return 0.5 * bulletMassKg * speed * speed;
    }

    /** Kinetic energy of one projectile (bullet or pellet) in joules at the given speed. */
    public double projectileEnergy(double speed) {
        return 0.5 * projectileMassKg() * speed * speed;
    }

    /** Recoil impulse (N*s): payload momentum plus propellant gas momentum. */
    public double recoilImpulse(double muzzleVelocity) {
        return bulletMassKg * muzzleVelocity + powderMassKg * muzzleVelocity * GAS_VELOCITY_FACTOR;
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static final class Builder {
        private final String id, name;
        private String ammoItemId;
        private CaliberClass caliberClass;
        private double bulletMassKg = Double.NaN, powderMassKg = Double.NaN, bcG7 = Double.NaN;
        private double fragmentationVelocity;
        private int pellets = 1;
        private double pelletMassKg = Double.NaN, patternMoa;
        private Tracer tracer;
        private ArmorLevel stoppedBy;

        private Builder(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.ammoItemId = "ammo_" + id;
        }

        /** Registry path of the ammo item (default "ammo_" + id). */
        public Builder ammoItem(String itemId) { this.ammoItemId = itemId; return this; }

        /** Bore / pressure class (what suppressors, brakes and magazines are rated for). */
        public Builder caliber(CaliberClass c) { this.caliberClass = c; return this; }

        /** A single bullet: mass (kg) and G7 BC. */
        public Builder bullet(double massKg, double bcG7) {
            this.pellets = 1;
            this.bulletMassKg = massKg;
            this.bcG7 = bcG7;
            return this;
        }

        public Builder powder(double massKg) { this.powderMassKg = massKg; return this; }

        /**
         * A tracer variant with the same ballistics: {@code designation} (e.g. "M856"), glow colour
         * 0xRRGGBB, burnout distance in metres. Its ammo item is "&lt;ball item&gt;_tracer".
         */
        public Builder tracer(String designation, String name, int rgb, double burnoutM) {
            this.tracer = new Tracer(designation, name, null, rgb, burnoutM);
            return this;
        }

        /** Fragments in tissue above this impact speed (m/s). */
        public Builder fragmentsAbove(double speed) { this.fragmentationVelocity = speed; return this; }

        /** The lowest body-armour level that stops it at full power (NIJ, {@link ArmorLevel}). */
        public Builder stoppedBy(ArmorLevel level) { this.stoppedBy = level; return this; }

        /**
         * A shot load: {@code pellets} pellets of {@code pelletMassKg} each with G7 BC {@code pelletBcG7},
         * patterning {@code patternMoa} (full diameter). The payload mass is pellets x pellet mass.
         */
        public Builder shot(int pellets, double pelletMassKg, double pelletBcG7, double patternMoa) {
            this.pellets = pellets;
            this.pelletMassKg = pelletMassKg;
            this.bulletMassKg = pellets * pelletMassKg;
            this.bcG7 = pelletBcG7;
            this.patternMoa = patternMoa;
            return this;
        }

        public Cartridge build() {
            if (Double.isNaN(bulletMassKg) || Double.isNaN(bcG7)) throw new IllegalStateException("cartridge " + id + ": bullet() or shot() missing");
            if (Double.isNaN(powderMassKg)) throw new IllegalStateException("cartridge " + id + ": powder() missing");
            if (caliberClass == null) throw new IllegalStateException("cartridge " + id + ": caliber() missing");
            if (stoppedBy == null) throw new IllegalStateException("cartridge " + id + ": stoppedBy() missing");
            if (pellets < 1) throw new IllegalStateException("cartridge " + id + ": pellets must be >= 1");
            double pellet = pellets > 1 ? pelletMassKg : bulletMassKg;
            return new Cartridge(id, name, ammoItemId, caliberClass, bulletMassKg, powderMassKg, bcG7, fragmentationVelocity,
                    pellets, pellet, pellets > 1 ? patternMoa : 0,
                    tracer == null ? null : new Tracer(tracer.designation(), tracer.name(), ammoItemId + "_tracer",
                            tracer.rgb(), tracer.burnoutM()), stoppedBy);
        }
    }
}
