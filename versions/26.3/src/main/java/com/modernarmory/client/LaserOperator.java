package com.modernarmory.client;

import java.util.ArrayList;
import java.util.List;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.laser.LaserTurretItem;
import com.modernarmory.laser.TurretLayout;
import com.modernarmory.laser.TurretSeat;
import com.modernarmory.network.LaserCommandPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The laser turret on the client.
 * <ul>
 *   <li>At the controls (riding a TurretSeat): the view is the sensor camera on its mast above the head; the
 *       mouse aims the view (the head follows it until a drone is locked), the scroll wheel zooms; every drone in
 *       sensor range is boxed (LaserHud); a left click locks the drone nearest the crosshair and fires (the head
 *       then tracks it by itself, the view stays free), a right click ceases fire, and the lock is dropped when the
 *       drone leaves the screen; the hand and held item are hidden; the operator's body shows on the seat.</li>
 *   <li>Everyone: the beam of every laser that is firing (a thin white-hot line, glowing where it lands).</li>
 *   <li>Holding the item: a hologram of where the turret would go, green if it fits, red with the blocks in
 *       the way marked (and amber under cells that would hang over a drop).</li>
 * </ul>
 */
public final class LaserOperator {
    private static final java.util.Set<Identifier> KEEP_LAYERS = java.util.Set.of(VanillaGuiLayers.CHAT,
            VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.TITLE, VanillaGuiLayers.TAB_LIST, VanillaGuiLayers.OVERLAY_MESSAGE,
            ModernArmory.id("flashbang"), ModernArmory.id("laser_hud"));
    private static final float MAX_ZOOM = 8;

