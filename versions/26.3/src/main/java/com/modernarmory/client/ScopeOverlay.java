package com.modernarmory.client;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.gun.GunView;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The view through the M2010's Leupold Mark 4 M5A2 (6.5-20x50, front focal plane, mil-dot).
 * Drawn in real framebuffer pixels, back to front:
 * <ol>
 * <li>the reticle, procedurally: in the front focal plane it magnifies with the image, so it is
 *     sized from the rendered field of view each frame and 1 mil always covers 1 mrad of the
 *     world (a 2-block pillar 100 blocks away spans 20 mils at any power);</li>
 * <li>the exit-pupil shadow, a black layer with a soft hole that slides off-centre when the
 *     eye leaves the eye box (see ClientState's eye box);</li>
 * <li>lens and tube: vignette and chromatic edge inside the field stop, the inner tube wall,
 *     black beyond ({@code GunView.scopeOverlay()} texture);</li>
 * <li>the magnification readout.</li>
 * </ol>
 */
final class ScopeOverlay {
    private static final Identifier SHADOW = ModernArmory.id("textures/misc/scope_shadow.png");
    /** Half the texture side in field radii (tools/texgen/make_textures.py TUBE_/SHADOW_EXTENT). */
    private static final float TUBE_EXTENT = 1.25f, SHADOW_EXTENT = 2.0f;
    private static final int TUBE_TEX = 1024, SHADOW_TEX = 512;
    /** Field of view circle: diameter as a fraction of the screen height. */
    private static final float FIELD = 0.9f;

    // USMC/Leupold mil-dot, in mils: dots at 1-4, thick posts from 5 out to the field stop
    private static final double POST_START = 5.0, POST_WIDTH = 0.45, LINE_WIDTH = 0.05, DOT = 0.22;
    private static final int INK = 0xFF0B0B0B;

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.scopedIn() || !mc.options.getCameraType().isFirstPerson()) return;
        GunView view = ClientState.heldView();
        Window win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight();
        double gs = win.getGuiScale();
        int cx = W / 2, cy = H / 2; // pixel the optical axis passes through

        // scope-in: the black closes in from the screen edges onto the field stop
        float b = ClientState.scopeBlend();
        float R = FIELD * H / 2 * (1 + 0.9f * (1 - b));
        float sx = ClientState.eyeShadowX(), sy = ClientState.eyeShadowY();
        // the field stop follows the eye a little, the shadow edge follows it fully
        int tubeX = cx + Math.round(sx * R * 0.22f), tubeY = cy + Math.round(sy * R * 0.22f);
        int shX = cx + Math.round(sx * R * 0.95f), shY = cy + Math.round(sy * R * 0.95f);

        // pixels per mil from the vertical field of view the world was just rendered with
        double ppm = (H / 2.0) / Math.tan(Math.toRadians(ClientState.scopedFov() / 2)) / 1000.0;

        g.pose().pushMatrix();
        g.pose().scale((float) (1 / gs), (float) (1 / gs));
        // ACOG: sized to its own 7 deg (122 mil) field of view, as it looks through a real TA31
        // 26.x: GUI draws are always batched (no drawManaged); fills keep submission order
        if (view.reticle() == Attachment.Reticle.CHEVRON) chevron(g, cx, cy, R / 61.0);
        else reticle(g, cx, cy, ppm, R * 1.1f);
        if (sx != 0 || sy != 0) {
            int half = Math.round(R * SHADOW_EXTENT);
            layer(g, SHADOW, shX - half, shY - half, 2 * half, SHADOW_TEX);
            blackAround(g, shX - half, shY - half, shX + half, shY + half, W, H);
        }
        int half = Math.round(R * TUBE_EXTENT);
        layer(g, view.scopeOverlay(), tubeX - half, tubeY - half, 2 * half, TUBE_TEX);
        blackAround(g, tubeX - half, tubeY - half, tubeX + half, tubeY + half, W, H);
        g.pose().popMatrix();

        // magnification, small and grey on the eyepiece housing below-right of the field
        String mag = String.format("%.1fx", ClientState.scopeZoom());
        float a = (float) Math.toRadians(38);
        int tx = (int) ((cx + Math.cos(a) * R * 1.13) / gs), ty = (int) ((cy + Math.sin(a) * R * 1.13) / gs);
        if (b > 0.9f) g.text(mc.font, mag, tx, ty, 0xFF7A7A7A, false);
    }

    /**
     * Fine crosshair with mil dots, thick posts toward the edge. Everything is sized in mils and
     * anti-aliased, so it keeps its true size even where a mil is only a few pixels (6.5x on a
     * small window), with a minimum so it never thins out to nothing.
     */
    /**
     * Trijicon TA31 ACOG (4x): an illuminated red chevron whose tip is the point of aim, a fine
     * post below it, and bullet-drop stadia bars for 400-800 m that get narrower with range
     * (each bar's width = a man's shoulders at that distance). Sized in mils like the mil-dot.
     */
    private static void chevron(GuiGraphicsExtractor g, int px, int py, double ppm) {
        double cx = px + 0.5, cy = py + 0.5;
        int red = 0xFFE62A1E, glow = 0x55FF3020;
        double arm = 4.0 * ppm, drop = 3.3 * ppm, thick = Math.max(2.5, 0.6 * ppm);
        // chevron: tip at the point of aim, arms down-and-out; drawn as many thin rows
        int rows = (int) Math.max(6, Math.round(drop));
        for (int pass = 0; pass < 2; pass++) {
            double t = pass == 0 ? thick * 2.2 : thick;
            int col = pass == 0 ? glow : red;
            for (int i = 0; i < rows; i++) {
                double f = (double) i / rows, y0 = cy + f * drop, y1 = cy + (i + 1.0) / rows * drop, half = f * arm;
                g.fill((int) Math.round(cx - half - t / 2), (int) Math.round(y0), (int) Math.round(cx - half + t / 2),
                        (int) Math.round(Math.max(y1, y0 + 1)), col);
                g.fill((int) Math.round(cx + half - t / 2), (int) Math.round(y0), (int) Math.round(cx + half + t / 2),
                        (int) Math.round(Math.max(y1, y0 + 1)), col);
            }
        }
        // fine post below the chevron with bullet-drop stadia: M855 drops (mils) at 400-800 m, each
        // bar as wide as a man's shoulders (0.5 m) at that range
        double line = Math.max(1.5, 0.08 * ppm);
        rect(g, cx - line / 2, cy + drop + 0.6 * ppm, cx + line / 2, cy + 9.5 * ppm);
        double[][] bars = {{2.6, 0.62}, {3.7, 0.50}, {5.0, 0.42}, {6.5, 0.36}, {8.3, 0.31}}; // {mils down, half-width}
        for (double[] bar : bars) {
            double y = cy + bar[0] * ppm + drop * 0.6;
            rect(g, cx - bar[1] * ppm, y - line / 2, cx + bar[1] * ppm, y + line / 2);
        }
    }

    private static void reticle(GuiGraphicsExtractor g, int px, int py, double ppm, float reach) {
        double cx = px + 0.5, cy = py + 0.5; // centre of the axis pixel: 1-px lines stay crisp
        double line = Math.max(1.0, LINE_WIDTH * ppm);
        double post = Math.max(2.2, POST_WIDTH * ppm);
        double tip = (line + post) / 2;       // the post's first half mil is a little narrower
        double dot = Math.max(2.4, DOT * ppm);
        // post starts snapped to whole pixels, so the line / tip / post joints have no seams
        double ps = Math.round(cx + POST_START * ppm) - cx, tp = Math.round(cx + (POST_START + 0.5) * ppm) - cx;
        double end = reach;
        // fine crosshair between the posts
        rect(g, cx - ps, cy - line / 2, cx + ps, cy + line / 2);
        rect(g, cx - line / 2, cy - ps, cx + line / 2, cy - line / 2);
        rect(g, cx - line / 2, cy + line / 2, cx + line / 2, cy + ps);
        for (int side = -1; side <= 1; side += 2) {
            // horizontal: tip, then post out to the field stop
            rect(g, side > 0 ? cx + ps : cx - tp, cy - tip / 2, side > 0 ? cx + tp : cx - ps, cy + tip / 2);
            rect(g, side > 0 ? cx + tp : cx - end, cy - post / 2, side > 0 ? cx + end : cx - tp, cy + post / 2);
            // vertical
            rect(g, cx - tip / 2, side > 0 ? cy + ps : cy - tp, cx + tip / 2, side > 0 ? cy + tp : cy - ps);
            rect(g, cx - post / 2, side > 0 ? cy + tp : cy - end, cx + post / 2, side > 0 ? cy + end : cy - tp);
        }
        for (int m = 1; m <= 4; m++) {
            double o = m * ppm;
            disc(g, cx + o, cy, dot / 2);
            disc(g, cx - o, cy, dot / 2);
            disc(g, cx, cy + o, dot / 2);
            disc(g, cx, cy - o, dot / 2);
        }
    }

    private static void ink(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double coverage) {
        int a = (int) Math.round(Math.min(1, coverage) * ((INK >>> 24) & 0xFF));
        if (a > 0 && x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, (a << 24) | (INK & 0xFFFFFF));
    }

    /** Rectangle with fractional edges: whole pixels solid, edge pixels by coverage. */
    private static void rect(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1) {
        if (x1 <= x0 || y1 <= y0) return;
        int ix0 = (int) Math.floor(x0), ix1 = (int) Math.ceil(x1), iy0 = (int) Math.floor(y0), iy1 = (int) Math.ceil(y1);
        // rows: [iy0] partial, [iy0+1, iy1-1) full, [iy1-1] partial (one row if it all fits)
        int[][] rows = iy1 - iy0 == 1 ? new int[][] {{iy0, iy1}} : new int[][] {{iy0, iy0 + 1}, {iy0 + 1, iy1 - 1}, {iy1 - 1, iy1}};
        for (int[] r : rows) {
            if (r[1] <= r[0]) continue;
            double cy = Math.min(r[1], y1) - Math.max(r[0], y0);
            if (r[1] - r[0] > 1) cy = 1;
            if (ix1 - ix0 == 1) { ink(g, ix0, r[0], ix1, r[1], cy * (x1 - x0)); continue; }
            ink(g, ix0, r[0], ix0 + 1, r[1], cy * (ix0 + 1 - x0));
            ink(g, ix0 + 1, r[0], ix1 - 1, r[1], cy);
            ink(g, ix1 - 1, r[0], ix1, r[1], cy * (x1 - (ix1 - 1)));
        }
    }

    /** Anti-aliased filled circle (4x4 samples per pixel), interior rows merged into runs. */
    private static void disc(GuiGraphicsExtractor g, double x, double y, double r) {
        for (int py = (int) Math.floor(y - r); py < Math.ceil(y + r); py++) {
            int runStart = -1;
            int x0 = (int) Math.floor(x - r), x1 = (int) Math.ceil(x + r);
            for (int px = x0; px <= x1; px++) {
                double cov = 0;
                if (px < x1) {
                    int n = 0;
                    for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                        double dx = px + (sx + 0.5) / 4 - x, dy = py + (sy + 0.5) / 4 - y;
                        if (dx * dx + dy * dy <= r * r) n++;
                    }
                    cov = n / 16.0;
                }
                if (cov >= 1) { if (runStart < 0) runStart = px; continue; }
                if (runStart >= 0) { ink(g, runStart, py, px, py + 1, 1); runStart = -1; }
                ink(g, px, py, px + 1, py + 1, cov);
            }
        }
    }

    /**
     * A translucent square texture layer. 26.x: the GUI_TEXTURED pipeline carries its own (translucent)
     * blend state, so the 1.21.1 enableBlend/defaultBlendFunc dance is not needed; blit takes the
     * pipeline first and (u, v) before the size.
     */
    private static void layer(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size, int texSize) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, texSize, texSize, texSize, texSize);
    }

    /** Black over the whole screen outside the rectangle (x0, y0)-(x1, y1). */
    private static void blackAround(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int W, int H) {
        int k = 0xFF000000;
        if (y0 > 0) g.fill(0, 0, W, y0, k);
        if (y1 < H) g.fill(0, y1, W, H, k);
        int top = Math.max(0, y0), bot = Math.min(H, y1);
        if (x0 > 0 && bot > top) g.fill(0, top, x0, bot, k);
        if (x1 < W && bot > top) g.fill(x1, top, W, bot, k);
    }

    private ScopeOverlay() {}
}
