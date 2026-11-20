package com.modernarmory.gun;

import com.modernarmory.ballistics.Cartridge;
import com.modernarmory.ballistics.Tracer;
import com.modernarmory.ballistics.ProjectileSpec;
import com.modernarmory.entity.BulletEntity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What leaves the muzzle for one round (strategy, chosen from the cartridge's data by
 * {@link #of(Cartridge)}): a single bullet, or a shot load of N pellets spread in a pattern.
 * Every projectile is a real {@link BulletEntity} with its own mass / BC / energy, so pellets
 * slow down, drop, penetrate and do damage each on their own ("damage per pellet").
 */
public interface ProjectileLoad {
    /**
     * Fires one round.
     *
     * @param aim          direction after the shooter's spread was applied
     * @param speed        muzzle velocity with attachment effects, m/s
     * @param gunId        gun item id (for mastery XP on kills)
     * @param tracer       the round is this tracer (glows until burnout), or null for ball
     */
    void launch(Level level, LivingEntity shooter, Vec3 origin, Vec3 aim, GunSpec spec, double speed, String gunId,
                Tracer tracer, RandomSource rand);

    /** One bullet along the aim line. */
    ProjectileLoad SINGLE = (level, shooter, origin, aim, spec, speed, gunId, tracer, rand) ->
            BulletEntity.fire(level, shooter, origin, aim, spec.projectile(), speed, gunId, tracer);

    /**
     * {@code pellets} pellets, each deviating from the aim line by a random angle within the
     * cartridge's pattern ({@code patternMoa} = full pattern diameter; Gaussian, ~68% of pellets
     * inside the half-diameter circle, like the shooter spread).
     */
    ProjectileLoad PELLETS = (level, shooter, origin, aim, spec, speed, gunId, tracer, rand) -> {
        Cartridge c = spec.cartridge();
        ProjectileSpec pellet = spec.projectile();
        double patternDeg = c.patternMoa() / 60.0;
        Vec3 dir = aim.normalize();
        // basis perpendicular to the aim line
        Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 down = right.cross(dir).normalize();
        for (int i = 0; i < c.pellets(); i++) {
            double angle = Math.toRadians(Math.abs(rand.nextGaussian()) * patternDeg * 0.5);
            double azimuth = rand.nextDouble() * Math.PI * 2;
            double t = Math.tan(angle);
            Vec3 d = dir.add(right.scale(t * Math.cos(azimuth))).add(down.scale(t * Math.sin(azimuth)));
            BulletEntity.fire(level, shooter, origin, d, pellet, speed, gunId, tracer);
        }
    };

    static ProjectileLoad of(Cartridge c) {
        return c.isShot() ? PELLETS : SINGLE;
    }
}