    private static Marker camera;
    private static float zoom = 1, lastFov = 70;
    /** The locked drone a cease was last sent for (it left the screen), so it is sent once. */
    private static int ceaseSentFor = -1;
    /** Autotest: an outside camera to film the beam from while still at the controls (null in play). */
    static net.minecraft.world.entity.Entity testView;
    /** Holding the item: this tick's placement check (the hologram and the hint), or null. */
    static TurretLayout.Placement preview;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        // 26.x: the operator's screen is a GUI layer of its own (1.21.1 drew it in RenderGuiEvent.Post)
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(ModernArmory.id("laser_hud"), LaserHud::render));
        NeoForge.EVENT_BUS.addListener(LaserOperator::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onTick);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onInteraction);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onScroll);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onFov);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onTurn);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onRenderHand);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onSubmitGeometry);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onLogout);
    }

    /** The seat the local player is at, or null. */
    public static TurretSeat seat() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof TurretSeat s ? s : null;
    }

    public static boolean active() {
        return seat() != null;
    }

    static float zoom() { return zoom; }
    static float fov() { return lastFov; }

    // ---- camera -----------------------------------------------------------------------------------

    /** The sensor camera: on its mast above and left of the head; it looks where the operator looks. */
    static Vec3 cameraPos(TurretSeat s, float pt) {
        return TurretLayout.sensor(s.core(), s.facing());
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        TurretSeat s = seat();
        if (s != null) {
            if (camera == null || camera.level() != mc.level) {
                camera = new Marker(EntityTypes.MARKER, mc.level);
                Vec3 at = cameraPos(s, 1);
                camera.snapTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
            }
            Vec3 at = cameraPos(s, 1);
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.setPos(at);
            camera.setYRot(player.getYRot());
            camera.setXRot(player.getXRot());
            net.minecraft.world.entity.Entity view = testView != null ? testView : camera;
            if (mc.getCameraEntity() != view) mc.setCameraEntity(view);
            // the lock goes when the locked drone leaves the screen
            if (s.target() != null && s.targetId() != ceaseSentFor && !onScreen(s.target())) {
                ceaseSentFor = s.targetId();
                ClientPacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.CEASE, -1));
            }
        } else if (camera != null) {
            if (mc.getCameraEntity() == camera || mc.getCameraEntity() == testView) mc.setCameraEntity(player);
            camera = null;
            zoom = 1;
            ceaseSentFor = -1;
        }
        preview = s == null ? placementPreview(mc, player) : null;
        // a little smoke where a beam lands on something
        for (TurretSeat seat : mc.level.getEntitiesOfClass(TurretSeat.class, player.getBoundingBox().inflate(160), TurretSeat::firing)) {
            Vec3 o = TurretLayout.beamOrigin(seat.core(), seat.facing(), seat.yaw(1), seat.elevation(1));
            Vec3 end = seat.beamEnd(o, TurretLayout.beamDirection(seat.yaw(1), seat.elevation(1)));
            if (mc.level.getGameTime() % 3 == 0) mc.level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.02, 0);
        }
    }

    private static TurretLayout.Placement placementPreview(Minecraft mc, LocalPlayer player) {
        boolean held = player.getMainHandItem().getItem() instanceof LaserTurretItem || player.getOffhandItem().getItem() instanceof LaserTurretItem;
        if (!held || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos target = LaserTurretItem.target(mc.level, hit.getBlockPos(), hit.getDirection());
        return TurretLayout.check(mc.level, target, player.getDirection(), player);
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        TurretSeat s = seat();
        if (s == null || Minecraft.getInstance().getCameraEntity() != camera) return;
        float pt = (float) event.getPartialTick();
        LocalPlayer p = Minecraft.getInstance().player;
        event.setYaw(p.getViewYRot(pt));
        event.setPitch(p.getViewXRot(pt));
        event.setRoll(0);
    }

    /** 26.x: ComputeFov fires for the world, then for the hand, each frame; only the first is the view's. */
    private static int fovEvents;

    private static void onRenderFrame(RenderFrameEvent.Pre event) {
        fovEvents = 0;
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (fovEvents++ > 0) return;
        if (active()) event.setFOV(event.getFOV() / zoom);
        lastFov = (float) event.getFOV();
    }

    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active()) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom * (event.getScrollDeltaY() > 0 ? 1.25f : 0.8f), 1, MAX_ZOOM);
    }

    private static void onTurn(CalculatePlayerTurnEvent event) {
        if (!active() || zoom <= 1.01f) return;
        double base = event.getMouseSensitivity() * 0.6 + 0.2; // vanilla: turn speed ~ base^3
        event.setMouseSensitivity(Math.max(0, (base * Math.cbrt(1 / zoom) - 0.2) / 0.6));
    }

    private static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (active()) event.setCanceled(true); // the sensor's picture: no hand, no held item
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active() && !KEEP_LAYERS.contains(event.getName())) event.setCanceled(true);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        camera = null;
        zoom = 1;
        ceaseSentFor = -1;
        preview = null;
    }

    // ---- engaging -------------------------------------------------------------------------------

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        TurretSeat s = seat();
        if (s == null) return;
        event.setCanceled(true); // no swinging, mining or using from the seat
        event.setSwingHand(false);
        if (event.isAttack()) {
            DroneEntity d = pick(s);
            if (d != null) ClientPacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.ENGAGE, d.getId()));
            else Minecraft.getInstance().player.playSound(com.modernarmory.registry.ModSounds.DRONE_BEEP.get(), 0.5f, 0.6f);
        } else if (event.isUseItem()) {
            ClientPacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.CEASE, -1));
        }
    }

    /** A drone the sensors have: where it is, whether the laser has a clear line to it, how far from the crosshair. */
    record Track(DroneEntity drone, Vec3 at, double range, boolean clear, double offDeg) {}

    /** Every drone in sensor range, from the operator's camera. */
    static List<Track> tracks(TurretSeat s, float pt) {
        Minecraft mc = Minecraft.getInstance();
        List<Track> out = new ArrayList<>();
        Vec3 cam = cameraPos(s, pt);
        Vec3 look = Vec3.directionFromRotation(mc.player.getViewXRot(pt), mc.player.getViewYRot(pt));
        Vec3 origin = TurretLayout.beamOrigin(s.core(), s.facing(), s.yaw(pt), s.elevation(pt));
        double r = s.spec().sensorRange();
        for (DroneEntity d : mc.level.getEntitiesOfClass(DroneEntity.class, new AABB(cam, cam).inflate(r), DroneEntity::isAlive)) {
            Vec3 at = d.getPosition(pt).add(0, d.getBbHeight() / 2, 0);
            double range = at.distanceTo(origin);
            if (range > r) continue;
            boolean clear = mc.level.clip(new ClipContext(origin, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, s)).getType() == HitResult.Type.MISS;
            double off = Math.toDegrees(Math.acos(Mth.clamp(look.dot(at.subtract(cam).normalize()), -1, 1)));
            out.add(new Track(d, at, range, clear && range <= s.spec().range(), off));
        }
        return out;
    }

    /** What a left click locks: the drone with a clear line nearest the crosshair, within the engage cone. */
    static DroneEntity pick(TurretSeat s) {
        Track best = null;
        for (Track t : tracks(s, 1)) {
            if (!t.clear() || t.offDeg() > s.spec().engageCone()) continue;
            if (best == null || t.offDeg() < best.offDeg()) best = t;
        }
        return best == null ? null : best.drone();
    }

    /** Is the drone on the operator's screen (inside the sensor picture)? */
    static boolean onScreen(DroneEntity d) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int[] sp = LaserHud.project(mc.gameRenderer.mainCamera(), d.getBoundingBox().getCenter(), w, h);
        return sp != null && sp[0] >= 0 && sp[0] <= w && sp[1] >= 0 && sp[1] <= h;
    }

    // ---- world drawing: beams and the hologram ------------------------------------------------------

    /**
     * Quads to submit, camera-relative: x, y, z of four corners, then r, g, b, a. 26.x submits world geometry (drawn
     * later), so the shapes are built first and handed over in one callback (1.21.1 drew them in RenderLevelStageEvent).
     */
    private static final class Quads {
        final List<float[]> list = new ArrayList<>();

        void add(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int al) {
            list.add(new float[] {(float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x, (float) c.y,
                    (float) c.z, (float) d.x, (float) d.y, (float) d.z, r, g, bl, al});
        }

        void emit(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer vc) {
            for (float[] q : list) {
                int r = (int) q[12], g = (int) q[13], b = (int) q[14], a = (int) q[15];
                for (int i = 0; i < 12; i += 3) vc.addVertex(pose, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, a);
                for (int i = 9; i >= 0; i -= 3) vc.addVertex(pose, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, a); // both sides
            }
        }
    }

    private static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        Quads quads = new Quads();
        for (TurretSeat s : mc.level.getEntitiesOfClass(TurretSeat.class, new AABB(cp, cp).inflate(256), TurretSeat::firing)) {
            float yaw = s.yaw(pt), el = s.elevation(pt);
            Vec3 o = TurretLayout.beamOrigin(s.core(), s.facing(), yaw, el);
            Vec3 end = s.beamEnd(o, TurretLayout.beamDirection(yaw, el));
            float flicker = 0.85f + 0.15f * Mth.sin((mc.level.getGameTime() + pt) * 2.7f);
            ribbon(quads, o.subtract(cp), end.subtract(cp), 0.025f, 255, 250, 235, Math.round(230 * flicker));
            ribbon(quads, o.subtract(cp), end.subtract(cp), 0.09f, 255, 140, 60, Math.round(60 * flicker));
            spot(quads, end.subtract(cp), cam, 0.18f, 255, 230, 200, 220);
            spot(quads, end.subtract(cp), cam, 0.45f, 255, 120, 40, 70);
        }
        TurretLayout.Placement p = preview;
        if (p != null) hologram(quads, cp, p, mc.level.getGameTime() + pt);
        if (quads.list.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), quads::emit);
    }

    /** A flat strip from a to b (camera-relative), turned to face the camera. */
    private static void ribbon(Quads q, Vec3 a, Vec3 b, float w, int r, int g, int bl, int al) {
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(a.add(b).scale(0.5)).normalize().scale(w);
        if (!Double.isFinite(side.x)) return;
        q.add(a.subtract(side), a.add(side), b.add(side), b.subtract(side), r, g, bl, al);
    }

    /** A glow facing the camera. */
    private static void spot(Quads q, Vec3 at, Camera cam, float s, int r, int g, int b, int a) {
        Vec3 left = new Vec3(cam.leftVector().x(), cam.leftVector().y(), cam.leftVector().z()).scale(s);
        Vec3 up = new Vec3(cam.upVector().x(), cam.upVector().y(), cam.upVector().z()).scale(s);
        q.add(at.subtract(left).subtract(up), at.subtract(left).add(up), at.add(left).add(up), at.add(left).subtract(up), r, g, b, a);
    }

    /**
     * The placement hologram: the turret's volume and its base, glowing green (fits) or red (doesn't), the cells in the
     * way filled red, amber under cells that would hang over a drop, an arrow on the ground the way the turret faces.
     */
    private static void hologram(Quads q, Vec3 cp, TurretLayout.Placement p, float time) {
        boolean ok = p.ok();
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.25f);
        int r = ok ? 80 : 255, g = ok ? 255 : 60, b = ok ? 120 : 50;
        BlockPos core = p.core();
        Direction f = p.facing();
        AABB base = null, upper = null;
        for (int[] c : TurretLayout.cells()) {
            AABB cell = TurretLayout.cellBox(core, f, c);
            if (c[2] == 0) base = base == null ? cell : base.minmax(cell);
            else upper = upper == null ? cell : upper.minmax(cell);
        }
        for (AABB part : new AABB[] {base, upper}) {
            AABB a = part.move(-cp.x, -cp.y, -cp.z);
            box(q, a, r, g, b, Math.round(38 * pulse));
            edges(q, a, 0.025f, r, g, b, Math.round(200 * pulse));
        }
        for (BlockPos bp : p.blocked()) {
            AABB a = new AABB(bp).inflate(0.01).move(-cp.x, -cp.y, -cp.z);
            box(q, a, 255, 30, 30, 110);
            edges(q, a, 0.03f, 255, 60, 60, 255);
        }
        for (BlockPos bp : p.unsupported())
            box(q, new AABB(bp.below()).setMinY(bp.getY() - 0.06).move(-cp.x, -cp.y, -cp.z), 255, 170, 30, 120);
        Vec3 tail = TurretLayout.toWorld(core, f, new Vec3(0, 0.6, -26)).subtract(cp);
        Vec3 tip = TurretLayout.toWorld(core, f, new Vec3(0, 0.6, -46)).subtract(cp);
        Vec3 wl = TurretLayout.toWorld(core, f, new Vec3(-8, 0.6, -38)).subtract(cp);
        Vec3 wr = TurretLayout.toWorld(core, f, new Vec3(8, 0.6, -38)).subtract(cp);
        int al = Math.round(230 * pulse);
        ribbon(q, tail, tip, 0.05f, r, g, b, al);
        ribbon(q, wl, tip, 0.05f, r, g, b, al);
        ribbon(q, wr, tip, 0.05f, r, g, b, al);
    }

    /** The twelve edges of a box as thin camera-facing strips. */
    private static void edges(Quads q, AABB a, float w, int r, int g, int b, int al) {
        double[] xs = {a.minX, a.maxX}, ys = {a.minY, a.maxY}, zs = {a.minZ, a.maxZ};
        for (double y : ys) for (double z : zs) ribbon(q, new Vec3(a.minX, y, z), new Vec3(a.maxX, y, z), w, r, g, b, al);
        for (double x : xs) for (double z : zs) ribbon(q, new Vec3(x, a.minY, z), new Vec3(x, a.maxY, z), w, r, g, b, al);
        for (double x : xs) for (double y : ys) ribbon(q, new Vec3(x, y, a.minZ), new Vec3(x, y, a.maxZ), w, r, g, b, al);
    }

    /** A filled box (all six faces). */
    private static void box(Quads q, AABB a, int r, int g, int b, int al) {
        double x0 = a.minX, y0 = a.minY, z0 = a.minZ, x1 = a.maxX, y1 = a.maxY, z1 = a.maxZ;
        q.add(new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), r, g, b, al);
        q.add(new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), r, g, b, al);
        q.add(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), r, g, b, al);
        q.add(new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), r, g, b, al);
        q.add(new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), r, g, b, al);
        q.add(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), r, g, b, al);
    }

    private LaserOperator() {}
}
