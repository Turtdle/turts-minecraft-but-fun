package com.modernarmory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** The mod's settings (Mods > Modern Armory > Config): the crosshair editor and every other option. */
public class SettingsScreen extends Screen {
    private final ModContainer mod;
    private final Screen parent;

    public SettingsScreen(ModContainer mod, Screen parent) {
        super(Component.literal("Modern Armory Settings"));
        this.mod = mod;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200, x = (width - w) / 2, y = height / 2 - 34;
        addRenderableWidget(Button.builder(Component.literal("Crosshair editor..."),
                b -> minecraft.setScreen(new CrosshairEditorScreen(this))).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All settings..."),
                b -> minecraft.setScreen(new ConfigurationScreen(mod, this))).bounds(x, y + 24, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y + 56, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 56, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
