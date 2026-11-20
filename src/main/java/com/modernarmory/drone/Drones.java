package com.modernarmory.drone;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.modernarmory.registry.ModSounds;

/**
 * The drones, with real-world numbers. Adding one is an entry here, a model (tools/modelgen/guns/&lt;id&gt;.py),
 * sounds, a recipe and a lang line; a new kind of payload is a {@link DronePayload}, a new failsafe a
 * {@link LinkLoss}. The item, entity, creative-tab entry, controller pairing, camera view and HUD all come
 * from the catalog.
 *
 * Models are at the guns' scale (1 unit ~ 3.35 cm); in the world they are drawn x1.4 (scout) / x1.3 (FPV)
 * life size so they read at a distance.
 *
 * Both fly stabilised ({@link FlightMode#STABILIZED}): WASD and jump / sneak ask for a velocity, the mouse turns
 * the heading and tilts the camera, the flight controller tilts and throttles the airframe to deliver it.
 * {@code .flightMode(FlightMode.ACRO)} gives a type acro (rate) controls instead: no self-levelling, flips.
 */
public final class Drones {
    private static final Map<String, DroneType> BY_ID = new LinkedHashMap<>();
    private static final float UNIT = 0.0335f; // model unit -> m

    /**
     * Skydio X2D (US Army Short Range Reconnaissance): 1.3 kg, 35 min, 6 km link, colour + FLIR thermal gimbal
     * camera with digital zoom. Loses the link: returns to the pilot. Speeds are game-tuned above the real
     * 36 mph (16 m/s): 14 m/s cruise, 22 m/s with sprint.
     */
    public static final DroneType SCOUT = add(DroneType.builder("drone_scout", "Skydio X2D Scout Drone")
            .description("Recon quadcopter. Left click in the camera view tags targets for the pilot.")
            .mass(1.3f).speeds(14, 22, 6, 12).endurance(35).recharge(300)
            .link(6000, 0.9f).airframe(6, 7)
            .size(0.85f, 0.2f, 0.07f, UNIT * 1.4f)
            .camera(70, 8, 0, true)
            .acro(2.2f, 180, 400)                     // a heavy, docile airframe: gentle rates
            .response(0.15f, 0.55f)                   // big props: slow to spin up a rotation, soft around centre
            .rotorSound(ModSounds.DRONE_ROTOR_SCOUT)
            .payload(new ReconPayload(120, 3, 15))
            .onLinkLoss(LinkLoss.RETURN_HOME)
            .build());

    /**
     * 7-inch FPV quadcopter with a fragmentation charge (the attritable kind now bought in numbers):
     * ~1.5 kg loaded, ~8 min, wide-angle FPV camera hard-mounted and tilted up, failsafe drop on link loss.
     * Racing-quad speeds: 22 m/s cruise, 40 m/s (~145 km/h) with sprint. Charge: a heavier blast than an M67
     * (radius 5, a little above TNT) that breaks blocks (config droneChargeBreaksBlocks), 120 fragments,
     * armed after 3 s of flight, contact fuze from 4 m/s.
     */
    public static final DroneType FPV = add(DroneType.builder("drone_fpv", "FPV Strike Drone")
            .description("Fast FPV quadcopter with a fragmentation charge. Detonates on impact or on left click.")
            .mass(1.5f).speeds(22, 40, 12, 30).endurance(8).recharge(240)
            .link(5000, 0.88f).airframe(3, 9)
            .size(0.5f, 0.18f, 0.1f, UNIT * 1.3f)
            .camera(105, 1, 0.6f, false)
            .acro(4.0f, 300, 720)                     // 7-inch freestyle / strike quad: ~4:1 loaded, fast rates
            .response(0.07f, 0.6f)                    // light and punchy, with the expo FPV pilots fly on
            .cameraTilt(20)                           // camera tilted up on the frame, as FPV pilots fly fast
            .rotorSound(ModSounds.DRONE_ROTOR_FPV)
            .payload(new ChargePayload(5.0f, 120, 3, 4, true))
            .onLinkLoss(LinkLoss.FAILSAFE_DROP)
            .build());

    private static DroneType add(DroneType t) {
        BY_ID.put(t.id(), t);
        return t;
    }

    public static Collection<DroneType> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static DroneType byId(String id) {
        return BY_ID.get(id);
    }

    private Drones() {}
}
