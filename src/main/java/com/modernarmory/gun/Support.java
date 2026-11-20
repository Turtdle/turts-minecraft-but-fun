package com.modernarmory.gun;

/**
 * How a gun is supported when fired (strategy for recoil and shooter spread by stance). Crouching is
 * how the player braces: it deploys a bipod (fitted attachment or integral) or gets down behind a
 * tripod. Server ({@code GunHandler}) and crosshair ({@code GunCrosshair}) use the same rules.
 */
public enum Support {
    /** Shoulder-fired. A fitted bipod attachment still deploys when crouched. */
    SHOULDER(false, 0.35f, 1f, 1f),
    /** Integral bipod (M249, M240, M107): crouch to deploy it, like the bipod attachment. */
    BIPOD(true, 0.35f, 1f, 1f),
    /**
     * Tripod-mounted heavy machine gun (M2A1, 38 kg gun on a 20 kg M3 tripod). Crouched = down on
     * the tripod: recoil is almost all soaked up (x0.15). Standing = trying to fire it off-hand:
     * x1.25 recoil (~7 deg per aimed .50 BMG shot, like an M107) and x2.5 spread, so it is only
     * usable in short bursts.
     */
    TRIPOD(true, 0.15f, 1.25f, 2.5f);

    /** Crouching braces the gun even without a bipod attachment. */
    private final boolean bracesWhenCrouched;
    /** Recoil factor when braced (crouched on a bipod / tripod). */
    private final float bracedRecoil;
    /** Recoil and spread factors when standing (not crouched). */
    private final float standingRecoil, standingSpread;

    /** Crouching without a brace still steadies the shot a little. */
    public static final float CROUCHED_RECOIL = 0.8f;
    /** Aimed spread when braced (shooter wobble taken out by the support). */
    public static final float BRACED_AIM_SPREAD = 0.5f;

    Support(boolean bracesWhenCrouched, float bracedRecoil, float standingRecoil, float standingSpread) {
        this.bracesWhenCrouched = bracesWhenCrouched;
        this.bracedRecoil = bracedRecoil;
        this.standingRecoil = standingRecoil;
        this.standingSpread = standingSpread;
    }

    /** Whether the gun is braced: crouched with an integral support or a fitted bipod. */
    public boolean braced(boolean crouching, boolean bipodFitted) {
        return crouching && (bracesWhenCrouched || bipodFitted);
    }

    /** Recoil multiplier for this stance. */
    public float recoil(boolean crouching, boolean bipodFitted) {
        if (braced(crouching, bipodFitted)) return bracesWhenCrouched ? bracedRecoil : SHOULDER.bracedRecoil;
        return crouching ? CROUCHED_RECOIL : standingRecoil;
    }

    /** Multiplier on the whole shooter spread for this stance (standing behind a tripod gun). */
    public float spread(boolean crouching) {
        return crouching ? 1f : standingSpread;
    }
}
