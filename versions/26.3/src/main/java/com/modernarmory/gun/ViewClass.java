package com.modernarmory.gun;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * How a class of guns is framed on screen: first-person hip position / scale, third-person hold,
 * hip-fire cant. The numbers live in ONE file, {@value #RESOURCE}, read both here (for the ADS
 * maths in ClientState) and by tools/modelgen/display.py (which writes every gun's
 * models/item/&lt;gun&gt;.json display transforms from it). Changing a class's framing mod-wide is
 * one edit there plus {@code python tools/modelgen/display.py}; the autotest checks that the
 * baked item models still match ({@code [autotest] framing ...}).
 */
public enum ViewClass {
    /** Shouldered long guns: carbines, rifles, SMGs, shotguns, DMRs, bolt guns (tuned on the M4 / M2010). */
    RIFLE,
    /** Handguns held out at arm's length. */
    PISTOL,
    /** Big, heavy guns carried low: belt-fed and heavy machine guns, anti-materiel rifles. */
    HEAVY,
    /** Hand grenades held in the right fist, the left hand beside it for the pin. */
    GRENADE,
    /** Handheld devices: a packed drone carried in the right hand, the two-handed drone controller. */
    DEVICE;

    public static final String RESOURCE = "/assets/modernarmory/view_classes.json";

    public Framing framing() {
        return Loaded.FRAMINGS.get(this);
    }

    /** One item display transform as in a model JSON: rotation (deg), translation (1/16 block), scale. */
    public record Transform(float[] rotation, float[] translation, float[] scale) {}

    /**
     * @param firstPerson  firstperson_righthand display transform
     * @param thirdPerson  thirdperson_righthand display transform
     * @param hipYawDeg    hip-fire cant: yaw toward the crosshair, blended out while aiming
     */
    public record Framing(Transform firstPerson, Transform thirdPerson, float hipYawDeg) {
        /**
         * Vanilla places the held item at this view-space offset before its display transform
         * (ItemInHandRenderer.applyItemArmTransform, right hand, fully equipped).
         */
        public static final float HAND_X = 0.56f, HAND_Y = -0.52f, HAND_Z = -0.72f;

        /** First-person model scale (model units / 16 -> blocks). */
        public float fpScale() {
            return firstPerson.scale()[0];
        }

        /** View-space position (blocks) of the gun model's origin at the hip. */
        public float originX() { return HAND_X + firstPerson.translation()[0] / 16f; }
        public float originY() { return HAND_Y + firstPerson.translation()[1] / 16f; }
        public float originZ() { return HAND_Z + firstPerson.translation()[2] / 16f; }
    }

    /** Lazy holder: the JSON is read on first use (client rendering / autotest), never on servers that don't need it. */
    private static final class Loaded {
        static final Map<ViewClass, Framing> FRAMINGS = load();

        private static Map<ViewClass, Framing> load() {
            try (InputStream in = ViewClass.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("missing " + RESOURCE);
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<ViewClass, Framing> m = new EnumMap<>(ViewClass.class);
                for (ViewClass vc : values()) {
                    JsonObject o = root.getAsJsonObject(vc.name());
                    if (o == null) throw new IllegalStateException(RESOURCE + ": no entry for " + vc);
                    m.put(vc, new Framing(transform(o, "firstperson_righthand"), transform(o, "thirdperson_righthand"),
                            o.get("hip_yaw_deg").getAsFloat()));
                }
                return m;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot read " + RESOURCE, e);
            }
        }

        private static Transform transform(JsonObject cls, String key) {
            JsonObject t = cls.getAsJsonObject(key);
            return new Transform(vec(t, "rotation"), vec(t, "translation"), vec(t, "scale"));
        }

        private static float[] vec(JsonObject t, String key) {
            JsonArray a = t.getAsJsonArray(key);
            return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
        }
    }
}
