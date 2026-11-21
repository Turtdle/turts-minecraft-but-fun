package com.modernarmory.gun;

import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.MasteryHandler;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Everything about one shot that both sides compute: the shooter's client predicts the shot the moment
 * the trigger is pulled (client.ShotPredictor), the server fires the real one when the client's request
 * arrives (server.GunHandler). Both seed a {@link RandomSource} with the same number and draw from it in
 * the same order (this plan first, then the projectile load's pellets), so the bullet you see is the
 * bullet the server simulates.
 */
public final class ShotMath {
    /** The rolled parts of a shot: where it goes, the report's pitch, and the camera kick (degrees). */
    public record Plan(Vec3 dir, float soundPitch, float kick, float side) {}

    /**
     * Spread for {@code p}'s next shot (cone diameter, degrees): the gun and ammo's mechanical accuracy plus
     * the shooter's (hip or aimed, moving, crouching, stance; mastery tightens it).
     */
    public static double spreadDeg(Player p, GunItem gun, ItemStack stack, boolean aiming) {
        GunSpec spec = gun.spec();
        GunStats stats = GunStats.of(gun, stack);
        Support support = spec.support();
        boolean crouching = p.isCrouching();
        boolean braced = support.braced(crouching, stats.bipod());
        var mastery = MasteryHandler.effects(p, gun);
        double spread = spec.mechanicalMoa() / 60.0
                + (aiming ? spec.adsSpreadDeg() * (braced ? Support.BRACED_AIM_SPREAD : 1)
                          : spec.hipSpreadDeg() * stats.hipSpread() * mastery.hipSpread()); // mastery: tighter from the hip
        double moving = ShooterSpread.movement(p, aiming);
        if (moving > 0) spread += moving * mastery.movingSpread(); // mastery: steadier on the move
        if (crouching) spread *= ShooterSpread.CROUCH;
        return spread * support.spread(crouching); // e.g. a tripod gun fired standing
    }

    /** Rolls the shot along (xRot, yRot) with {@code spread} from {@code rand}. */
    public static Plan plan(Player p, GunItem gun, ItemStack stack, boolean aiming, float xRot, float yRot, double spread,
                            RandomSource rand) {
        double angle = Math.abs(rand.nextGaussian()) * spread * 0.5;
        double azimuth = rand.nextDouble() * Math.PI * 2;
        Vec3 dir = Vec3.directionFromRotation(xRot + (float) (angle * Math.sin(azimuth)), yRot + (float) (angle * Math.cos(azimuth)));
        float pitch = 0.95f + rand.nextFloat() * 0.1f;
        // Recoil from free-recoil energy (~5 J for an M4), less when aiming, scaled by attachments
        // and by stance: a bipod / tripod deployed (crouching) soaks up most of it (gun.Support).
        GunSpec spec = gun.spec();
        float kick = (float) (spec.recoilEnergy() * 0.14) * GunStats.of(gun, stack).recoil();
        if (aiming) kick *= 0.8f;
        kick *= spec.support().recoil(p.isCrouching(), GunStats.of(gun, stack).bipod());
        float side = (rand.nextFloat() - 0.45f) * kick * 0.6f;
        return new Plan(dir, pitch, kick, side);
    }

    /**
     * Where the muzzle smoke puff goes: ~1.3 m ahead of the eye, under the line of sight when
     * aiming; from the hip, out to the gun-hand side and lower, where the muzzle is, so the puff
     * does not sit on the crosshair and hide the impacts.
     */
    public static Vec3 muzzleSmoke(Player p, boolean aiming) {
        Vec3 look = p.getLookAngle();
        Vec3 muzzle = p.getEyePosition().add(look.scale(1.3)).add(0, -0.15, 0);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (aiming || right.lengthSqr() < 1e-6) return muzzle;
        double side = p.getMainArm() == HumanoidArm.RIGHT ? 0.25 : -0.25;
        return muzzle.add(right.normalize().scale(side)).add(0, -0.1, 0);
    }

    private ShotMath() {}
}
