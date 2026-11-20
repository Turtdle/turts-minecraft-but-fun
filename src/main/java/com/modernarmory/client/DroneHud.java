package com.modernarmory.client;

import com.modernarmory.drone.DroneEntity;
import com.modernarmory.drone.DroneLink;
import com.modernarmory.drone.DroneType;
import com.modernarmory.drone.LinkLoss;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The drone camera's HUD (replaces the vanilla one while piloting): frame corners and reticle, battery and
 * flight time left, link bars, altitude above ground and above the pilot, speed, distance and heading, the
 * payload's state and action, zoom, and the pilot's own health (the body is out there, unguarded). A weak
 * link breaks the picture up; a lost one is static with what the autopilot is doing.
 */
final class DroneHud {
    private static final int WHITE = 0xFFF2F2F2, DIM = 0xB0E0E0E0, AMBER = 0xFFFFB020, RED = 0xFFFF4040, GREEN = 0xFF60FF80;
    private static final RandomSource NOISE = RandomSource.create();

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float lost = DronePilot.feedLost();
        if (DronePilot.strikeCam()) return; // after the FPV's detonation: a plain view of the blast, no overlay
        if (!DronePilot.active()) {
            if (lost > 0) {
                noise(g, w, h, lost);
                center(g, mc.font, "SIGNAL LOST", w / 2, h / 2 - 4, withAlpha(WHITE, lost));
            }
            return;
        }
        DroneEntity d = DronePilot.drone();
        LocalPlayer player = mc.player;
        if (d == null || player == null) return;
        DroneType t = d.type();
        Font font = mc.font;
        float signal = d.signal();
        boolean linked = d.linked();

        if (d.jammed()) {
            noise(g, w, h, 1f);
            jamming(g, w, h, mc.level.getGameTime());
        } else if (!linked) {
            noise(g, w, h, 1f);
        } else if (signal < 0.45f) {
            noise(g, w, h, Mth.clamp((0.45f - signal) / 0.35f, 0, 1) * 0.7f);
        }

        // frame corners + reticle
        int m = 14, len = 18;
        corner(g, m, m, len, 1, 1);
        corner(g, w - m, m, len, -1, 1);
        corner(g, m, h - m, len, 1, -1);
        corner(g, w - m, h - m, len, -1, -1);
        int cx = w / 2, cy = h / 2;
        if (t.gimbal()) {
            g.fill(cx - 9, cy, cx - 3, cy + 1, WHITE);
            g.fill(cx + 3, cy, cx + 9, cy + 1, WHITE);
            g.fill(cx, cy - 9, cx + 1, cy - 3, WHITE);
            g.fill(cx, cy + 3, cx + 1, cy + 9, WHITE);
        } else {
            ring(g, cx, cy, 10, WHITE);
            g.fill(cx, cy, cx + 1, cy + 1, WHITE);
        }

        // top left: what and recording
        g.drawString(font, t.name().toUpperCase(), m + 6, m + 6, WHITE);
        boolean blink = (mc.level.getGameTime() / 10) % 2 == 0;
        g.drawString(font, (blink ? "● " : "  ") + "REC", m + 6, m + 17, blink ? RED : DIM);

        // top right: battery, time left, link
        float bat = d.battery();
        int batColor = bat < 0.15f ? RED : bat < 0.3f ? AMBER : WHITE;
        int secs = Math.round(bat * t.enduranceSeconds());
        String batText = String.format("BAT %3d%%  %d:%02d", Math.round(bat * 100), secs / 60, secs % 60);
        right(g, font, batText, w - m - 6, m + 6, batColor);
        int bx = w - m - 6 - 40, by = m + 17;
        g.fill(bx, by, bx + 40, by + 4, 0x80000000);
        g.fill(bx, by, bx + Math.round(40 * bat), by + 4, batColor);
        int bars = linked ? Mth.clamp((int) Math.ceil(signal * 5), 1, 5) : 0;
        for (int i = 0; i < 5; i++) {
            int x = w - m - 6 - 40 - 32 + i * 5, hgt = 3 + i * 2;
            g.fill(x, by + 8 - hgt + 4, x + 3, by + 8 + 4, i < bars ? (bars <= 1 ? RED : bars <= 2 ? AMBER : WHITE) : 0x60FFFFFF);
        }
        right(g, font, d.jammed() ? "LINK JAMMED" : String.format("LINK %3d%%", Math.round(signal * 100)) + (d.relayed() ? " RELAY" : ""),
                w - m - 6, m + 28, linked ? DIM : RED);

