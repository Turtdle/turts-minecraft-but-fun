package com.modernarmory.client;

import com.modernarmory.item.GunItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * FPS-style dynamic crosshair while holding a gun: four ticks around a dot, with the gap showing
 * the real cone your next shot can land in (same spread rules as the server: hip-fire, moving,
 * sprinting, airborne, crouching), plus a short "bloom" after each shot. Hidden while aiming.
 */
final class GunCrosshair {
    private static float shownGapDeg = -1;
    private static long lastNanos;

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson()) return;
        if (!(p.getMainHandItem().getItem() instanceof GunItem gun)) return;
        float aim = ClientState.aimProgress();
        if (aim >= 0.99f) return;

        // A tight "+" at rest; it only opens up with movement (same extra spread the server adds
        // for walking / sprinting / airborne, shown as the ~68% cone).
        double extra = com.modernarmory.gun.ShooterSpread.movement(p, false);
        if (p.isCrouching()) extra *= com.modernarmory.gun.ShooterSpread.CROUCH;
        extra *= com.modernarmory.mastery.MasteryHandler.effects(p, gun).movingSpread(); // mastery
        // stance (gun.Support): a tripod gun fired standing sprays; show the extra hip spread too
        float stance = gun.spec().support().spread(p.isCrouching());
        if (stance != 1f) extra = extra * stance + gun.spec().hipSpreadDeg() * (stance - 1);
        float target = (float) extra * 0.5f;

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        shownGapDeg = shownGapDeg < 0 ? target : shownGapDeg + (target - shownGapDeg) * (1 - (float) Math.exp(-dt * 14));

        // degrees -> GUI pixels for the current vertical FOV
        double fov = mc.options.fov().get() * (aim > 0 ? Mth.lerp(aim, 1.0f, 0.8f) : 1.0f);
        int h = g.guiHeight(), w = g.guiWidth();
        float gap = (float) (Math.tan(Math.toRadians(shownGapDeg)) / Math.tan(Math.toRadians(fov / 2)) * (h / 2.0));
        gap = Mth.clamp(gap, 0f, h / 4f); // 0 = ticks touch the centre dot
        CrosshairConfig.Style st = CrosshairConfig.Style.current();
        draw(g, w / 2, h / 2, (st.dynamic() ? Math.round(gap) : 0) + st.gap(), st, 1 - aim);
    }

    /**
     * Draws a crosshair in style {@code st} centred on (cx, cy) with {@code gap} pixels between the centre
     * and the ticks. Shared by the HUD and the crosshair editor's preview.
     */
    static void draw(GuiGraphicsExtractor g, int cx, int cy, int gap, CrosshairConfig.Style st, float alphaMul) {
        int alpha = Mth.clamp(Math.round(255 * st.opacity() / 100f * alphaMul), 0, 255);
        if (alpha == 0) return;
        int col = (alpha << 24) | st.rgb(), shadow = st.outline() ? ((alpha * 3 / 5) << 24) : 0;
        int t = st.thickness(), len = st.length();
        int x0 = cx - t / 2, y0 = cy - t / 2; // top-left of the centre block
        drawTick(g, x0 - gap - len, y0, len, t, col, shadow);     // left
        drawTick(g, x0 + t + gap, y0, len, t, col, shadow);       // right
        if (!st.tStyle()) drawTick(g, x0, y0 - gap - len, t, len, col, shadow); // top
        drawTick(g, x0, y0 + t + gap, t, len, col, shadow);       // bottom
        if (st.dot()) drawTick(g, x0, y0, t, t, col, shadow);     // centre dot
    }

    private static void drawTick(GuiGraphicsExtractor g, int x, int y, int w, int h, int col, int shadow) {
        if (shadow != 0) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, shadow);
        g.fill(x, y, x + w, y + h, col);
    }

    private GunCrosshair() {}
}
