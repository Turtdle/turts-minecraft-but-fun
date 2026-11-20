package com.modernarmory.client.renderer;

import org.joml.Vector3f;

import com.modernarmory.client.TracerOrigin;
import com.modernarmory.entity.BulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a bullet as a faint, glowing streak trailing behind it (motion blur / tracer look).
 * The streak grows from the muzzle and is capped at a few metres. It starts at the gun's muzzle and
 * eases onto the bullet's true path, which starts at the eye (TracerOrigin). Tracer rounds glow in
 * their colour with a hot core and a camera-facing head until they burn out.
 * <p>
 * 26.x splits entity rendering into extracting a render state (on the entity) and submitting
 * geometry from that state; the geometry itself matches 1.21.1.
 */
public class BulletRenderer extends EntityRenderer<BulletEntity, BulletRenderer.State> {
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

    public static class State extends EntityRenderState {
        Vec3 direction = Vec3.ZERO;
        float trailLength;
        double flown;
        Vec3 offset = Vec3.ZERO;
        int glow = -1;
    }

    public BulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(BulletEntity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks) {
        return true; // the trail extends far outside the tiny bounding box
    }

    @Override
    public void extractRenderState(BulletEntity bullet, State state, float partialTick) {
        super.extractRenderState(bullet, state, partialTick);
        Vec3 vel = bullet.velocity();
        double speed = vel.length();
        if (speed < 1) {
            state.trailLength = 0;
            return;
        }
        state.direction = vel.scale(1.0 / speed);
        // distance flown up to this frame (the entity position is interpolated with partialTick)
        state.flown = bullet.distance() - (1 - partialTick) * speed * 0.05;
        state.glow = bullet.tracerGlow(); // -1: ball round, or the tracer has burnt out
        state.offset = bullet.tracerOffset();
        state.trailLength = (float) Mth.clamp(state.flown - 0.8, 0, state.glow >= 0 ? TRACER_TRAIL : MAX_TRAIL); // hide the first metre
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.trailLength > 0.01f) {
            // the true path starts at the eye; the visible tracer starts at the muzzle and merges into the path
            // (TracerOrigin): each end of the streak is shifted by the muzzle offset left at its distance
            float length = state.trailLength;
            Vec3 head = state.offset.scale(TracerOrigin.fade(state.flown));
            Vec3 tail = state.direction.scale(-length).add(state.offset.scale(TracerOrigin.fade(Math.max(0, state.flown - length))));
            Vec3 toCam = camera.pos.subtract(state.x, state.y, state.z).subtract(head);
            Vec3 side = head.subtract(tail).cross(toCam);
            if (side.lengthSqr() >= 1e-8) {
                Vec3 s = side.normalize();
                int glow = state.glow;
                if (glow >= 0) {
                    int r = (glow >> 16) & 255, g = (glow >> 8) & 255, b = glow & 255;
                    double camDist = toCam.length();
                    Vec3 glowSide = s.scale(Math.max(TRACER_GLOW, camDist * MIN_GLOW_ANGLE));
                    Vec3 coreSide = s.scale(Math.max(TRACER_CORE, camDist * MIN_CORE_ANGLE));
                    // the glowing head, facing the camera
                    Vector3f l = camera.orientation.transform(new Vector3f(-1, 0, 0)), u = camera.orientation.transform(new Vector3f(0, 1, 0));
                    Vec3 left = new Vec3(l), up = new Vec3(u);
                    double hs = Math.max(HEAD_MIN, camDist * HEAD_ANGLE);
                    collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vc) -> {
                        quad(vc, pose, head, tail, glowSide, r, g, b, 120);
                        quad(vc, pose, head, tail, coreSide, 255, 232, 205, 235);
                        billboard(vc, pose, head, left.scale(hs * 2), up.scale(hs * 2), r, g, b, 45); // faint halo
                        billboard(vc, pose, head, left.scale(hs), up.scale(hs), r, g, b, 140);
                        billboard(vc, pose, head, left.scale(hs * 0.4), up.scale(hs * 0.4), 255, 238, 215, 240);
                    });
                } else {
                    Vec3 sideScaled = s.scale(WIDTH);
                    // head: warm white; tail: transparent
                    collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
                            (pose, vc) -> quad(vc, pose, head, tail, sideScaled, 255, 236, 190, 150));
                }
            }
        }
        super.submit(state, poseStack, collector, camera);
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
}