        // bottom left: flight data
        double agl = heightAboveGround(d);
        double rel = d.getY() - player.getY();
        double speed = Math.sqrt(sq(d.getX() - d.xo) + sq(d.getY() - d.yo) + sq(d.getZ() - d.zo)) * 20;
        double dist = d.distanceTo(player);
        int heading = Math.floorMod(Math.round(d.getYRot()) + 180, 360);
        int y0 = h - m - 6 - 4 * 11;
        g.drawString(font, String.format("ALT %5.1f m AGL", agl), m + 6, y0, WHITE);
        g.drawString(font, String.format("    %+5.1f m PILOT", rel), m + 6, y0 + 11, DIM);
        g.drawString(font, String.format("SPD %5.1f m/s", speed), m + 6, y0 + 22, WHITE);
        g.drawString(font, String.format("DST %5.0f m  HDG %03d", dist, heading), m + 6, y0 + 33, WHITE);
        if (t.flightMode() == com.modernarmory.drone.FlightMode.ACRO) {
            // acro: the throttle stick (it stays where it is left) and the airframe's attitude; nothing levels it
            float thr = DronePilot.throttleStick();
            int ty = y0 - 26, tx = m + 6;
            g.drawString(font, String.format("THR %3d%%", Math.round(thr * 100)), tx, ty, WHITE);
            int bx0 = tx + 54;
            g.fill(bx0, ty + 2, bx0 + 50, ty + 6, 0x80000000);
            g.fill(bx0, ty + 2, bx0 + Math.round(50 * thr), ty + 6, WHITE);
            int hover = bx0 + Math.round(50 / t.thrustToWeight()); // where it hovers
            g.fill(hover, ty, hover + 1, ty + 8, AMBER);
            org.joml.Quaternionf q = d.attitude();
            org.joml.Vector3f fw = q.transform(new org.joml.Vector3f(0, 0, -1)), rt = q.transform(new org.joml.Vector3f(1, 0, 0)),
                    up = q.transform(new org.joml.Vector3f(0, 1, 0));
            int pitchDeg = Math.round((float) Math.toDegrees(Math.asin(Mth.clamp(fw.y, -1, 1))));
            int rollDeg = Math.round((float) Math.toDegrees(Math.atan2(-rt.y, up.y)));
            boolean inverted = up.y < 0;
            g.drawString(font, String.format("ATT P%+4d R%+4d", pitchDeg, rollDeg) + (inverted ? "  INVERTED" : ""), tx, ty + 11,
                    inverted ? AMBER : DIM);
            g.drawString(font, "ACRO", w / 2 - 12, m + 6, DIM);
        }

        // bottom right: payload, zoom, exit
        String exitKey = KeyBindings.DRONE_EXIT.getTranslatedKeyMessage().getString().toUpperCase();
        int ry = h - m - 6 - 4 * 11;
        float arm = d.armSeconds();
        if (arm > 0) right(g, font, String.format("SAFE %.1fs", arm), w - m - 6, ry, AMBER);
        else if (arm == 0) right(g, font, "ARMED", w - m - 6, ry, blink ? RED : 0xFFB02020);
        else if (DronePilot.spottedCount() > 0) right(g, font, "TAGGED " + DronePilot.spottedCount(), w - m - 6, ry, GREEN);
        right(g, font, "[LMB] " + t.payload().actionLabel(), w - m - 6, ry + 11, WHITE);
        if (t.maxZoom() > 1) right(g, font, String.format("ZOOM %.1fx", DronePilot.zoom()), w - m - 6, ry + 22, DIM);
        right(g, font, "[" + exitKey + "] EXIT", w - m - 6, ry + 33, DIM);

        // bottom centre: the pilot's body
        boolean hit = player.hurtTime > 0;
        String body = String.format("PILOT %d/%d", Math.round(player.getHealth()), Math.round(player.getMaxHealth()));
        center(g, font, hit ? "PILOT HIT! " + body : body, cx, h - m - 6 - 10, hit ? (blink ? RED : WHITE) : DIM);

