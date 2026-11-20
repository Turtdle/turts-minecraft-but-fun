package com.modernarmory.client;

import java.util.ArrayList;
import java.util.List;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.laser.LaserTurretItem;
import com.modernarmory.laser.TurretLayout;
import com.modernarmory.laser.TurretSeat;
import com.modernarmory.network.LaserCommandPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

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
    private static final java.util.Set<ResourceLocation> KEEP_LAYERS = java.util.Set.of(VanillaGuiLayers.CHAT,
            VanillaGuiLayers.DEBUG_OVERLAY, VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.TITLE, VanillaGuiLayers.TAB_LIST,
            VanillaGuiLayers.SAVING_INDICATOR, VanillaGuiLayers.OVERLAY_MESSAGE, ModernArmory.id("flashbang"));
    private static final float MAX_ZOOM = 8;

    private static Marker camera;
    private static float zoom = 1, lastFov = 70;
    /** The locked drone a cease was last sent for (it left the screen), so it is sent once. */
    private static int ceaseSentFor = -1;
    /** Autotest: an outside camera to film the beam from while still at the controls (null in play). */
    static net.minecraft.world.entity.Entity testView;
    /** Holding the item: this tick's placement check (the hologram and the hint), or null. */
    static TurretLayout.Placement preview;

    static void register() {
        NeoForge.EVENT_BUS.addListener(LaserOperator::onTick);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onInteraction);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onScroll);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onFov);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onTurn);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onRenderHand);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onGui);
        NeoForge.EVENT_BUS.addListener(LaserOperator::onRenderLevel);
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
                camera = new Marker(EntityType.MARKER, mc.level);
                Vec3 at = cameraPos(s, 1);
                camera.moveTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
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
                PacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.CEASE, -1));
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

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
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

    private static void onGui(RenderGuiEvent.Post event) {
        LaserHud.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
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
            if (d != null) PacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.ENGAGE, d.getId()));
            else Minecraft.getInstance().player.playSound(com.modernarmory.registry.ModSounds.DRONE_BEEP.get(), 0.5f, 0.6f);
        } else if (event.isUseItem()) {
            PacketDistributor.sendToServer(new LaserCommandPayload(TurretSeat.CEASE, -1));
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
        int[] sp = LaserHud.project(mc.gameRenderer.getMainCamera(), d.getBoundingBox().getCenter(), w, h);
        return sp != null && sp[0] >= 0 && sp[0] <= w && sp[1] >= 0 && sp[1] <= h;
    }

    // ---- world drawing: beams and the hologram ------------------------------------------------------

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera cam = event.getCamera();
        Vec3 cp = cam.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = new PoseStack();
        // positions are camera-relative and the view rotation is already on RenderSystem's model-view stack here

        List<TurretSeat> firing = mc.level.getEntitiesOfClass(TurretSeat.class, new AABB(cp, cp).inflate(256), TurretSeat::firing);
        if (!firing.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            for (TurretSeat s : firing) {
                float yaw = s.yaw(pt), el = s.elevation(pt);
                Vec3 o = TurretLayout.beamOrigin(s.core(), s.facing(), yaw, el);
                Vec3 end = s.beamEnd(o, TurretLayout.beamDirection(yaw, el));
                float flicker = 0.85f + 0.15f * Mth.sin((mc.level.getGameTime() + pt) * 2.7f);
                ribbon(vc, ps, o.subtract(cp), end.subtract(cp), cp, 0.025f, 255, 250, 235, Math.round(230 * flicker));
                ribbon(vc, ps, o.subtract(cp), end.subtract(cp), cp, 0.09f, 255, 140, 60, Math.round(60 * flicker));
                spot(vc, ps, end.subtract(cp), cam, 0.18f, 255, 230, 200, 220);
                spot(vc, ps, end.subtract(cp), cam, 0.45f, 255, 120, 40, 70);
            }
            buffers.endBatch(RenderType.lightning());
        }

        TurretLayout.Placement p = preview;
        if (p != null) hologram(buffers, ps, cp, p, mc.level.getGameTime() + pt);
    }

    /** A flat strip from a to b, turned to face the camera. */
    private static void ribbon(VertexConsumer vc, PoseStack ps, Vec3 a, Vec3 b, Vec3 camPos, float w, int r, int g, int bl, int al) {
        Vec3 axis = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = axis.cross(mid).normalize().scale(w); // camera at the origin (positions are camera-relative)
        if (!Double.isFinite(side.x)) return;
        var m = ps.last().pose();
        vc.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, al);
        vc.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, al);
    }

    /** A glow facing the camera. */
    private static void spot(VertexConsumer vc, PoseStack ps, Vec3 at, Camera cam, float s, int r, int g, int b, int a) {
        org.joml.Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        float lx = left.x() * s, ly = left.y() * s, lz = left.z() * s, ux = up.x() * s, uy = up.y() * s, uz = up.z() * s;
        var m = ps.last().pose();
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
    }

    /**
     * The placement hologram: the turret's volume and its base, glowing green (fits) or red (doesn't), the cells in the
     * way filled red, amber under cells that would hang over a drop, an arrow on the ground the way the turret faces.
     * Everything is drawn as additive quads (like the beam), which every renderer and shader pack shows in colour.
     */
    private static void hologram(MultiBufferSource.BufferSource buffers, PoseStack ps, Vec3 cp, TurretLayout.Placement p, float time) {
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
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        AABB[] parts = {base, upper};
        for (AABB part : parts) {
            AABB a = part.move(-cp.x, -cp.y, -cp.z);
            box(vc, ps, a, r, g, b, Math.round(38 * pulse));
            edges(vc, ps, a, 0.025f, r, g, b, Math.round(200 * pulse));
        }
        for (BlockPos bp : p.blocked()) {
            AABB a = new AABB(bp).inflate(0.01).move(-cp.x, -cp.y, -cp.z);
            box(vc, ps, a, 255, 30, 30, 110);
            edges(vc, ps, a, 0.03f, 255, 60, 60, 255);
        }
        for (BlockPos bp : p.unsupported()) {
            AABB a = new AABB(bp.below()).setMinY(bp.getY() - 0.06).move(-cp.x, -cp.y, -cp.z);
            box(vc, ps, a, 255, 170, 30, 120);
        }
        // an arrow on the ground ahead of the turret, the way it faces
        Vec3 tail = TurretLayout.toWorld(core, f, new Vec3(0, 0.6, -26)).subtract(cp);
        Vec3 tip = TurretLayout.toWorld(core, f, new Vec3(0, 0.6, -46)).subtract(cp);
        Vec3 wl = TurretLayout.toWorld(core, f, new Vec3(-8, 0.6, -38)).subtract(cp);
        Vec3 wr = TurretLayout.toWorld(core, f, new Vec3(8, 0.6, -38)).subtract(cp);
        int al = Math.round(230 * pulse);
        ribbon(vc, ps, tail, tip, cp, 0.05f, r, g, b, al);
        ribbon(vc, ps, wl, tip, cp, 0.05f, r, g, b, al);
        ribbon(vc, ps, wr, tip, cp, 0.05f, r, g, b, al);
        buffers.endBatch(RenderType.lightning());
    }

    /** The twelve edges of a box as thin camera-facing strips. */
    private static void edges(VertexConsumer vc, PoseStack ps, AABB a, float w, int r, int g, int b, int al) {
        double[] xs = {a.minX, a.maxX}, ys = {a.minY, a.maxY}, zs = {a.minZ, a.maxZ};
        for (double y : ys) for (double z : zs) ribbon(vc, ps, new Vec3(a.minX, y, z), new Vec3(a.maxX, y, z), Vec3.ZERO, w, r, g, b, al);
        for (double x : xs) for (double z : zs) ribbon(vc, ps, new Vec3(x, a.minY, z), new Vec3(x, a.maxY, z), Vec3.ZERO, w, r, g, b, al);
        for (double x : xs) for (double y : ys) ribbon(vc, ps, new Vec3(x, y, a.minZ), new Vec3(x, y, a.maxZ), Vec3.ZERO, w, r, g, b, al);
    }

    /** A filled box (all six faces, both sides). */
    private static void box(VertexConsumer vc, PoseStack ps, AABB a, int r, int g, int b, int al) {
        var m = ps.last().pose();
        float x0 = (float) a.minX, y0 = (float) a.minY, z0 = (float) a.minZ, x1 = (float) a.maxX, y1 = (float) a.maxY, z1 = (float) a.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] q : faces) {
            for (int i = 0; i < 12; i += 3) vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, al);
            for (int i = 9; i >= 0; i -= 3) vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, al);
        }
    }

    private LaserOperator() {}
}
