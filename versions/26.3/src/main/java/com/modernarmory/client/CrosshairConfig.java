package com.modernarmory.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The gun crosshair's look (client config, edited in CrosshairEditorScreen). {@link Style} is an
 * immutable snapshot: GunCrosshair draws from {@link Style#current()}, the editor works on a copy and
 * only writes it back when you press Done.
 */
public final class CrosshairConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Gun crosshair (edit it in game: Mods > Modern Armory > Config > Crosshair editor)").push("crosshair");
    }

    public static final ModConfigSpec.IntValue LENGTH = B.comment("Length of each tick, in GUI pixels")
            .defineInRange("length", 3, 1, 12);
    public static final ModConfigSpec.IntValue THICKNESS = B.comment("Thickness of the ticks and dot, in GUI pixels")
            .defineInRange("thickness", 1, 1, 4);
    public static final ModConfigSpec.IntValue GAP = B.comment("Extra gap between the centre and the ticks, in GUI pixels")
            .defineInRange("gap", 0, 0, 12);
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

    /** One crosshair look. */
    public record Style(int length, int thickness, int gap, int opacity, int rgb, boolean dot, boolean outline,
                        boolean dynamic, boolean tStyle) {
        public static final Style DEFAULT = new Style(3, 1, 0, 90, 0xFFFFFF, true, true, true, false);

        /** What the config says now (defaults until the config has loaded). */
        public static Style current() {
            if (!SPEC.isLoaded()) return DEFAULT;
            return new Style(LENGTH.get(), THICKNESS.get(), GAP.get(), OPACITY.get(), COLOR.get(), DOT.get(),
                    OUTLINE.get(), DYNAMIC.get(), T_STYLE.get());
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