        // warnings
        if (!linked) {
            center(g, font, d.jammed() ? "JAMMING DETECTED" : "NO SIGNAL", cx, cy - 26, d.jammed() && !blink ? WHITE : RED);
            String what = t.linkLoss() == LinkLoss.RETURN_HOME ? "RETURNING TO PILOT" : t.linkLoss() == LinkLoss.FAILSAFE_DROP
                    ? "FAILSAFE" : "HOLDING POSITION";
            center(g, font, what, cx, cy + 18, AMBER);
        } else if (signal < 0.25f) {
            center(g, font, "WEAK SIGNAL", cx, cy - 26, AMBER);
        }
        if (bat < 0.15f) center(g, font, bat <= 0 ? "BATTERY EMPTY" : "LOW BATTERY", cx, m + 6, blink ? RED : AMBER);
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Metres from the drone's underside down to the first solid block (64+ = "high"). */
    private static double heightAboveGround(DroneEntity d) {
        BlockPos.MutableBlockPos p = BlockPos.containing(d.getX(), d.getY() - 0.01, d.getZ()).mutable();
        for (int i = 0; i < 64; i++, p.move(0, -1, 0)) {
            var s = d.level().getBlockState(p);
            if (!s.getCollisionShape(d.level(), p).isEmpty()) return Math.max(0, d.getY() - (p.getY() + s.getCollisionShape(d.level(), p).max(net.minecraft.core.Direction.Axis.Y)));
        }
        return 64;
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy) {
        g.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len), y + dy, WHITE);
        g.fill(x, Math.min(y, y + dy * len), x + dx, Math.max(y, y + dy * len), WHITE);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int a = 0; a < 360; a += 10) {
            int x = cx + Math.round(r * Mth.cos(a * Mth.DEG_TO_RAD)), y = cy + Math.round(r * Mth.sin(a * Mth.DEG_TO_RAD));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Video break-up: grey speckle and torn lines, {@code amount} 0..1. */
    private static void noise(GuiGraphics g, int w, int h, float amount) {
        if (amount <= 0) return;
        int dots = Math.round(1400 * amount);
        for (int i = 0; i < dots; i++) {
            int x = NOISE.nextInt(w), y = NOISE.nextInt(h), s = 1 + NOISE.nextInt(3);
            int v = NOISE.nextInt(256);
            int a = Math.round((80 + NOISE.nextInt(160)) * Math.min(1, amount * 1.3f));
            g.fill(x, y, x + s * 2, y + s, (a << 24) | (v << 16) | (v << 8) | v);
        }
        int lines = Math.round(6 * amount);
        for (int i = 0; i < lines; i++) {
            int y = NOISE.nextInt(h), v = 120 + NOISE.nextInt(120);
            g.fill(0, y, w, y + 1 + NOISE.nextInt(2), (Math.round(110 * amount) << 24) | (v << 16) | (v << 8) | v);
        }
        if (amount >= 0.99f) g.fill(0, 0, w, h, 0x90101010);
    }

    /** A jammer drowning the video link: rolling bands of torn, colour-smeared picture over the static. */
    private static void jamming(GuiGraphics g, int w, int h, long time) {
        int bands = 5 + NOISE.nextInt(4);
        for (int i = 0; i < bands; i++) {
            int y = (int) ((NOISE.nextInt(h) + time * 7) % h), bh = 3 + NOISE.nextInt(18);
            int shift = NOISE.nextInt(w / 3) - w / 6;
            int v = 90 + NOISE.nextInt(140), tint = NOISE.nextInt(3);
            int r = tint == 0 ? Math.min(255, v + 60) : v, gr = tint == 1 ? Math.min(255, v + 50) : v, b = tint == 2 ? Math.min(255, v + 70) : v;
            g.fill(Math.max(0, shift), y, Math.min(w, w + shift), Math.min(h, y + bh), (0xA0 << 24) | (r << 16) | (gr << 8) | b);
        }
        int roll = (int) (time * 11 % h); // the picture losing sync: a dark bar rolling down
        g.fill(0, roll, w, Math.min(h, roll + 10), 0xC0000000);
    }

    private static int withAlpha(int argb, float a) {
        return (Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0, 1)) << 24) | (argb & 0xFFFFFF);
    }

    private static void right(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.drawString(font, s, x - font.width(s), y, color);
    }

    private static void center(GuiGraphics g, Font font, String s, int x, int y, int color) {
        g.drawString(font, s, x - font.width(s) / 2, y, color);
    }

    private DroneHud() {}
}
