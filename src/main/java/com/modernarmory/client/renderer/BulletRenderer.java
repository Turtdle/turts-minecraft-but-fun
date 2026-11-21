package com.modernarmory.client.renderer;

import org.joml.Vector3f;

import com.modernarmory.client.TracerOrigin;
import com.modernarmory.entity.BulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a bullet as a faint, glowing streak trailing behind it (motion blur / tracer look).
 * The streak grows from the muzzle and is capped at a few metres. It starts at the gun's muzzle and
 * eases onto the bullet's true path, which starts at the eye (TracerOrigin).
 */
public class BulletRenderer extends EntityRenderer<BulletEntity> {
    private static final float MAX_TRAIL = 7.0f;
    private static final float WIDTH = 0.025f;
    /** Tracer rounds: a longer trail, a wide soft glow in the tracer's colour and a hot core. */
    private static final float TRACER_TRAIL = 16.0f, TRACER_GLOW = 0.11f, TRACER_CORE = 0.035f;
    /**
     * A tracer seen from behind flies straight away from you, so its streak shrinks to a dot: keep it at
     * least this wide on screen (radians of view) and add a camera-facing glowing head, like the bright
     * dot real tracers are.
     */
    private static final double MIN_GLOW_ANGLE = 0.0035, MIN_CORE_ANGLE = 0.0013, HEAD_ANGLE = 0.009, HEAD_MIN = 0.15;

    public BulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(BulletEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true; // the trail extends far outside the tiny bounding box
    }

    @Override
    public void render(BulletEntity bullet, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Vec3 vel = bullet.velocity();
        double speed = vel.length();
        if (speed < 1) return;
        Vec3 dir = vel.scale(1.0 / speed);
        // distance flown up to this frame (interpolated like the entity position; a bullet that stopped
        // short of a whole tick's flight reaches its impact, then its streak collapses into it)
        double flown = bullet.distance(partialTick);
        int glow = bullet.tracerGlow(); // -1: ball round, or the tracer has burnt out
        boolean tracer = glow >= 0;
        float length = (float) Mth.clamp(flown - 0.8, 0, tracer ? TRACER_TRAIL : MAX_TRAIL) // hide the first metre
                * bullet.trailShare(partialTick);
        if (length <= 0.01f) return;

        // the true path starts at the eye; the visible tracer starts at the muzzle and merges into the path
        // (TracerOrigin): each end of the streak is shifted by the muzzle offset left at its distance
        Vec3 off = bullet.tracerOffset();
        Vec3 head = off.scale(TracerOrigin.fade(flown));
        Vec3 tail = dir.scale(-length).add(off.scale(TracerOrigin.fade(Math.max(0, flown - length))));
        Vec3 pos = bullet.getPosition(partialTick).add(head);
        Vec3 axis = head.subtract(tail);
        Vec3 toCam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(pos);
        Vec3 side = axis.cross(toCam);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize();

        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        if (tracer) {
            int r = (glow >> 16) & 255, g = (glow >> 8) & 255, b = glow & 255;
            double camDist = toCam.length();
            quad(vc, pose, head, tail, side.scale(Math.max(TRACER_GLOW, camDist * MIN_GLOW_ANGLE)), r, g, b, 120);
            quad(vc, pose, head, tail, side.scale(Math.max(TRACER_CORE, camDist * MIN_CORE_ANGLE)), 255, 232, 205, 235);
            // the glowing head, facing the camera
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 left = new Vec3(cam.getLeftVector()), up = new Vec3(cam.getUpVector());
            double hs = Math.max(HEAD_MIN, camDist * HEAD_ANGLE);
            billboard(vc, pose, head, left.scale(hs * 2), up.scale(hs * 2), r, g, b, 45); // faint halo
            billboard(vc, pose, head, left.scale(hs), up.scale(hs), r, g, b, 140);
            billboard(vc, pose, head, left.scale(hs * 0.4), up.scale(hs * 0.4), 255, 238, 215, 240);
        } else {
            // head: warm white; tail: transparent
            quad(vc, pose, head, tail, side.scale(WIDTH), 255, 236, 190, 150);
        }
        super.render(bullet, yaw, partialTick, poseStack, buffers, light);
    }

    /** A camera-facing square centred on c (both windings). */
    private static void billboard(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vec3 left, Vec3 up, int r, int g, int b, int a) {
        Vector3f p1 = c.add(left).add(up).toVector3f(), p2 = c.subtract(left).add(up).toVector3f();
        Vector3f p3 = c.subtract(left).subtract(up).toVector3f(), p4 = c.add(left).subtract(up).toVector3f();
        for (Vector3f v : new Vector3f[] {p1, p2, p3, p4, p4, p3, p2, p1}) vc.addVertex(pose, v.x, v.y, v.z).setColor(r, g, b, a);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 head, Vec3 tail, Vec3 side, int r, int g, int b, int a) {
        Vector3f h1 = head.add(side).toVector3f(), h2 = head.subtract(side).toVector3f();
        Vector3f t1 = tail.add(side.scale(0.4)).toVector3f(), t2 = tail.subtract(side.scale(0.4)).toVector3f();
        // both windings so it shows from either side
        vc.addVertex(pose, h1.x, h1.y, h1.z).setColor(r, g, b, a);
        vc.addVertex(pose, h2.x, h2.y, h2.z).setColor(r, g, b, a);
        vc.addVertex(pose, t2.x, t2.y, t2.z).setColor(r, g, b, 0);
        vc.addVertex(pose, t1.x, t1.y, t1.z).setColor(r, g, b, 0);
        vc.addVertex(pose, t1.x, t1.y, t1.z).setColor(r, g, b, 0);
        vc.addVertex(pose, t2.x, t2.y, t2.z).setColor(r, g, b, 0);
        vc.addVertex(pose, h2.x, h2.y, h2.z).setColor(r, g, b, a);
        vc.addVertex(pose, h1.x, h1.y, h1.z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(BulletEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
