package com.modernarmory.client;

import java.util.ArrayDeque;
import java.util.Iterator;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.ImpactMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Bullet holes (decals), client side: a small textured square on the face a bullet hit, from the atlas
 * textures/misc/bullet_holes.png (tools/texgen/bullet_hole_textures.py: rows = {@link ImpactMaterial}, columns
 * = styles x variants). Each client keeps its own: at most {@link #MAX} (oldest go first), fading out after
 * {@link #LIFE_TICKS}, and gone as soon as their block changes (broken, opened, replaced).
 */
public final class BulletHoles {
    public static final Identifier ATLAS = ModernArmory.id("textures/misc/bullet_holes.png");
    static final int MAX = 400, LIFE_TICKS = 1200, FADE_TICKS = 200, STYLES = 3, VARIANTS = 4;
    private static final double RENDER_DISTANCE = 64;

    /** The style holes use (0 pixel, 1 crater, 2 detailed: the user's pick, 2026-11-19). */
    public static final int DEFAULT_STYLE = 2;
    /** Which style new holes use (the autotest's comparison wall switches it per row). */
    public static int style = DEFAULT_STYLE;

    record Hole(BlockPos pos, BlockState state, Vec3 at, Direction face, ImpactMaterial material, int style, int variant,
                float size, float angle, long born) {}

    private static final ArrayDeque<Hole> HOLES = new ArrayDeque<>();
    private static Level lastLevel;
    private static int counter;

    /** BulletImpacts listener (registered in ModernArmoryClient). */
    public static void add(Level level, BlockPos pos, BlockState state, Vec3 at, Direction face, double size, boolean scrape) {
        ImpactMaterial m = ImpactMaterial.of(state);
        if (m == null) return;
        if (level != lastLevel) {
            HOLES.clear();
            lastLevel = level;
        }
        if (HOLES.size() >= MAX) HOLES.removeFirst();
        int n = counter++;
        float s = (float) (scrape ? size * 0.7 : size);
        HOLES.addLast(new Hole(pos.immutable(), state, at, face, m, style, Math.floorMod(n * 7 + pos.hashCode(), VARIANTS), s,
                (float) (level.getRandom().nextFloat() * Math.PI * 2), level.getGameTime()));
    }

    public static int count() {
        return HOLES.size();
    }

    /** For the autotest: where every hole is, oldest first. */
    static java.util.List<Vec3> positions() {
        return HOLES.stream().map(Hole::at).toList();
    }

    /** For the autotest: removes every hole. */
    static void clear() {
        HOLES.clear();
    }

    /** Drops holes whose block changed, that are too old, or that belong to another world. */
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != lastLevel) {
            HOLES.clear();
            lastLevel = mc.level;
            return;
        }
        if (mc.level.getGameTime() % 10 != 0) return;
        long now = mc.level.getGameTime();
        for (Iterator<Hole> it = HOLES.iterator(); it.hasNext(); ) {
            Hole h = it.next();
            if (now - h.born() > LIFE_TICKS || mc.level.getBlockState(h.pos()) != h.state()) it.remove();
        }
    }

    /**
     * Submits them as custom world geometry right after the particles (1.21.1: RenderLevelStageEvent
     * AFTER_PARTICLES), with camera-relative coordinates on the event's PoseStack.
     */
    static void submit(SubmitCustomGeometryEvent event) {
        if (HOLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        long now = mc.level.getGameTime();
        java.util.List<Hole> visible = new java.util.ArrayList<>();
        java.util.List<int[]> params = new java.util.ArrayList<>(); // alpha, light, index
        int i = 0;
        for (Hole h : HOLES) {
            i++;
            if (h.at().distanceToSqr(cp) > RENDER_DISTANCE * RENDER_DISTANCE) continue;
            long age = now - h.born();
            int alpha = (int) (255 * Math.min(1.0, (LIFE_TICKS - age) / (double) FADE_TICKS));
            if (alpha <= 0) continue;
            visible.add(h);
            params.add(new int[] {alpha, net.minecraft.util.LightCoordsUtil.getLightCoords(mc.level, h.pos().relative(h.face())), i});
        }
        if (visible.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.entityTranslucent(ATLAS), (pose, vc) -> {
            for (int k = 0; k < visible.size(); k++) {
                int[] p = params.get(k);
                quad(vc, pose, visible.get(k), cp, p[0], p[1], 0.004 + (p[2] % 16) * 0.0004); // tiny steps: overlapping holes don't fight
            }
        });
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Hole h, Vec3 cp, int alpha, int light, double lift) {
        Direction f = h.face();
        float nx = f.getStepX(), ny = f.getStepY(), nz = f.getStepZ();
        // two axes in the face's plane, turned by the hole's random angle
        float ux, uy, uz, vx, vy, vz;
        switch (f.getAxis()) {
            case Y -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
            case Z -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0; }
            default -> { ux = 0; uy = 0; uz = 1; vx = 0; vy = 1; vz = 0; }
        }
        float c = (float) Math.cos(h.angle()), s = (float) Math.sin(h.angle()), half = h.size() / 2;
        float ax = (ux * c + vx * s) * half, ay = (uy * c + vy * s) * half, az = (uz * c + vz * s) * half;
        float bx = (vx * c - ux * s) * half, by = (vy * c - uy * s) * half, bz = (vz * c - uz * s) * half;
        float x = (float) (h.at().x - cp.x + nx * lift), y = (float) (h.at().y - cp.y + ny * lift), z = (float) (h.at().z - cp.z + nz * lift);
        int col = h.style() * VARIANTS + h.variant(), row = h.material().ordinal();
        float u0 = col / (float) (STYLES * VARIANTS), u1 = (col + 1) / (float) (STYLES * VARIANTS);
        float v0 = row / (float) ImpactMaterial.values().length, v1 = (row + 1) / (float) ImpactMaterial.values().length;
        vertex(vc, pose, x - ax - bx, y - ay - by, z - az - bz, u0, v1, alpha, light, nx, ny, nz);
        vertex(vc, pose, x + ax - bx, y + ay - by, z + az - bz, u1, v1, alpha, light, nx, ny, nz);
        vertex(vc, pose, x + ax + bx, y + ay + by, z + az + bz, u1, v0, alpha, light, nx, ny, nz);
        vertex(vc, pose, x - ax + bx, y - ay + by, z - az + bz, u0, v0, alpha, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int alpha, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    private BulletHoles() {}
}
