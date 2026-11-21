package com.modernarmory.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.AcroSticks;
import com.modernarmory.drone.DroneControl;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.drone.DroneInput;
import com.modernarmory.drone.DroneType;
import com.modernarmory.network.DroneActionPayload;
import com.modernarmory.network.DroneCorrectPayload;
import com.modernarmory.network.DroneSpotPayload;
import com.modernarmory.network.DroneViewPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pilot's side of flying a drone: the view switches to the drone's camera (the body stays where it
 * is), the movement keys and mouse become the sticks, left click is the drone's action, the scroll wheel
 * zooms, the exit key (B) brings you back. The vanilla HUD is replaced by the camera HUD ({@link DroneHud});
 * vanilla attack / use / movement do nothing to the body meanwhile. Targets the scout tags glow for the
 * pilot only.
 */
public final class DronePilot {
    /** Shared-flags data slot (Entity.DATA_SHARED_FLAGS_ID) and its glowing bit: set on the pilot's client only. */
    private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    private static final int GLOW_BIT = 1 << 6;
    /** HUD layers left on while looking through a drone (everything else belongs to the body). */
    private static final java.util.Set<ResourceLocation> KEEP_LAYERS = java.util.Set.of(VanillaGuiLayers.CHAT,
            VanillaGuiLayers.DEBUG_OVERLAY, VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.TITLE, VanillaGuiLayers.TAB_LIST,
            VanillaGuiLayers.SAVING_INDICATOR, VanillaGuiLayers.OVERLAY_MESSAGE, ModernArmory.id("drone_hud"), ModernArmory.id("flashbang"));

    private static int droneId = -1;
    private static int pendingId = -1, pendingTicks;
    /**
     * The body's perspective (F5). In the drone view every perspective is the drone's camera, so F5 does not
     * change the view; presses cycle this instead, and the body comes back in it.
     */
    private static CameraType bodyCamera;
    private static float savedYaw, savedPitch, zoom = 1;
    private static boolean savedCrouch, attackWas, exitWas;
    private static long feedLostNanos;
    /**
     * Acro: the throttle stick (0..1, stays where it is left), the gimbal tilt, and the yaw the mouse is
     * re-centred on every tick (its movement since then is the stick input).
     */
    private static float throttleStick, gimbalPitch, mouseYaw;
    /** Acro: where A/D have pushed the yaw stick (-1..1); keys move it over time, like a thumb. */
    private static float stickYaw;

    /** Strike camera (after an FPV detonation): ticks left and the camera entity. */
    private static int strikeTicks;
    private static net.minecraft.world.entity.Entity strikeCam;
    private static int warnTicks;
    /** Spotted entity id -> client game time the tag runs out. */
    private static final Map<Integer, Long> SPOTTED = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(DronePilot::onTickPre);
        NeoForge.EVENT_BUS.addListener(DronePilot::onTickPost);
        NeoForge.EVENT_BUS.addListener(DronePilot::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DronePilot::onInteraction);
        NeoForge.EVENT_BUS.addListener(DronePilot::onRenderHand);
        NeoForge.EVENT_BUS.addListener(DronePilot::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(DronePilot::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(DronePilot::onFov);
        NeoForge.EVENT_BUS.addListener(DronePilot::onScroll);
        NeoForge.EVENT_BUS.addListener(DronePilot::onTurn);
        NeoForge.EVENT_BUS.addListener(DronePilot::onLogout);
    }

    // ---- state read by the HUD / autotest ---------------------------------------------------------

    /** Looking through a drone, or at its strike camera: the body is out of the player's hands. */
    public static boolean active() {
        return droneId >= 0 || strikeTicks > 0;
    }

    /** The drone we are looking through, or null. */
    public static DroneEntity drone() {
        Minecraft mc = Minecraft.getInstance();
        return active() && mc.level != null && mc.level.getEntity(droneId) instanceof DroneEntity d ? d : null;
    }

    public static float zoom() {
        return zoom;
    }

    /** Autotest: set the camera zoom as the scroll wheel would. */
    static void setZoom(float z) {
        DroneEntity d = drone();
        if (d != null) zoom = Mth.clamp(z, 1, d.type().maxZoom());
    }

    /** 0..1: the "feed lost" static after the camera died (crash, detonation), fading out. */
    static float feedLost() {
        float t = (System.nanoTime() - feedLostNanos) / 1e9f;
        return t < 0.9f ? 1 - t / 0.9f : 0;
    }

    static int spottedCount() {
        return SPOTTED.size();
    }

    public static boolean isSpotted(Entity e) {
        return SPOTTED.containsKey(e.getId());
    }

    // ---- server messages ------------------------------------------------------------------------

    public static void onView(DroneViewPayload p) {
        if (p.drone() < 0) {
            leave(false, false);
        } else {
            pendingId = p.drone();
            pendingTicks = 40; // the drone's spawn packet may still be on its way
            tryEnter();
        }
    }

    public static void onSpot(DroneSpotPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long until = mc.level.getGameTime() + p.ticks();
        for (int id : p.entities()) SPOTTED.put(id, until);
    }

    public static void onCorrect(DroneCorrectPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(p.drone()) instanceof DroneEntity d) d.correctTo(p.pos());
    }

