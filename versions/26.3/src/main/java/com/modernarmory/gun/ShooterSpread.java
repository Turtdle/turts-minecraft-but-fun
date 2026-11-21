package com.modernarmory.gun;

import net.minecraft.world.entity.player.Player;

/**
 * Shooter-induced spread rules shared by the server (where rounds actually go, GunHandler) and
 * the client crosshair (which draws the same cone, GunCrosshair). Degrees, used as a cone diameter.
 */
public final class ShooterSpread {
    /** Extra spread while airborne / sprinting / walking (from the hip; aimed walking is steadier). */
    public static final double AIRBORNE = 10.0, SPRINTING = 2.5, WALKING_HIP = 1.0, WALKING_AIMED = 0.4; // jumping: a 10 degree cone, aimed or not
    /** Crouching multiplies the whole spread by this. */
    public static final double CROUCH = 0.7;
    /** Horizontal movement per tick (blocks) above which the shooter counts as walking. */
    private static final double WALK_THRESHOLD = 0.05;

    /** Extra spread from the shooter's movement (before mastery and crouching). */
    public static double movement(Player p, boolean aiming) {
        double moved = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
        if (!p.onGround()) return AIRBORNE;
        if (p.isSprinting()) return SPRINTING;
        if (moved > WALK_THRESHOLD) return aiming ? WALKING_AIMED : WALKING_HIP;
        return 0;
    }

    private ShooterSpread() {}
}
