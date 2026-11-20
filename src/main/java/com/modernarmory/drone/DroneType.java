package com.modernarmory.drone;

import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;

/**
 * One kind of drone (catalog: {@link Drones}). Pure data plus two strategies: what its payload does
 * ({@link DronePayload}: recon camera, charge) and what it does when the control link is lost
 * ({@link LinkLoss}). Speeds are m/s (1 block = 1 m), times seconds.
 *
 * @param id            registry path of the item (and its model files, tools/modelgen/guns/&lt;id&gt;.py)
 * @param name          display name
 * @param description   one line for the tooltip
 * @param massKg        take-off weight (shown in the tooltip)
 * @param cruiseSpeed   horizontal speed with the stick fully over
 * @param maxSpeed      horizontal speed with sprint held (sport / boost)
 * @param climbRate     vertical speed with jump / sneak held
 * @param accel         how fast it reaches the commanded speed (m/s^2)
 * @param enduranceSeconds   flight time on a full battery
 * @param rechargeSeconds    empty to full while the drone is carried in the inventory
 * @param linkRange     radio range of the real system (capped in game, see {@link DroneLink})
 * @param wallKeep      fraction of the signal left after each solid block between pilot and drone
 * @param health        hit points of the airframe
 * @param crashSpeed    impact speed that starts to damage the airframe
 * @param width         hitbox width (blocks)
 * @param height        hitbox height (blocks)
 * @param eyeHeight     camera lens height above the drone's feet (blocks)
 * @param renderScale   model units -> blocks for the flying drone (models are at the guns' 1 unit ~ 3.35 cm)
 * @param fov           camera field of view (degrees, unzoomed)
 * @param maxZoom       camera zoom (scroll wheel); 1 = fixed lens
 * @param cameraRoll    0 = gimbal-stabilised picture, 1 = camera hard-mounted (the view banks with the airframe)
 * @param gimbal        the model's {@code gimbal} bone follows the camera pitch
 * @param thrustToWeight  motors' full thrust / weight (hover throttle = 1 / this)
 * @param rateDeg       acro rotation rate at full stick (deg/s)
 * @param boostRateDeg  acro rate with sprint held (flips)
 * @param cameraTilt    hard-mounted camera's up-tilt on the frame (deg)
 * @param rotTau        how long the airframe takes to reach a commanded rotation rate (s; heavier = slower)
 * @param expo          stick expo 0..1: the rate is softened near the centre (rate = s x (1 - expo) + s^3 x expo)
 * @param flightMode    acro (rate) or stabilised sticks
 * @param rotorSound    looping rotor noise
 * @param payload       what the primary action / impacts do
 * @param linkLoss      autopilot when the signal is gone
 */
