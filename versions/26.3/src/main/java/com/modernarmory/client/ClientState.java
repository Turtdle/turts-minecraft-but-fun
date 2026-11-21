package com.modernarmory.client;

import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.modernarmory.gun.GunSpec;
import com.modernarmory.gun.GunView;
import com.modernarmory.gun.ViewClass;
import com.modernarmory.network.GunActionPayload;
import com.modernarmory.network.GunInputPayload;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.server.GunHandler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-side gun input, recoil and aim state. */
public final class ClientState {
    private static boolean sentTrigger, sentAim;
    private static boolean aiming;

    // recoil
    private static float pendingPitch, pendingYaw, recoverPool;
    /** Sideways recoil drift still to undo, degrees of yaw (+ = it pushed the view right); signed, unlike the climb. */
    private static float recoverYawPool;
    /** Pitch at the end of the last frame: what changed since then was the player's own mouse. */
    private static float recoilLastPitch = Float.NaN, recoilLastYaw = Float.NaN;
    private static long lastShotNanos, lastFrameNanos;

    public static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    public static boolean isAiming() {
        return aiming;
    }

    static boolean holdingGun(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof GunItem;
    }

    /** Stop vanilla attack/use (block breaking, swinging) while a gun is in hand. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if ((event.isAttack() || event.isUseItem()) && holdingGun(Minecraft.getInstance().player)) {
            if (event.isAttack()) ShotPredictor.onAttackClick();
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        // 26.x: the open screen moved from Minecraft#screen to Gui#screen()
        // flying a drone: the body's hands are on the controller, not the gun
        boolean gun = holdingGun(p) && mc.gui.screen() == null && !p.isSpectator() && !DronePilot.active();
        boolean trigger = gun && mc.options.keyAttack.isDown();
        aiming = gun && mc.options.keyUse.isDown();
        if (trigger != sentTrigger || aiming != sentAim) {
            ClientPacketDistributor.sendToServer(new GunInputPayload(trigger, aiming));
            sentTrigger = trigger;
            sentAim = aiming;
        }
        ShotPredictor.tick(p, gun, trigger, aiming); // fires rounds on this side at once (server checks each)
        while (KeyBindings.RELOAD.consumeClick()) {
            if (gun) {
                ShotPredictor.onReloadRequested();
                ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_RELOAD));
            }
        }
        while (KeyBindings.FIRE_MODE.consumeClick()) {
            if (gun) ClientPacketDistributor.sendToServer(new GunActionPayload(GunHandler.ACTION_FIRE_MODE));
        }
    }

    static void onFov(ComputeFovModifierEvent event) {
        GunView v = heldView();
        if (aiming && v != null && !v.scoped() && firstPerson()) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.8f);
        }
    }

    /** Our own shot (ShotPredictor): camera recoil (degrees) and muzzle flash, {@code delay} s into this tick. */
    static void onOwnShot(float pitchKick, float yawKick, float delay) {
        schedule(delay, () -> {
            pendingPitch += pitchKick;
            pendingYaw += yawKick;
            lastShotNanos = System.nanoTime();
            shotSeed = (float) Math.random();
        });
    }

    // The server fires on 50 ms ticks, but 800 rpm is one round every 75 ms. Each shot carries
    // its exact time within the tick, and we play it then, so the cadence is even instead of
    // bunching into "ba-bang ... ba-bang".
    private record Pending(long due, Runnable action) {}
    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    private static void schedule(float delaySeconds, Runnable action) {
        if (delaySeconds <= 0.002f) action.run();
        else PENDING.add(new Pending(System.nanoTime() + (long) (delaySeconds * 1e9), action));
    }

    private static void runDue() {
        if (PENDING.isEmpty()) return;
        long now = System.nanoTime();
        PENDING.removeIf(pnd -> {
            if (pnd.due() > now) return false;
            pnd.action().run();
            return true;
        });
    }

    private static float shotSeed;

    /** Own gun sounds: non-positional (relative to the listener) so they follow you. */
    public static void onLocalSound(LocalSoundPayload p) {
        playOwnSound(p.sound(), p.volume(), p.pitch(), p.delay());
    }

