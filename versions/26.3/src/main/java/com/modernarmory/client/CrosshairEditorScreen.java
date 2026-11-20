package com.modernarmory.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Crosshair editor: controls on the left, a live preview on the right (over sky and grass, with the gap
 * breathing as if you were walking when "Moves with spread" is on). Works on a copy of the style; Done
 * saves it to the client config, Cancel / Esc throws it away.
 */
public class CrosshairEditorScreen extends Screen {
    /** Colour presets the colour button cycles through (the R/G/B sliders fine-tune). */
    private static final int[] PRESETS = {0xFFFFFF, 0x55FF55, 0x00FFFF, 0xFFFF55, 0xFF5555, 0xFF55FF};
    private static final String[] PRESET_NAMES = {"White", "Green", "Cyan", "Yellow", "Red", "Magenta"};
    private static final int ROW = 22;

    private final Screen parent;
    private CrosshairConfig.Style style;
    private int left, top, previewX, previewY, preview;

    public CrosshairEditorScreen(Screen parent) {
        super(Component.literal("Crosshair Editor"));
        this.parent = parent;
        this.style = CrosshairConfig.Style.current();
    }

    @Override
    protected void init() {
        // fit the window: two columns of controls and a square preview, buttons underneath
        preview = Mth.clamp(Math.min(width / 3, height - 120), 80, 140);
        int totalW = Math.min(width - 16, 150 * 2 + 10 + preview);
        int colW = (totalW - preview - 10) / 2;
        int controlsH = ROW * 6, bodyH = Math.max(controlsH, preview + 22);
        left = (width - totalW) / 2;
        top = Math.max(22, (height - (bodyH + 30)) / 2);
        previewX = left + colW * 2 + 10;
        previewY = top;
        int x1 = left, x2 = left + colW + 5, w = colW - 5;

        int y = top;
        addRenderableWidget(slider(x1, y, w, "Length", 1, 12, style.length(), "", v -> style = style.withLength(v)));
        addRenderableWidget(slider(x2, y, w, "Thickness", 1, 4, style.thickness(), "", v -> style = style.withThickness(v)));
        y += ROW;
        addRenderableWidget(slider(x1, y, w, "Gap", 0, 12, style.gap(), "", v -> style = style.withGap(v)));
        addRenderableWidget(slider(x2, y, w, "Opacity", 10, 100, style.opacity(), "%", v -> style = style.withOpacity(v)));
        y += ROW;
        addRenderableWidget(slider(x1, y, w, "Red", 0, 255, (style.rgb() >> 16) & 255, "",
                v -> style = style.withRgb((style.rgb() & 0x00FFFF) | (v << 16))));
        addRenderableWidget(slider(x2, y, w, "Green", 0, 255, (style.rgb() >> 8) & 255, "",
                v -> style = style.withRgb((style.rgb() & 0xFF00FF) | (v << 8))));
        y += ROW;
        addRenderableWidget(slider(x1, y, w, "Blue", 0, 255, style.rgb() & 255, "",
                v -> style = style.withRgb((style.rgb() & 0xFFFF00) | v)));
        addRenderableWidget(Button.builder(Component.literal("Colour: " + presetName()), b -> {
            int i = (presetIndex() + 1) % PRESETS.length;
            style = style.withRgb(PRESETS[i]);
            rebuildWidgets(); // move the R/G/B sliders to the preset
        }).bounds(x2, y, w, 20).build());
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(style.dot()).create(x1, y, w, 20, Component.literal("Centre dot"),
                (b, v) -> style = style.withDot(v)));
        addRenderableWidget(CycleButton.onOffBuilder(style.outline()).create(x2, y, w, 20, Component.literal("Outline"),
                (b, v) -> style = style.withOutline(v)));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(style.dynamic()).create(x1, y, w, 20, Component.literal("Moves with spread"),
                (b, v) -> style = style.withDynamic(v)));
        addRenderableWidget(CycleButton.onOffBuilder(style.tStyle()).create(x2, y, w, 20, Component.literal("T-style"),
                (b, v) -> style = style.withTStyle(v)));
        y = top + bodyH + 6;
        int bw = (totalW - 10) / 3;
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            style = CrosshairConfig.Style.DEFAULT;
            rebuildWidgets();
        }).bounds(left, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(left + bw + 5, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            style.save();
            onClose();
        }).bounds(left + (bw + 5) * 2, y, bw, 20).build());
    }

    private int presetIndex() {
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == style.rgb()) return i;
        return -1;
    }

    private String presetName() {
        int i = presetIndex();
        return i < 0 ? "Custom" : PRESET_NAMES[i];
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, top - 16, 0xFFFFFFFF);
        // preview: sky over grass, like looking down a field
        int x0 = previewX, y0 = previewY, x1 = x0 + preview, y1 = y0 + preview;
        g.fillGradient(x0, y0, x1, y0 + preview * 3 / 5, 0xFF6F9BE0, 0xFFB9D4F5);
        g.fillGradient(x0, y0 + preview * 3 / 5, x1, y1, 0xFF5E8E3A, 0xFF3F6A26);
        g.outline(x0 - 1, y0 - 1, preview + 2, preview + 2, 0xFF000000);
        // with "Moves with spread" on, the gap breathes like walking would open it
        float walk = style.dynamic() ? (float) (0.5 - 0.5 * Math.cos(System.nanoTime() / 1e9 * 2.2)) : 0f;
        int gap = style.gap() + Math.round(walk * 6);
        g.pose().pushMatrix();
        g.pose().translate(x0 + preview / 2f, y0 + preview / 2f);
        g.pose().scale(2, 2); // shown at 2x so small changes are easy to see
        GunCrosshair.draw(g, 0, 0, gap, style, 1f);
        g.pose().popMatrix();
        g.text(font, style.dynamic() ? "Preview 2x, walking" : "Preview 2x", x0, y1 + 5, 0xFFA0A0A0, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private static AbstractSliderButton slider(int x, int y, int w, String name, int min, int max, int value, String unit,
                                               IntConsumer onChange) {
        return new AbstractSliderButton(x, y, w, 20, Component.empty(), (value - min) / (double) (max - min)) {
            {
                updateMessage();
            }

            private int current() {
                return min + (int) Math.round(Mth.clamp(this.value, 0, 1) * (max - min));
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal(name + ": " + current() + unit));
            }

            @Override
            protected void applyValue() {
                onChange.accept(current());
            }
        };
    }
}