public record DroneType(String id, String name, String description, float massKg,
                        float cruiseSpeed, float maxSpeed, float climbRate, float accel,
                        float enduranceSeconds, float rechargeSeconds, float linkRange, float wallKeep,
                        float health, float crashSpeed, float width, float height, float eyeHeight, float renderScale,
                        float fov, float maxZoom, float cameraRoll, boolean gimbal,
                        float thrustToWeight, float rateDeg, float boostRateDeg, float cameraTilt, float rotTau, float expo,
                        FlightMode flightMode,
                        Supplier<SoundEvent> rotorSound, DronePayload payload, LinkLoss linkLoss) {

    /**
     * Quadratic drag coefficient (1/m): sized so that at full throttle, tilted as far as it can while holding
     * height, the drone tops out at {@link #maxSpeed()}.
     */
    public double drag() {
        return Math.sqrt(Math.max(0.2, thrustToWeight * thrustToWeight - 1)) * 9.81 / (maxSpeed * maxSpeed);
    }

    /** Fastest it can go at all: straight down with the throttle open (m/s). */
    public double terminalSpeed() {
        return Math.sqrt((thrustToWeight + 1) * 9.81 / drag());
    }

    /** Battery used per tick of powered flight at hover effort (full = 1). */
    public float drainPerTick() {
        return 1f / (enduranceSeconds * 20f);
    }

    public float rechargePerTick() {
        return 1f / (rechargeSeconds * 20f);
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    /** Fluent builder; {@link #build()} names anything left unset. */
    public static final class Builder {
        private final String id, name;
        private String description = "";
        private float massKg = -1, cruise = -1, max = -1, climb = -1, accel = -1, endurance = -1, recharge = 300,
                range = -1, wallKeep = 0.75f, health = -1, crash = 7, width = -1, height = -1, eye = -1, scale = -1,
                fov = 70, zoom = 1, roll = 0, tw = 2.5f, rate = 200, boostRate = 500, tilt = 0, rotTau = 0.1f, expo = 0.5f;
        private FlightMode mode = FlightMode.STABILIZED;
        private boolean gimbal;
        private Supplier<SoundEvent> sound;
        private DronePayload payload;
        private LinkLoss linkLoss = LinkLoss.HOVER;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder description(String d) { description = d; return this; }
        public Builder mass(float kg) { massKg = kg; return this; }
        /** Cruise / boosted horizontal speed and climb rate, m/s, and acceleration m/s^2. */
        public Builder speeds(float cruise, float max, float climb, float accel) {
            this.cruise = cruise; this.max = max; this.climb = climb; this.accel = accel; return this;
        }
        public Builder endurance(float minutes) { endurance = minutes * 60; return this; }
        public Builder recharge(float seconds) { recharge = seconds; return this; }
        /** Real radio range (m) and the fraction of signal kept per solid block in the way. */
        public Builder link(float rangeMeters, float wallKeep) { range = rangeMeters; this.wallKeep = wallKeep; return this; }
        public Builder airframe(float health, float crashSpeed) { this.health = health; crash = crashSpeed; return this; }
        /** Hitbox (blocks), lens height (blocks) and render scale (model units -> blocks). */
        public Builder size(float width, float height, float eyeHeight, float renderScale) {
            this.width = width; this.height = height; eye = eyeHeight; scale = renderScale; return this;
        }
        public Builder camera(float fov, float maxZoom, float roll, boolean gimbal) {
            this.fov = fov; zoom = maxZoom; this.roll = roll; this.gimbal = gimbal; return this;
        }
        /** Acro handling: thrust-to-weight, rotation rate and boosted (flip) rate in deg/s. */
        public Builder acro(float thrustToWeight, float rateDeg, float boostRateDeg) {
            tw = thrustToWeight; rate = rateDeg; boostRate = boostRateDeg; return this;
        }
        public Builder cameraTilt(float deg) { tilt = deg; return this; }
        /** Acro feel: rotation response time (s) and stick expo (0..1). */
        public Builder response(float rotTauSeconds, float stickExpo) { rotTau = rotTauSeconds; expo = stickExpo; return this; }
        public Builder flightMode(FlightMode m) { mode = m; return this; }
        public Builder rotorSound(Supplier<SoundEvent> s) { sound = s; return this; }
        public Builder payload(DronePayload p) { payload = p; return this; }
        public Builder onLinkLoss(LinkLoss l) { linkLoss = l; return this; }

        public DroneType build() {
            StringBuilder missing = new StringBuilder();
            if (massKg < 0) missing.append(" mass");
            if (cruise < 0) missing.append(" speeds");
            if (endurance < 0) missing.append(" endurance");
            if (range < 0) missing.append(" link");
            if (health < 0) missing.append(" airframe");
            if (width < 0) missing.append(" size");
            if (sound == null) missing.append(" rotorSound");
            if (payload == null) missing.append(" payload");
            if (!missing.isEmpty()) throw new IllegalStateException("drone " + id + " is missing:" + missing);
            return new DroneType(id, name, description, massKg, cruise, max, climb, accel, endurance, recharge, range, wallKeep,
                    health, crash, width, height, eye, scale, fov, zoom, roll, gimbal, tw, rate, boostRate, tilt, rotTau, expo, mode, sound,
                    payload, linkLoss);
        }
    }
}