    /** One of our own gun sounds (shots predicted here, ShotPredictor), {@code delay} seconds from now. */
    static void playOwnSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch, float delay) {
        playOwnSound(sound.location(), volume, pitch, delay);
    }

    private static void playOwnSound(net.minecraft.resources.Identifier sound, float volume, float pitch, float delay) {
        schedule(delay, () -> Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS,
                Math.min(1f, volume), pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true)));
    }

    /** Seconds since the local player's last shot (drives the first-person muzzle flash). */
    public static float secondsSinceShot() {
        return (System.nanoTime() - lastShotNanos) / 1e9f;
    }

    /** Random value per shot, so each muzzle flash looks a bit different. */
    public static float shotSeed() {
        return shotSeed;
    }

    // ---- aim down sights -------------------------------------------------
    /**
     * When aiming, the gun moves (view space, blocks) so the eye sits on the gun's sight line
     * (GunView.sightY at GunView.eyeZ). The point of aim is then the screen centre, which is where
     * bullets go (they fly from the eye along the view direction). The first-person scale and hip
     * origin come from the gun's {@link ViewClass} framing, the same numbers that
     * tools/modelgen/display.py writes into models/item/&lt;gun&gt;.json (for the RIFLE class:
     * scale 0.78, hip origin at view (0.28, -0.52, -0.42)).
     */
    static float adsX(ViewClass.Framing f, GunView v) { return -f.originX(); }
    static float adsY(ViewClass.Framing f, GunView v) { return -(v.sightY() * f.fpScale() / 16f + 0.01f) - f.originY(); }
    static float adsZ(ViewClass.Framing f, GunView v) { return -(v.eyeZ() * f.fpScale() / 16f) - f.originZ(); }

    private static float aimProgress, prevAimProgress;

    public static float aimProgress() {
        return aimProgress;
    }

    /** The held gun's current sight picture (its fitted optic), or null when not holding a gun. */
    public static GunView heldView() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainHandItem().getItem() instanceof GunItem g ? GunViews.resolve(g, p.getMainHandItem()) : null;
    }

    static GunSpec heldSpec() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainHandItem().getItem() instanceof GunItem g ? g.spec() : null;
    }

    // ---- scopes ------------------------------------------------------------
    private static final float SCOPE_IN = 0.85f; // aim progress at which you're "behind the glass"
    private static final float SCOPE_IN_SECONDS = 0.13f; // black closes in, view zooms to power
    private static float scopeZoom = 1f;  // power ring setting (scroll wheel)
    private static float viewZoom = 1f;   // magnification shown, eased toward scopeZoom
    private static GunView zoomFor;
    private static float swayX, swayY;
    private static boolean wasScoped;
    private static long scopeInNanos;
    private static double lastScopedFov = 70;

    /**
     * True while looking through a magnified scope (overlay drawn, gun model hidden). First person only:
     * in third person (F5) aiming still raises the gun, but there is no scope picture, zoom or sway.
     */
    public static boolean scopedIn() {
        GunView v = heldView();
        return v != null && v.scoped() && aimProgress >= SCOPE_IN && firstPerson();
    }

    /** Scope / aim view effects (zoom, overlay, slower mouse) only apply when looking through the gun. */
    static boolean firstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** Current magnification: follows the power ring smoothly instead of jumping between steps. */
    public static float scopeZoom() {
        return viewZoom;
    }

    /** Test hook (AutoTest): turn the power ring to this magnification. */
    static void setScopeZoom(float zoom) {
        GunView v = heldView();
        scopeZoom = v == null ? zoom : Mth.clamp(zoom, v.scopeMin(), v.scopeMax());
    }

    /** 0 -> 1 over the first ~0.13 s behind the scope, eased. */
    static float scopeBlend() {
        if (!scopedIn()) return 0;
        float t = Mth.clamp((System.nanoTime() - scopeInNanos) / 1e9f / SCOPE_IN_SECONDS, 0, 1);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Vertical field of view (degrees) the world was last rendered with while scoped. */
    static double scopedFov() {
        return lastScopedFov;
    }

    /**
     * 26.x: ComputeFov no longer says whether it is the world FOV or the hand (HUD) FOV. Camera#update computes
     * both, always in that order (calculateFov, then calculateHudFov), once per frame on the only Camera, so the
     * second event of a frame is the hand's (the flag is reset in onRenderFrame). 1.21.1 skipped the hand FOV via
     * usedConfiguredFov(); this skips the same one.
     */
    private static boolean nextFovIsHud;

    static void onComputeFov(ViewportEvent.ComputeFov event) {
        boolean hud = nextFovIsHud;
        nextFovIsHud = !nextFovIsHud;
        GunView v = heldView();
        if (v == null || !v.scoped() || hud || !firstPerson()) return;
        if (!v.equals(zoomFor)) { zoomFor = v; scopeZoom = viewZoom = v.scopeMin(); } // new optic: start at its lowest power
        float zoom;
        if (scopedIn()) {
            // coming up to the eyepiece: the view swoops from the 1.25x of aiming to the power ring
            float b = scopeBlend();
            zoom = (float) Math.exp(Mth.lerp(b, (float) Math.log(1.25f), (float) Math.log(viewZoom)));
        } else {
            zoom = 1f + 0.25f * aimProgress;
        }
        event.setFOV(event.getFOV() / zoom);
        if (scopedIn()) lastScopedFov = event.getFOV();
    }

    /** Scroll wheel turns the power ring (6.5-20x on the M2010) instead of the hotbar. */
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!scopedIn()) return;
        GunView v = heldView();
        scopeZoom = Mth.clamp(scopeZoom * (event.getScrollDeltaY() > 0 ? 1.2f : 1 / 1.2f), v.scopeMin(), v.scopeMax());
        event.setCanceled(true);
    }

    // ---- eye box -------------------------------------------------------------
    // The scope only shows a full picture while the eye sits inside its exit pupil (objective /
    // power: 50 mm / 6.5x = 7.7 mm, 50 / 20x = 2.5 mm). Turning fast, the kick of a shot, sway
    // and walking move the head relative to the eyepiece; the eye is modelled as a damped spring
    // (mm, screen axes) pulled by those, and once it leaves the exit pupil a soft shadow creeps
    // in from one side. A smaller exit pupil at high power makes it much less forgiving.
    private static final float OBJECTIVE_MM = 50f, EYE_PUPIL_MM = 3f;
    private static float eyeX, eyeY, eyeVX, eyeVY;   // eye offset from the optical axis, mm and mm/s
    private static float turnX, turnY;              // smoothed view turn rate, deg/s
    private static float lastYaw, lastPitch;
    private static long seenShotNanos;
    private static float shadowX, shadowY;          // shadow offset, in field radii

    /** Offset of the exit-pupil shadow (field radii, screen axes; 0 = eye on axis). */
    static float eyeShadowX() { return shadowX; }
    static float eyeShadowY() { return shadowY; }

    private static void updateEyeBox(LocalPlayer player, float dt, float partial) {
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (!scopedIn()) {
            eyeX = eyeY = eyeVX = eyeVY = turnX = turnY = shadowX = shadowY = 0;
            lastYaw = yaw;
            lastPitch = pitch;
            seenShotNanos = lastShotNanos;
            return;
        }
        float rx = Mth.wrapDegrees(yaw - lastYaw) / dt, ry = (pitch - lastPitch) / dt;
        lastYaw = yaw;
        lastPitch = pitch;
        float k = 1 - (float) Math.exp(-dt * 25);
        turnX += (Mth.clamp(rx, -720, 720) - turnX) * k;
        turnY += (Mth.clamp(ry, -720, 720) - turnY) * k;
        // the head lags the rifle while it swings: ~0.05 mm per deg/s
        float tx = -turnX * 0.05f, ty = -turnY * 0.05f;
        // walking: the cheek weld bobs with each step
        var mv = player.getDeltaMovement();
        float speed = (float) Math.min(1.5, Math.sqrt(mv.x * mv.x + mv.z * mv.z) / 0.1);
        if (player.onGround() && speed > 0.05f) {
            // 26.x: the walk distance moved from Entity#walkDist to the client avatar state
            float walk = player.avatarState().getInterpolatedWalkDistance(partial) * (float) Math.PI;
            float bob = AimBob.masteryFactor(player); // mastery steadies the cheek weld too
            tx += Mth.sin(walk) * 1.1f * speed * bob;
            ty += (Math.abs(Mth.cos(walk)) * 1.4f * speed - 0.7f * speed) * bob;
        }
        float len = (float) Math.hypot(tx, ty);
        if (len > 6) { tx *= 6 / len; ty *= 6 / len; }
        // the kick of a shot shoves the scope back and up into the face
        if (lastShotNanos != seenShotNanos) {
            seenShotNanos = lastShotNanos;
            eyeVY += 55f + 15f * shotSeed;
            eyeVX += (shotSeed - 0.5f) * 40f;
        }
        // damped spring, ~1.3 Hz, settles in ~0.4 s
        float w = 8.5f, zeta = 0.75f;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            eyeVX += (-w * w * (eyeX - tx) - 2 * zeta * w * eyeVX) * h;
            eyeVY += (-w * w * (eyeY - ty) - 2 * zeta * w * eyeVY) * h;
            eyeX += eyeVX * h;
            eyeY += eyeVY * h;
        }
        // tolerance: how far the eye can drift while its pupil stays inside the exit pupil
        float exitR = OBJECTIVE_MM / viewZoom / 2, tol = Math.max(0.15f, exitR - EYE_PUPIL_MM / 2);
        float d = (float) Math.hypot(eyeX, eyeY);
        float amount = Mth.clamp((d - tol) / 2.2f, 0, 1.8f);
        float sx = d > 1e-4f ? -eyeX / d * amount : 0, sy = d > 1e-4f ? -eyeY / d * amount : 0;
        float f = 1 - (float) Math.exp(-dt * 40); // light smoothing, no pixel jitter
        shadowX += (sx - shadowX) * f;
        shadowY += (sy - shadowY) * f;
    }

    /** Mouse sensitivity scales with magnification, like the spyglass, so aiming stays controllable. */
    static void onTurn(CalculatePlayerTurnEvent event) {
        if (!firstPerson()) return;
        float factor = scopedIn() ? 1f / viewZoom : aimProgress > 0.5f ? 0.8f : 1f;
        if (factor >= 1f) return;
        double s = event.getMouseSensitivity();
        double base = s * 0.6 + 0.2; // vanilla: turn speed ~ base^3
        event.setMouseSensitivity(Math.max(0, (base * Math.cbrt(factor) - 0.2) / 0.6));
    }

    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getItemStack().getItem() instanceof GunItem gun)) return;
        GunView v = GunViews.resolve(gun, event.getItemStack());
        if (v.scoped() && aimProgress >= SCOPE_IN) {
            event.setCanceled(true); // looking through the scope: the overlay replaces the gun
            return;
        }
        float p = Mth.lerp(event.getPartialTick(), prevAimProgress, aimProgress);
        p = p * p * (3 - 2 * p); // smoothstep
        ViewClass.Framing f = gun.spec().viewClass().framing();
        PoseStack ps = event.getPoseStack();
        if (p > 0) ps.translate(adsX(f, v) * p, adsY(f, v) * p, adsZ(f, v) * p);
        float hip = 1 - p;
        if (hip > 0) {
            // hip-fire cant: yawed so the muzzle points in toward the crosshair and you see the
            // gun's right side, around the gun's origin; blended out while aiming
            // 26.x PoseStack has no mulPose(Quaternionf); rotateAround(q, pivot) = translate, rotate, translate back
            ps.rotateAround(Axis.YP.rotationDegrees(f.hipYawDeg() * hip), f.originX(), f.originY(), f.originZ());
        }
    }

    /**
     * Aim progress per game tick: the held gun's {@code adsSeconds} (pistols quick, machine guns
     * slow) with its attachments' aim-speed multipliers (a big scope is slower to raise).
     */
    private static float aimStep() {
        var player = Minecraft.getInstance().player;
        double secs = player != null && player.getMainHandItem().getItem() instanceof GunItem g
                ? GunStats.of(g, player.getMainHandItem()).adsSeconds() : 0.2;
        return (float) Math.min(1.0, 0.05 / secs);
    }

    static void onClientTickPre(ClientTickEvent.Pre event) {
        prevAimProgress = aimProgress;
        aimProgress = Mth.approach(aimProgress, aiming ? 1f : 0f, aimStep()); // M4: 4 ticks = 0.2 s to raise
        boolean scoped = scopedIn();
        if (scoped && !wasScoped) scopeInNanos = System.nanoTime(); // the scope-in blend starts now
        wasScoped = scoped;
    }

    static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && holdingGun(Minecraft.getInstance().player)) {
            event.setCanceled(true); // guns draw their own crosshair (GunCrosshair), none when aiming
        }
    }


    /**
     * Smoothly applies recoil each frame, then brings the view all the way back to where you were aiming
     * once firing stops. Whatever you pulled down yourself while spraying counts as already recovered, so
     * compensating never drags you below the target.
     */
    static void onRenderFrame(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        nextFovIsHud = false; // the camera computes world then hand FOV after this event
        runDue();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || dt <= 0) return;

        // the player's own pitch input since last frame: pulling down pays back the recoil climb
        if (!Float.isNaN(recoilLastPitch)) {
            float pulledDown = player.getXRot() - recoilLastPitch;
            if (pulledDown > 0 && recoverPool > 0) recoverPool = Math.max(0, recoverPool - pulledDown);
        }
        // the same sideways: moving against the drift pays it back (never past where you were aiming)
        if (!Float.isNaN(recoilLastYaw)) {
            float moved = player.getYRot() - recoilLastYaw;
            if (recoverYawPool > 0 && moved < 0) recoverYawPool = Math.max(0, recoverYawPool + moved);
            else if (recoverYawPool < 0 && moved > 0) recoverYawPool = Math.min(0, recoverYawPool + moved);
        }
        if (pendingPitch != 0 || pendingYaw != 0) {
            float f = 1 - (float) Math.exp(-dt * 30);
            float dp = pendingPitch * f, dy = pendingYaw * f;
            pendingPitch -= dp;
            pendingYaw -= dy;
            if (Math.abs(pendingPitch) < 1e-3) pendingPitch = 0;
            if (Math.abs(pendingYaw) < 1e-3) pendingYaw = 0;
            float before = player.getXRot(), yawBefore = player.getYRot();
            player.turn(dy / 0.15, -dp / 0.15); // Entity.turn scales by 0.15
            recoverPool += Math.max(0, before - player.getXRot()); // the climb that happened (not past straight up)
            recoverYawPool += player.getYRot() - yawBefore;
        }
        // power ring: ease the magnification toward the setting (log space, ~0.15 s)
        if (Math.abs(viewZoom - scopeZoom) > 1e-3f) {
            float lz = (float) Math.log(viewZoom), lt = (float) Math.log(scopeZoom);
            viewZoom = (float) Math.exp(lz + (lt - lz) * (1 - (float) Math.exp(-dt * 16)));
        } else {
            viewZoom = scopeZoom;
        }
        if (scopedIn()) { // breathing sway behind a scope; crouch to steady it
            float amp = (player.isCrouching() ? 0.03f : 0.09f) * masterySway(player), t = now / 1e9f;
            float sx = amp * (float) Math.sin(t * 0.83), sy = amp * 0.7f * (float) Math.sin(t * 1.31 + 1.0);
            player.turn((sx - swayX) / 0.15, (sy - swayY) / 0.15);
            swayX = sx;
            swayY = sy;
        } else {
            swayX = swayY = 0;
        }
        if ((recoverPool > 0.01f || Math.abs(recoverYawPool) > 0.01f) && now - lastShotNanos > 150_000_000L) {
            float k = 1 - (float) Math.exp(-dt * 7);
            float r = recoverPool * k, ry = recoverYawPool * k;
            recoverPool -= r;
            recoverYawPool -= ry;
            player.turn(-ry / 0.15, r / 0.15);
        }
        recoilLastPitch = player.getXRot();
        recoilLastYaw = player.getYRot();
        updateEyeBox(player, dt, event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /** Mastery reduces breathing / scope sway with this gun type. */
    private static float masterySway(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof GunItem g
                ? com.modernarmory.mastery.MasteryHandler.effects(player, g).sway() : 1f;
    }

    private ClientState() {}
}
