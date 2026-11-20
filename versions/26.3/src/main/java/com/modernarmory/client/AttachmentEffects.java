package com.modernarmory.client;

import org.joml.Vector3fc;

import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.Attachment.Reticle;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Client visuals for attachments: reflex-sight reticles and the laser's dot in the world. */
final class AttachmentEffects {
    private static final double MOA = Math.toRadians(1.0 / 60.0);

    // ---- reflex sights (red dot / holographic) ------------------------------------------
    /**
     * Reflex sights project their reticle at infinity, so it always marks the point of aim
     * (the screen centre). Sized in true angular units: M68 CCO = 2 MOA dot; EOTech EXPS3 =
     * 68 MOA ring + 1 MOA dot.
     */
    static void renderReflex(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || !(p.getMainHandItem().getItem() instanceof GunItem gun)) return;
        float aim = ClientState.aimProgress();
        if (aim < 0.9f) return;
        Attachment optic = GunStats.of(gun, p.getMainHandItem()).optic();
        if (optic == null || optic.optic().magnified()) return;

        Window win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight();
        double fov = mc.options.fov().get() * 0.8; // iron/reflex ADS applies a 0.8 FOV modifier
        double pxPerMoa = (H / 2.0) / Math.tan(Math.toRadians(fov / 2)) * Math.tan(MOA);
        int alpha = (int) (255 * Math.min(1, (aim - 0.9f) / 0.1f));
        int cx = W / 2, cy = H / 2;
        g.pose().pushMatrix();
        g.pose().scale(1f / win.getGuiScale(), 1f / win.getGuiScale());
        if (optic.optic().reticle() == Reticle.RED_DOT) {
            disc(g, cx, cy, Math.max(2.5, 2 * pxPerMoa), (alpha / 3) << 24 | 0xFF2020, 1.8);  // soft bloom
            disc(g, cx, cy, Math.max(2.0, 2 * pxPerMoa), alpha << 24 | 0xFF3030, 1.0);
        } else if (optic.optic().reticle() == Reticle.HOLOGRAPHIC) {
            int col = alpha << 24 | 0xFF3A3A;
            ring(g, cx, cy, 34 * pxPerMoa, Math.max(1.5, 1.2 * pxPerMoa), col);
            disc(g, cx, cy, Math.max(2.0, pxPerMoa), col, 1.0);
            // the EXPS3 has short ticks at 3, 6 and 9 o'clock inside the ring... and a bottom tick
            int r = (int) Math.round(34 * pxPerMoa), t = Math.max(2, (int) Math.round(6 * pxPerMoa)), w = Math.max(1, (int) Math.round(1.2 * pxPerMoa));
            g.fill(cx - r, cy - w / 2, cx - r + t, cy - w / 2 + w, col);
            g.fill(cx + r - t, cy - w / 2, cx + r, cy - w / 2 + w, col);
            g.fill(cx - w / 2, cy + r - t, cx - w / 2 + w, cy + r, col);
        }
        g.pose().popMatrix();
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, double d, int col, double scale) {
        double r = d / 2 * scale;
        for (int y = (int) Math.floor(-r); y <= Math.ceil(r); y++) {
            double q = r * r - (y + 0.5) * (y + 0.5);
            if (q <= 0) continue;
            int hw = (int) Math.round(Math.sqrt(q));
            if (hw > 0) g.fill(cx - hw, cy + y, cx + hw, cy + y + 1, col);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, double radius, double width, int col) {
        double ro = radius + width / 2, ri = radius - width / 2;
        for (int y = (int) Math.floor(-ro); y <= Math.ceil(ro); y++) {
            double yy = y + 0.5;
            double qo = ro * ro - yy * yy;
            if (qo <= 0) continue;
            int xo = (int) Math.round(Math.sqrt(qo));
            double qi = ri * ri - yy * yy;
            int xi = qi > 0 ? (int) Math.round(Math.sqrt(qi)) : 0;
            if (xo > xi) {
                g.fill(cx - xo, cy + y, cx - xi, cy + y + 1, col);
                g.fill(cx + xi, cy + y, cx + xo, cy + y + 1, col);
            }
        }
    }

    // ---- laser dot ---------------------------------------------------------------------
    /**
     * The PEQ-15's visible laser lands where the gun points: a small glowing dot on whatever it hits.
     * 26.x: world geometry is submitted, not drawn; SubmitCustomGeometryEvent fires right after particles are
     * submitted (1.21.1 used RenderLevelStageEvent AFTER_PARTICLES) with a camera-relative PoseStack.
     */
    static void submitLaserDots(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        Vector3fc left = cam.leftVector(), up = cam.upVector();
        java.util.List<float[]> dots = new java.util.ArrayList<>(); // x, y, z (camera-relative), size
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(cp) > 96 * 96 || !(p.getMainHandItem().getItem() instanceof GunItem gun)) continue;
            Attachment side = gun.attachments(p.getMainHandItem()).get(AttachmentType.SIDE);
            if (side == null || !side.id().equals("laser") || !gun.spec().slots().contains(AttachmentType.SIDE)) continue;
            Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(120)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) continue;
            Vec3 at = hit.getLocation().subtract(look.scale(0.03)).subtract(cp);
            float size = (float) Math.max(0.03, 0.006 * Math.sqrt(hit.getLocation().distanceTo(cp)) * 4);
            dots.add(new float[] {(float) at.x, (float) at.y, (float) at.z, size});
        }
        if (dots.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, vc) -> {
            for (float[] d : dots) {
                quad(vc, pose, d, left, up, d[3], 255, 40, 40, 220);
                quad(vc, pose, d, left, up, d[3] * 2.2f, 255, 20, 20, 60); // glow
            }
        });
    }

    private static void quad(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose m, float[] at, Vector3fc left, Vector3fc up,
                             float s, int r, int g, int b, int a) {
        float x = at[0], y = at[1], z = at[2];
        float lx = left.x() * s, ly = left.y() * s, lz = left.z() * s, ux = up.x() * s, uy = up.y() * s, uz = up.z() * s;
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
    }

    private AttachmentEffects() {}
}