    // ---- entering / leaving ---------------------------------------------------------------------

    private static void tryEnter() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.level.getEntity(pendingId) instanceof DroneEntity d)) return;
        if (strikeTicks > 0) { // a new drone while the last one's blast is still on screen
            strikeTicks = 0;
            strikeCam = null;
        } else if (droneId < 0) {
            savedYaw = player.getYRot();
            savedPitch = player.getXRot();
            savedCrouch = player.isShiftKeyDown();
        }
        droneId = d.getId();
        pendingId = -1;
        zoom = 1;
        // the mouse now turns the camera: start from where the drone faces
        // acro: the mouse is read as movement from here (re-centred every tick); stabilised: it is the look
        mouseYaw = d.getYRot();
        setLook(player, mouseYaw, d.type().flightMode() == com.modernarmory.drone.FlightMode.ACRO ? 0 : d.cameraPitch());
        gimbalPitch = d.cameraPitch();
        throttleStick = d.onGround() || !d.motorsOn() ? 0 : 1 / d.type().thrustToWeight(); // pick it up hovering
        stickYaw = 0;
        if (bodyCamera == null) bodyCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON); // the drone's camera, whatever the perspective
        mc.setCameraEntity(d);
        attackWas = true; // the click that started it is not a drone action
        exitWas = true;
        ModernArmory.LOGGER.debug("drone view: {} ({})", d.getId(), d.type().id());
    }

    /** Back to the body. {@code tellServer}: the pilot chose to leave; {@code feedLost}: the camera died. */
    static void leave(boolean tellServer, boolean feedLost) {
        if (droneId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        DroneEntity d = drone();
        if (d != null) d.pilotInput = null;
        droneId = -1;
        if (tellServer && mc.getConnection() != null) PacketDistributor.sendToServer(new DroneActionPayload(DroneControl.ACTION_EXIT));
        if (strikeTicks > 0) return; // the strike camera is still showing the blast: back to the body when it ends
        restoreBody(feedLost);
    }

    /** The view goes back to the body: camera, look, stance and input as they were before flying. */
    private static void restoreBody(boolean feedLost) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            setLook(mc.player, savedYaw, savedPitch);
        }
        if (bodyCamera != null) mc.options.setCameraType(bodyCamera); // the perspective the pilot cycled F5 to
        bodyCamera = null;
        if (feedLost) feedLostNanos = System.nanoTime();
    }

    // ---- strike camera ----------------------------------------------------------------------

    /** The FPV's charge went off: watch it from a vantage point for a moment, if there is one. */
    public static void onBlast(com.modernarmory.network.DroneBlastPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (droneId < 0 || mc.level == null || mc.player == null) return;
        strikeCam = DroneStrikeCam.create(mc, p);
        if (strikeCam == null) return; // nowhere sees it: straight back to the body (with the lost-feed static)
        strikeTicks = DroneStrikeCam.TICKS;
        mc.setCameraEntity(strikeCam);
    }

    /** True while the strike camera shows a blast. */
    public static boolean strikeCam() {
        return strikeTicks > 0;
    }

    private static void endStrikeCam() {
        strikeTicks = 0;
        strikeCam = null;
        if (droneId >= 0) leave(true, false);
        else restoreBody(false);
    }

    private static void setLook(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    // ---- ticking --------------------------------------------------------------------------------

    private static void onTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (pendingId >= 0) {
            tryEnter();
            if (pendingId >= 0 && --pendingTicks <= 0) pendingId = -1;
        }
        if (active()) { // F5 in the drone view: the camera stays put, the presses are kept for the body
            while (mc.options.keyTogglePerspective.consumeClick()) bodyCamera = (bodyCamera != null ? bodyCamera : CameraType.FIRST_PERSON).cycle();
            if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
                bodyCamera = mc.options.getCameraType(); // changed some other way: take it for the body too
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
        if (strikeTicks > 0) {
            boolean skip = false;
            while (KeyBindings.DRONE_EXIT.consumeClick()) skip = true;
            while (mc.options.keyAttack.consumeClick()) { /* nothing to do */ }
            while (mc.options.keyUse.consumeClick()) { /* the body's hands are busy */ }
            if (skip || --strikeTicks <= 0 || !player.isAlive()) endStrikeCam();
            else if (mc.getCameraEntity() != strikeCam) mc.setCameraEntity(strikeCam);
            return;
        }
        if (!active()) return;
        DroneEntity d = drone();
        if (d == null || d.isRemoved()) {
            leave(true, true);
            return;
        }
        if (!player.isAlive()) {
            leave(true, false);
            return;
        }
        if (mc.getCameraEntity() != d) mc.setCameraEntity(d);

        boolean free = mc.screen == null;
        boolean exit = false, attack = false;
        while (KeyBindings.DRONE_EXIT.consumeClick()) exit = true;
        while (mc.options.keyAttack.consumeClick()) attack = true;
        while (mc.options.keyUse.consumeClick()) { /* the body's hands are busy */ }
        while (mc.options.keyDrop.consumeClick()) { /* nothing to drop from the controller */ }
        boolean exitDown = KeyBindings.DRONE_EXIT.isDown(), attackDown = mc.options.keyAttack.isDown();
        exit |= exitDown && !exitWas;
        attack |= attackDown && !attackWas;
        exitWas = exitDown;
        attackWas = attackDown;
        if (free && exit) {
            leave(true, false);
            return;
        }
        if (free && attack) PacketDistributor.sendToServer(new DroneActionPayload(DroneControl.ACTION_PRIMARY));

        var o = mc.options;
        float fwd = free ? (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0) : 0;
        float strafe = free ? (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0) : 0;
        float lift = free ? (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0) : 0;
        boolean boost = free && o.keySprint.isDown();
        if (d.type().flightMode() == com.modernarmory.drone.FlightMode.ACRO) {
            // acro: the mouse is the right stick (pitch / roll: the drone turns while it moves and keeps its attitude
            // when it stops), jump / sneak move the throttle stick, A/D yaw, W/S tilt a gimbal; nothing levels it
            float sens = CrosshairConfig.DRONE_MOUSE.get().floatValue(); // Mods > Turt's Armory > Config > All settings
            float rollDeg = free ? Mth.wrapDegrees(player.getYRot() - mouseYaw) * sens : 0, pitchDeg = free ? player.getXRot() * sens : 0;
            setLook(player, mouseYaw, 0);
            throttleStick = Mth.clamp(throttleStick + lift * THROTTLE_PER_TICK, 0, 1);
            stickYaw = towards(stickYaw, strafe);
            if (d.type().gimbal()) gimbalPitch = Mth.clamp(gimbalPitch - fwd * GIMBAL_PER_TICK, -30, 90);
            AcroSticks sticks = testPilot != null ? testPilot.apply(d)
                    : new AcroSticks(0, 0, stickYaw, pitchDeg, rollDeg, 0, throttleStick, boost, gimbalPitch);
            if (testPilot != null) throttleStick = sticks.throttle();
            d.pilotSticks = sticks;
        } else {
            d.pilotInput = testInput != null ? testInput.apply(d) : new DroneInput(fwd, strafe, lift, boost, player.getYRot(), player.getXRot());
        }

        // the controller nags when the battery is low or the link is going
        if (++warnTicks >= 60) {
            warnTicks = 0;
            if (d.battery() < 0.15f || d.signal() < 0.25f)
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DRONE_WARNING.get(), 1.0f, 0.5f));
        }
    }

    private static void onTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SPOTTED.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = SPOTTED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            Entity target = mc.level.getEntity(e.getKey());
            boolean expired = now >= e.getValue() || target == null || !target.isAlive();
            if (target != null) glow(target, !expired);
            if (expired) it.remove();
        }
        DroneSounds.tick(mc);
    }

    /** Outline {@code e} for this client only: the server's own flag updates are re-covered next tick. */
    private static void glow(Entity e, boolean on) {
        var data = e.getEntityData();
        byte flags = data.get(SHARED_FLAGS);
        byte want = (byte) (on ? flags | GLOW_BIT : flags & ~GLOW_BIT);
        if (want != flags) data.set(SHARED_FLAGS, want);
    }

    // ---- input / view hooks -------------------------------------------------------------------------

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = false;
        in.shiftKeyDown = savedCrouch; // the body keeps the stance it had
    }

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active() && !KEEP_LAYERS.contains(event.getName())) event.setCanceled(true);
    }

    /** A key pushes its stick out over STICK_OUT ticks and lets it spring back to the centre over STICK_BACK. */
    private static final float STICK_OUT = 5, STICK_BACK = 3;

    private static float towards(float stick, float key) {
        if (key == 0) return Math.abs(stick) <= 1 / STICK_BACK ? 0 : stick - Math.signum(stick) / STICK_BACK;
        float next = stick + Math.signum(key - stick) / STICK_OUT;
        return key > stick ? Math.min(next, key) : Math.max(next, key);
    }

    /** How fast W/S tilt a gimbal (deg per tick). */
    private static final float GIMBAL_PER_TICK = 3;

    /** How fast jump / sneak move the throttle stick (full travel in 0.8 s). */
    private static final float THROTTLE_PER_TICK = 1 / 16f;
    /** Autotest: stands in for the pilot's hands in acro mode (null in play). */
    static java.util.function.Function<DroneEntity, AcroSticks> testPilot;
    /** Autotest: stands in for the pilot's hands in stabilised mode (null in play). */
    static java.util.function.Function<DroneEntity, DroneInput> testInput;

    /** The perspective the body comes back in (F5 presses while flying cycle it). */
    static CameraType bodyCamera() {
        return bodyCamera;
    }

    /** Acro throttle stick position 0..1 (HUD). */
    static float throttleStick() {
        return throttleStick;
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (strikeTicks > 0) return; // the strike camera: a plain, fixed view of the blast
        DroneEntity d = drone();
        LocalPlayer p = Minecraft.getInstance().player;
        if (d == null || p == null) return;
        float pt = (float) event.getPartialTick();
        DroneType t = d.type();
        if (t.flightMode() != com.modernarmory.drone.FlightMode.ACRO) { // stabilised: the mouse is the look
            event.setYaw(p.getViewYRot(pt));
            event.setPitch(p.getViewXRot(pt));
            event.setRoll(0);
            return;
        }
        // acro: the camera is on the airframe (the mouse turns the airframe, through its inertia)
        if (t.gimbal()) { // a gimbal keeps the picture level and holds its own tilt
            event.setYaw(Mth.rotLerp(pt, d.yRotO, d.getYRot()));
            event.setPitch(Mth.clamp(gimbalPitch, -30, 90));
            event.setRoll(0);
            return;
        }
        org.joml.Quaternionf q = new org.joml.Quaternionf(d.prevAttitude).slerp(d.attitude(), pt);
        org.joml.Vector3f e = com.modernarmory.drone.DroneFlight.camera(q, t).getEulerAnglesYXZ(new org.joml.Vector3f());
        // the camera is built as rotationYXZ(180 - yaw, -pitch, -roll): read those back off the camera's attitude
        event.setYaw(180 - (float) Math.toDegrees(e.y));
        event.setPitch(-(float) Math.toDegrees(e.x));
        event.setRoll(-(float) Math.toDegrees(e.z));
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        DroneEntity d = drone();
        if (d != null) event.setFOV(d.type().fov() / zoom);
    }

    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        DroneEntity d = drone();
        if (d == null) return;
        event.setCanceled(true); // no hotbar scrolling for the body
        float max = d.type().maxZoom();
        if (max > 1) zoom = Mth.clamp(zoom * (event.getScrollDeltaY() > 0 ? 1.25f : 0.8f), 1, max);
    }

    private static void onTurn(CalculatePlayerTurnEvent event) {
        if (!active() || zoom <= 1.01f) return;
        double base = event.getMouseSensitivity() * 0.6 + 0.2; // vanilla: turn speed ~ base^3
        event.setMouseSensitivity(Math.max(0, (base * Math.cbrt(1 / zoom) - 0.2) / 0.6));
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        droneId = -1;
        pendingId = -1;
        bodyCamera = null;
        strikeTicks = 0;
        strikeCam = null;
        SPOTTED.clear();
        DroneSounds.clear();
    }

    private DronePilot() {}
}
