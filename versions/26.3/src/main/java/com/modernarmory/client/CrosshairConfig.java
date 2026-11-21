package com.modernarmory.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The gun crosshair's look (client config, edited in CrosshairEditorScreen). {@link Style} is an
 * immutable snapshot: GunCrosshair draws from {@link Style#current()}, the editor works on a copy and
 * only writes it back when you press Done.
 */
public final class CrosshairConfig {
    /** Editor / config limits, in screen pixels (the red dot is about 2 px; a GUI pixel is 2-4 px). */
    public static final int MAX_LENGTH = 48, MAX_THICKNESS = 16, MAX_GAP = 48;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Gun crosshair (edit it in game: Mods > Modern Armory > Config > Crosshair editor)").push("crosshair");
    }

    public static final ModConfigSpec.IntValue LENGTH = B.comment("Length of each tick, in screen pixels")
            .defineInRange("length", 3, 1, MAX_LENGTH);
    public static final ModConfigSpec.IntValue THICKNESS = B.comment("Thickness of the ticks and dot, in screen pixels")
            .defineInRange("thickness", 1, 1, MAX_THICKNESS);
    public static final ModConfigSpec.IntValue GAP = B.comment("Extra gap between the centre and the ticks, in screen pixels")
            .defineInRange("gap", 0, 0, MAX_GAP);
    /**
     * Older versions measured length / thickness / gap in GUI pixels (1 GUI pixel = GUI-scale screen pixels).
     * False until those values have been converted once ({@link Style#current()}), so a saved crosshair keeps its size.
     */
    public static final ModConfigSpec.BooleanValue SCREEN_PIXELS = B
            .comment("Internal: true once the sizes above are in screen pixels (older versions used GUI pixels)")
            .define("screenPixels", false);
    public static final ModConfigSpec.IntValue OPACITY = B.comment("Opacity, percent")
            .defineInRange("opacity", 90, 10, 100);
    public static final ModConfigSpec.IntValue COLOR = B.comment("Colour, 0xRRGGBB")
            .defineInRange("color", 0xFFFFFF, 0, 0xFFFFFF);
    public static final ModConfigSpec.BooleanValue DOT = B.comment("Draw a centre dot").define("dot", true);
    public static final ModConfigSpec.BooleanValue OUTLINE = B.comment("Dark outline so it reads on bright backgrounds")
            .define("outline", true);
    public static final ModConfigSpec.BooleanValue DYNAMIC = B.comment("Ticks open up with movement spread (walking, sprinting, jumping)")
            .define("dynamic", true);
    public static final ModConfigSpec.BooleanValue T_STYLE = B.comment("T-style: no top tick").define("t_style", false);

    static {
        B.pop();
    }

    static {
        B.comment("Drones").push("drones");
    }

    /** Mouse sensitivity while flying a drone in acro mode, relative to the look sensitivity (DronePilot). */
    public static final ModConfigSpec.DoubleValue DRONE_MOUSE = B
            .comment("Mouse sensitivity while flying a drone (the mouse pitches and rolls it), relative to your look sensitivity")
            .defineInRange("droneMouseSensitivity", 0.6, 0.05, 2.0);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    /** One crosshair look; length, thickness and gap are in screen pixels. */
    public record Style(int length, int thickness, int gap, int opacity, int rgb, boolean dot, boolean outline,
                        boolean dynamic, boolean tStyle) {
        /** The old default (3 / 1 / 0 GUI pixels) at GUI scale 3. */
        public static final Style DEFAULT = new Style(9, 3, 0, 90, 0xFFFFFF, true, true, true, false);

        /** What the config says now (defaults until the config has loaded). */
        public static Style current() {
            if (!SPEC.isLoaded()) return DEFAULT;
            if (!SCREEN_PIXELS.get()) convertFromGuiPixels();
            return new Style(LENGTH.get(), THICKNESS.get(), GAP.get(), OPACITY.get(), COLOR.get(), DOT.get(),
                    OUTLINE.get(), DYNAMIC.get(), T_STYLE.get());
        }

        /** One-off: sizes saved in GUI pixels become the same size in screen pixels at the current GUI scale. */
        private static void convertFromGuiPixels() {
            int s = Math.max(1, (int) Math.round(net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
            LENGTH.set(Math.min(MAX_LENGTH, LENGTH.get() * s));
            THICKNESS.set(Math.min(MAX_THICKNESS, THICKNESS.get() * s));
            GAP.set(Math.min(MAX_GAP, GAP.get() * s));
            SCREEN_PIXELS.set(true);
            SPEC.save();
        }

        /** Write this style to the config file. */
        public void save() {
            LENGTH.set(length);
            THICKNESS.set(thickness);
            GAP.set(gap);
            OPACITY.set(opacity);
            COLOR.set(rgb);
            DOT.set(dot);
            OUTLINE.set(outline);
            DYNAMIC.set(dynamic);
            T_STYLE.set(tStyle);
            SCREEN_PIXELS.set(true);
            SPEC.save();
        }

        public Style withLength(int v) { return new Style(v, thickness, gap, opacity, rgb, dot, outline, dynamic, tStyle); }
        public Style withThickness(int v) { return new Style(length, v, gap, opacity, rgb, dot, outline, dynamic, tStyle); }
        public Style withGap(int v) { return new Style(length, thickness, v, opacity, rgb, dot, outline, dynamic, tStyle); }
        public Style withOpacity(int v) { return new Style(length, thickness, gap, v, rgb, dot, outline, dynamic, tStyle); }
        public Style withRgb(int v) { return new Style(length, thickness, gap, opacity, v, dot, outline, dynamic, tStyle); }
        public Style withDot(boolean v) { return new Style(length, thickness, gap, opacity, rgb, v, outline, dynamic, tStyle); }
        public Style withOutline(boolean v) { return new Style(length, thickness, gap, opacity, rgb, dot, v, dynamic, tStyle); }
        public Style withDynamic(boolean v) { return new Style(length, thickness, gap, opacity, rgb, dot, outline, v, tStyle); }
        public Style withTStyle(boolean v) { return new Style(length, thickness, gap, opacity, rgb, dot, outline, dynamic, v); }
    }

    private CrosshairConfig() {}
}
