package com.modernarmory.client;

import com.modernarmory.drone.DroneEntity;
import com.modernarmory.laser.TurretLayout;
import com.modernarmory.laser.TurretSeat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The laser turret operator's screen: the sensor picture's frame and reticle, a box on every drone the radar
 * has (white: in reach; grey: no line of sight or out of laser range; amber: what a click would lock; red: locked,
 * with how far it has burned), the laser's heat and power, and the mode (SEARCH / TRACK / BURNING / COOLING). Holding the turret's item: the placement hint under the crosshair.
 */
final class LaserHud {
    private static final int WHITE = 0xFFF2F2F2, DIM = 0xB0E0E0E0, GREY = 0x90A0A0A0, AMBER = 0xFFFFB020, RED = 0xFFFF4040,
            GREEN = 0xFF60FF80;

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        TurretSeat s = LaserOperator.seat();
        if (s == null) {
            placementHint(g, mc);
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2, m = 14;
        boolean blink = (mc.level.getGameTime() / 8) % 2 == 0;

        // frame and reticle
        int len = 18;
        corner(g, m, m, len, 1, 1);
        corner(g, w - m, m, len, -1, 1);
        corner(g, m, h - m, len, 1, -1);
        corner(g, w - m, h - m, len, -1, -1);
        g.fill(cx - 14, cy, cx - 4, cy + 1, WHITE);
        g.fill(cx + 5, cy, cx + 15, cy + 1, WHITE);
        g.fill(cx, cy - 14, cx + 1, cy - 4, WHITE);
        g.fill(cx, cy + 5, cx + 1, cy + 15, WHITE);

        // tracks
        DroneEntity target = s.target();
        DroneEntity next = target == null ? LaserOperator.pick(s) : null;
        Camera cam = mc.gameRenderer.mainCamera();
        for (LaserOperator.Track t : LaserOperator.tracks(s, pt)) {
            int[] sp = project(cam, t.at(), w, h);
            if (sp == null) continue;
            boolean engaged = t.drone() == target, picked = t.drone() == next;
            int col = engaged ? RED : picked ? AMBER : t.clear() ? WHITE : GREY;
            int half = Math.max(6, (int) (90 / Math.max(1, t.range()) * 8 * LaserOperator.zoom()));
            half = Math.min(half, 40);
            box(g, sp[0], sp[1], half, col);
            String label = String.format("%dm", Math.round(t.range()));
            if (!t.clear()) label += t.range() > s.spec().range() ? " RANGE" : " NO LOS";
            g.text(font, label, sp[0] + half + 3, sp[1] - half, col);
            if (engaged) {
                float burn = s.burn();
                g.fill(sp[0] - half, sp[1] + half + 3, sp[0] + half, sp[1] + half + 6, 0x80000000);
                g.fill(sp[0] - half, sp[1] + half + 3, sp[0] - half + Math.round(2 * half * burn), sp[1] + half + 6, RED);
                g.text(font, s.firing() ? (blink ? "BURN" : "") : "LOCK", sp[0] + half + 3, sp[1] - half + 10, RED);
            }
        }

        // top left: system and mode
        g.text(font, s.spec().name() + "  HEL " + Math.round(s.spec().powerKw()) + " kW", m + 6, m + 6, WHITE);
        String mode = s.overheated() ? "COOLING" : target == null ? "SEARCH" : s.firing() ? "BURNING" : "TRACK";
        int modeCol = s.overheated() ? AMBER : target == null ? GREEN : RED;
        g.text(font, mode, m + 6, m + 17, modeCol);

        // top right: heat, power
        float heat = s.heat();
        int hx = w - m - 6 - 60, hy = m + 6;
        rightText(g, font, "HEAT", hx - 4, hy, s.overheated() ? AMBER : DIM);
        g.fill(hx, hy + 1, hx + 60, hy + 7, 0x80000000);
        g.fill(hx, hy + 1, hx + Math.round(60 * heat), hy + 7, heat > 0.8f || s.overheated() ? RED : heat > 0.5f ? AMBER : WHITE);
        float power = target != null ? s.power() : 1;
        String weather = mc.level.isThundering() ? "  STORM" : mc.level.isRaining() ? "  RAIN" : "";
        rightText(g, font, String.format("POWER %3d%%", Math.round(power * 100)) + weather, w - m - 6, m + 17, power < 0.7f ? AMBER : DIM);

        // bottom left: where the turret points
        int az = Math.floorMod(Math.round(s.yaw(pt)) + 180, 360);
        g.text(font, String.format("AZ %03d  EL %+3d", az, Math.round(s.elevation(pt))), m + 6, h - m - 6 - 20, WHITE);
        g.text(font, String.format("ZOOM %.1fx", LaserOperator.zoom()), m + 6, h - m - 6 - 9, DIM);

        // bottom right: controls
        rightText(g, font, target == null ? "[LMB] LOCK & FIRE" : "[RMB] CEASE FIRE", w - m - 6, h - m - 6 - 20, WHITE);
        rightText(g, font, "[" + mc.options.keyShift.getTranslatedKeyMessage().getString().toUpperCase() + "] EXIT", w - m - 6, h - m - 6 - 9, DIM);

        if (s.overheated()) centre(g, font, "LASER OVERHEATED - COOLING", cx, cy + 26, blink ? AMBER : WHITE);
    }

    /** Holding the item: does it fit where you're aiming? */
    private static void placementHint(GuiGraphicsExtractor g, Minecraft mc) {
        TurretLayout.Placement p = LaserOperator.preview;
        if (p == null) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        centre(g, mc.font, p.ok() ? "Laser turret fits: right-click to place" : p.message(), cx, cy + 14, p.ok() ? GREEN : RED);
        centre(g, mc.font, "Seat here, turret facing the way you face", cx, cy + 25, DIM);
    }

    /** Screen position of a world point (null if behind the camera). */
    static int[] project(Camera cam, Vec3 at, int w, int h) {
        Vec3 rel = at.subtract(cam.position());
        org.joml.Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        double z = rel.x * f.x() + rel.y * f.y() + rel.z * f.z();
        if (z < 0.1) return null;
        double x = -(rel.x * l.x() + rel.y * l.y() + rel.z * l.z()), y = rel.x * u.x() + rel.y * u.y() + rel.z * u.z();
        double scale = (h / 2.0) / Math.tan(Math.toRadians(LaserOperator.fov()) / 2);
        return new int[] {(int) Math.round(w / 2.0 + x / z * scale), (int) Math.round(h / 2.0 - y / z * scale)};
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int half, int col) {
        int c = Math.max(3, half / 2);
        corner(g, x - half, y - half, c, 1, 1, col);
        corner(g, x + half, y - half, c, -1, 1, col);
        corner(g, x - half, y + half, c, 1, -1, col);
        corner(g, x + half, y + half, c, -1, -1, col);
    }

    private static void corner(GuiGraphicsExtractor g, int x, int y, int len, int dx, int dy) {
        corner(g, x, y, len, dx, dy, WHITE);
    }

    private static void corner(GuiGraphicsExtractor g, int x, int y, int len, int dx, int dy, int col) {
        g.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len), y + dy, col);
        g.fill(x, Math.min(y, y + dy * len), x + dx, Math.max(y, y + dy * len), col);
    }

    private static void rightText(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x - font.width(s), y, color);
    }

    private static void centre(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x - font.width(s) / 2, y, color);
    }

    static float clamp01(float v) {
        return Mth.clamp(v, 0, 1);
    }

    private LaserHud() {}
}
