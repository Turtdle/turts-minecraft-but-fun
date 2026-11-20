package com.modernarmory.ballistics;

/**
 * The standard G7 drag function (drag coefficient vs. Mach number) used in real external
 * ballistics for long boat-tail bullets such as the 5.56 M855.
 *
 * <p>A bullet's ballistic coefficient (BC) says how it compares to the G7 reference projectile.
 * With quadratic drag, deceleration is
 * <pre>  a = rho * v^2 * Cd_G7(M) * PI / (8 * BC)</pre>
 * with BC in kg/m^2 (1 lb/in^2 = 703.0696 kg/m^2).
 */
public final class G7Drag {
    public static final double LB_PER_IN2_TO_KG_PER_M2 = 703.0696;
    public static final double SPEED_OF_SOUND = 343.0; // m/s at 20 C, sea level
    public static final double AIR_DENSITY = 1.225; // kg/m^3, sea level
    public static final double WATER_DENSITY = 1000.0;

    // Mach, Cd (standard G7 table)
    private static final double[] MACH = {
            0.00, 0.50, 0.60, 0.65, 0.70, 0.725, 0.75, 0.775, 0.80, 0.825, 0.85, 0.875, 0.90, 0.925,
            0.95, 0.975, 1.00, 1.025, 1.05, 1.075, 1.10, 1.125, 1.15, 1.20, 1.25, 1.30, 1.35, 1.40,
            1.50, 1.55, 1.60, 1.65, 1.70, 1.75, 1.80, 1.85, 1.90, 1.95, 2.00, 2.10, 2.20, 2.30,
            2.40, 2.50, 2.60, 2.70, 2.80, 2.90, 3.00, 3.50, 4.00, 5.00};
    private static final double[] CD = {
            0.1198, 0.1194, 0.1194, 0.1197, 0.1202, 0.1207, 0.1215, 0.1226, 0.1242, 0.1266, 0.1306, 0.1368, 0.1464, 0.1660,
            0.2054, 0.2993, 0.3803, 0.4015, 0.4043, 0.4034, 0.4014, 0.3987, 0.3955, 0.3884, 0.3810, 0.3732, 0.3657, 0.3580,
            0.3440, 0.3376, 0.3315, 0.3260, 0.3209, 0.3160, 0.3117, 0.3078, 0.3042, 0.3010, 0.2980, 0.2922, 0.2864, 0.2807,
            0.2752, 0.2697, 0.2643, 0.2588, 0.2533, 0.2479, 0.2424, 0.2165, 0.1952, 0.1618};

    private G7Drag() {}

    /** G7 drag coefficient at the given Mach number (linear interpolation). */
    public static double cd(double mach) {
        if (mach <= MACH[0]) return CD[0];
        for (int i = 1; i < MACH.length; i++) {
            if (mach <= MACH[i]) {
                double t = (mach - MACH[i - 1]) / (MACH[i] - MACH[i - 1]);
                return CD[i - 1] + t * (CD[i] - CD[i - 1]);
            }
        }
        return CD[CD.length - 1];
    }

    /**
     * Quadratic-drag constant k (1/m) such that dv/dt = -k v^2 at the current speed.
     * @param bcG7 ballistic coefficient in lb/in^2 (as published)
     */
    public static double dragK(double speed, double bcG7, double density) {
        double bcSi = bcG7 * LB_PER_IN2_TO_KG_PER_M2;
        return density * cd(speed / SPEED_OF_SOUND) * Math.PI / (8.0 * bcSi);
    }
}
