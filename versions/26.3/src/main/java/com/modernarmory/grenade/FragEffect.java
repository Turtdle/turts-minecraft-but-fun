package com.modernarmory.grenade;

import com.modernarmory.Config;
import com.modernarmory.ballistics.ProjectileSpec;
import com.modernarmory.ballistics.Ricochet;
import com.modernarmory.entity.BulletEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * M67: the Comp B charge's blast (a small explosion, no crater unless the config allows it) plus steel
 * fragments from the body, each a real projectile in the ballistics model: ~0.6 g, ~1,500 m/s, very
 * draggy, so they are deadly close in and spent within tens of metres. Fragments can glance off stone or
 * metal once ({@link Ricochet#FRAGMENT}), so they are deadlier in a concrete room.
 *
 * <p>Lying on the ground, the grenade throws most of its fragments out sideways from its body (the steel wall is
 * nearly all of its surface; the ends are small), so the spray is densest from a little below to a little above
 * the horizon: lethal to ~5 m, wounding to ~15 m, like the real M67 (a person 2.5 m away takes ~8 fragments,
 * 5 m ~2, 15 m one now and then).
 */
public final class FragEffect implements GrenadeEffect {
    private static final double FRAGMENT_KG = 0.0006, FRAGMENT_SPEED = 1500;
    private static final ProjectileSpec FRAGMENT = new ProjectileSpec(FRAGMENT_KG, 0.012, 0,
            0.5 * FRAGMENT_KG * FRAGMENT_SPEED * FRAGMENT_SPEED, 9f, Ricochet.FRAGMENT);
    public static final int FRAGMENTS = 250;
    /** A grenade on the ground: the lowest fragment direction (vertical component), and the spray's centre and
     *  spread in degrees above the horizon. */
    public static final double GROUND_LOWEST_Z = -0.6;
    private static final double SPRAY_CENTRE_DEG = 5, SPRAY_SPREAD_DEG = 18;
    /** Explosion power: lethal to ~1 m, wounding out to ~6 m (with the fragments on top). */
    public static final float BLAST_RADIUS = 3.0f;

    @Override
    public void detonate(ServerLevel level, GrenadeEntity g) {
        LivingEntity owner = g.getOwner() instanceof LivingEntity l ? l : null;
        burst(level, g, owner, g.position().add(0, 0.3, 0), BLAST_RADIUS, FRAGMENTS, GROUND_LOWEST_Z);
    }

    /**
     * A fragmenting charge going off at {@code p}: the blast (an explosion of {@code blastRadius}, no crater
     * unless the config allows it) plus {@code fragments} steel fragments flown by the ballistics model.
     * {@code lowestZ} is the lowest vertical direction component fragments fly at: -1 = a full sphere, evenly
     * (a charge in the air); above that, a charge lying on the ground, whose fragments spray out sideways
     * ({@value #SPRAY_CENTRE_DEG} +/- {@value #SPRAY_SPREAD_DEG} degrees above the horizon, none lower than
     * {@code lowestZ}). Reused by anything with a frag charge (drone warheads).
     */
    public static void burst(ServerLevel level, Entity source, LivingEntity owner, Vec3 p, float blastRadius, int fragments,
                             double lowestZ) {
        burst(level, source, owner, p, blastRadius, fragments, lowestZ, Config.GRENADES_BREAK_BLOCKS.get());
    }

    /** As {@link #burst(ServerLevel, Entity, LivingEntity, Vec3, float, int, double)}, saying whether the blast breaks blocks. */
    public static void burst(ServerLevel level, Entity source, LivingEntity owner, Vec3 p, float blastRadius, int fragments,
                             double lowestZ, boolean breakBlocks) {
        level.explode(source, p.x, p.y, p.z, blastRadius, breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        RandomSource r = level.getRandom();
        for (int i = 0; i < fragments; i++) {
            double z = lowestZ <= -1 ? r.nextDouble() * 2 - 1 : sideways(r, lowestZ);
            double a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
            Vec3 dir = new Vec3(s * Math.cos(a), z, s * Math.sin(a));
            BulletEntity.fire(level, owner, p, dir, FRAGMENT, FRAGMENT_SPEED * (0.7 + 0.3 * r.nextDouble()), null, null);
        }
    }

    /** Vertical direction component of one fragment from a charge lying on the ground: mostly out sideways. */
    private static double sideways(RandomSource r, double lowestZ) {
        double lo = Math.asin(lowestZ), th;
        do th = Math.toRadians(SPRAY_CENTRE_DEG + SPRAY_SPREAD_DEG * r.nextGaussian());
        while (th < lo || th > Math.PI / 2);
        return Math.sin(th);
    }
}
