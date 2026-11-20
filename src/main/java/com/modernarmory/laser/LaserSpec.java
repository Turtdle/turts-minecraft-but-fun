package com.modernarmory.laser;

/**
 * A high-energy laser air-defence system's numbers (data; the one system is {@link #HELWS}). A laser
 * kills by heating one spot until something gives (a motor, the battery, the camera), so a kill takes the
 * beam held on the target for a while ("dwell"); the dwell grows with range (the beam spreads) and in rain
 * or snow (the air scatters it). The laser's thermal budget limits how long it can fire before it has to
 * cool down.
 *
 * @param name            display name
 * @param powerKw         beam power (HUD)
 * @param range           how far it engages (blocks)
 * @param sensorRange     how far the radar boxes drones (blocks)
 * @param fullPowerRange  full power out to here; it falls off linearly beyond
 * @param farPower        share of the power left at {@code range}
 * @param dwellTicks      dwell to burn down a scout-sized drone (6 hp) at full power; a smaller drone takes less
 * @param heatTicks       continuous firing before it overheats
 * @param coolTicks       cooling from fully hot to cold
 * @param overheatRelease heat (0..1) it has to cool to after an overheat before it can fire again
 * @param yawRate         turret slew rate (deg/s)
 * @param pitchRate       director slew rate (deg/s)
 * @param minElevation    lowest the director points (deg)
 * @param maxElevation    highest (deg)
 * @param engageCone      how far from the crosshair a click can lock a drone (deg)
 * @param rainPower       power left through rain or snow
 * @param thunderPower    power left in a thunderstorm
 */
public record LaserSpec(String name, float powerKw, double range, double sensorRange, double fullPowerRange, float farPower,
                        int dwellTicks, int heatTicks, int coolTicks, float overheatRelease, float yawRate, float pitchRate,
                        float minElevation, float maxElevation, float engageCone, float rainPower, float thunderPower) {

    /** Raytheon HELWS (the US Army's P-HEL): a 10 kW-class laser on a pallet, with its own radar and EO/IR sensor. */
    public static final LaserSpec HELWS = new LaserSpec("HELWS", 10, 192, 224, 64, 0.4f,
            50, 300, 300, 0.35f, 150, 100, -5, 85, 15, 0.55f, 0.4f);

    /** Share of the beam's power arriving at {@code distance} blocks. */
    public float rangePower(double distance) {
        if (distance <= fullPowerRange) return 1;
        double t = Math.min(1, (distance - fullPowerRange) / (range - fullPowerRange));
        return (float) (1 - t * (1 - farPower));
    }

    /** Dwell (ticks) to burn down a drone with {@code health} hit points at full power. */
    public float dwellFor(float health) {
        return dwellTicks * Math.max(0.5f, Math.min(1.5f, health / 6f));
    }
}
