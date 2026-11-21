package com.modernarmory.client;

import java.io.Reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Paints a gun's inventory sprite from its icon geometry (tools/modelgen/item_icons.py export_geometry): the
 * faces of the gun with every attachment on, already shaded, projected and painter-sorted. Faces of hidden bones
 * (and their children) are left out, the rest fitted into the sprite and drawn with exactly the arithmetic of
 * tools/modelgen/sprite.py rasterize() (each pixel the nearest face over the centre of its middle supersample, a
 * 1-px dark outline), so a gun with its default attachments is its shipped sprite pixel for pixel and any other
 * loadout looks drawn by the same hand. Change one, change both.
 * Plain Java, no Minecraft: {@link GunIcons} turns the pixels into a texture.
 */
public final class GunIconPainter {
    /** One gun's icon geometry. Face {@code i}: bone {@code faceBone[i]}, colour {@code faceRgb[i]}, corners {@code faceXy[i]} (x0,y0..x3,y3, +y up). */
    public record Geometry(int size, int supersample, double margin, int outlineArgb, String[] bones, int[] parents,
                           int[] faceBone, int[] faceRgb, double[][] faceXy) {

        public static Geometry read(Reader in) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            JsonArray b = o.getAsJsonArray("bones"), p = o.getAsJsonArray("parents"), f = o.getAsJsonArray("faces");
            String[] bones = new String[b.size()];
            int[] parents = new int[b.size()];
            for (int i = 0; i < bones.length; i++) {
                bones[i] = b.get(i).getAsString();
                parents[i] = p.get(i).getAsInt();
            }
            int n = f.size();
            int[] fb = new int[n], rgb = new int[n];
            double[][] xy = new double[n][8];
            for (int i = 0; i < n; i++) {
                JsonArray a = f.get(i).getAsJsonArray();
                fb[i] = a.get(0).getAsInt();
                rgb[i] = a.get(1).getAsInt();
                for (int k = 0; k < 8; k++) xy[i][k] = a.get(2 + k).getAsDouble();
            }
            JsonArray ol = o.getAsJsonArray("outline");
            int outline = ol.get(3).getAsInt() << 24 | ol.get(0).getAsInt() << 16 | ol.get(1).getAsInt() << 8 | ol.get(2).getAsInt();
            return new Geometry(o.get("size").getAsInt(), o.get("supersample").getAsInt(), o.get("margin").getAsDouble(),
                    outline, bones, parents, fb, rgb, xy);
        }

        public int boneIndex(String name) {
            for (int i = 0; i < bones.length; i++) if (bones[i].equals(name)) return i;
            return -1;
        }
    }

    /**
     * The sprite (size x size ARGB, row by row from the top) with {@code hiddenBones} (by index) and everything
     * under them left out.
     */
    public static int[] paint(Geometry g, boolean[] hiddenBones) {
        int nb = g.bones().length;
        boolean[] gone = new boolean[nb];
        for (int i = 0; i < nb; i++) { // parents come before their children
            int par = g.parents()[i];
            gone[i] = hiddenBones[i] || (par >= 0 && gone[par]);
        }
        int n = g.faceBone().length;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (gone[g.faceBone()[i]]) continue;
            double[] p = g.faceXy()[i];
            for (int k = 0; k < 8; k += 2) {
                minX = Math.min(minX, p[k]);
                maxX = Math.max(maxX, p[k]);
                minY = Math.min(minY, p[k + 1]);
                maxY = Math.max(maxY, p[k + 1]);
            }
        }
        int size = g.size(), ss = g.supersample();
        int[] px = new int[size * size];
        if (minX > maxX) return px;
        double big = size * ss, margin = g.margin() * ss;
        double scale = Math.min((big - 2 * margin) / Math.max(maxX - minX, 1e-3), (big - 2 * margin) / Math.max(maxY - minY, 1e-3));
        double cx = big / 2 - (minX + maxX) / 2 * scale, cy = big / 2 + (minY + maxY) / 2 * scale;

        double[] sx = new double[4], sy = new double[4];
        for (int i = 0; i < n; i++) { // far to near: nearer faces paint over
            if (gone[g.faceBone()[i]]) continue;
            double[] p = g.faceXy()[i];
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                sx[k] = cx + p[2 * k] * scale;
                sy[k] = cy - p[2 * k + 1] * scale;
                x0 = Math.min(x0, sx[k]); x1 = Math.max(x1, sx[k]);
                y0 = Math.min(y0, sy[k]); y1 = Math.max(y1, sy[k]);
            }
            double area = 0;
            for (int k = 0; k < 4; k++) {
                int j = (k + 1) & 3;
                area += sx[k] * sy[j] - sx[j] * sy[k];
            }
            double sign = area >= 0 ? 1 : -1;
            int argb = 0xFF000000 | g.faceRgb()[i];
            // each sprite pixel samples the centre of its middle supersampled pixel (x*ss + ss/2)
            int pxFrom = Math.max(0, (int) Math.floor((x0 - ss / 2.0 - 1) / ss)), pxTo = Math.min(size - 1, (int) Math.ceil((x1 - ss / 2.0 + 1) / ss));
            int pyFrom = Math.max(0, (int) Math.floor((y0 - ss / 2.0 - 1) / ss)), pyTo = Math.min(size - 1, (int) Math.ceil((y1 - ss / 2.0 + 1) / ss));
            for (int y = pyFrom; y <= pyTo; y++) {
                double py = y * ss + ss / 2 + 0.5;
                for (int x = pxFrom; x <= pxTo; x++) {
                    if (inside(sx, sy, sign, x * ss + ss / 2 + 0.5, py)) px[y * size + x] = argb;
                }
            }
        }
        // 1-px dark border round the sprite so it reads on any slot background
        int[] out = px.clone();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((px[y * size + x] >>> 24) != 0) continue;
                if ((x + 1 < size && (px[y * size + x + 1] >>> 24) != 0) || (x > 0 && (px[y * size + x - 1] >>> 24) != 0)
                        || (y + 1 < size && (px[(y + 1) * size + x] >>> 24) != 0) || (y > 0 && (px[(y - 1) * size + x] >>> 24) != 0))
                    out[y * size + x] = g.outlineArgb();
            }
        }
        return out;
    }

    /** Supersampled pixels: a sample this close outside a face's edge still counts as on it (sprite.py EDGE_SLACK). */
    private static final double EDGE_SLACK = 0.25;

    /** Point in the convex quad, edges included ({@link #EDGE_SLACK}). */
    private static boolean inside(double[] sx, double[] sy, double sign, double x, double y) {
        for (int k = 0; k < 4; k++) {
            int j = (k + 1) & 3;
            double ex = sx[j] - sx[k], ey = sy[j] - sy[k];
            double len = Math.sqrt(ex * ex + ey * ey);
            if (len < 1e-9) continue;
            double d = sign * (ex * (y - sy[k]) - ey * (x - sx[k])) / len;
            if (d < -EDGE_SLACK) return false;
        }
        return true;
    }

    private GunIconPainter() {}
}
