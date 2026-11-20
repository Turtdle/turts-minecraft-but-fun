package com.modernarmory.client;

import com.modernarmory.gun.Guns;
import com.modernarmory.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The "Gun Mastery" tab on top of the survival inventory: a vanilla-style tab with a gun icon that opens
 * MasteryScreen. It follows the inventory when the recipe book shifts it.
 */
public final class MasteryTab {
    static final int TAB_W = 28, TAB_H = 32;

    static ItemStack icon() {
        return new ItemStack(ModItems.gun(Guns.M4));
    }

    /** Add the tab whenever the survival inventory opens. */
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) event.addListener(new Tab(inv));
    }

    /** A tab standing on top of a panel whose top edge is y; selected tabs merge into the panel. */
    static void drawTab(GuiGraphicsExtractor g, int x, int panelTop, boolean selected, ItemStack icon, boolean hovered) {
        int y = panelTop - TAB_H + 4, h = selected ? TAB_H : TAB_H - 4;
        int fill = selected ? 0xFFC6C6C6 : hovered ? 0xFFB0B0B0 : 0xFF9A9A9A;
        g.fill(x, y, x + TAB_W, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + TAB_W - 1, y + h - (selected ? 0 : 1), fill);
        g.fill(x + 1, y + 1, x + TAB_W - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + TAB_W - 2, y + 2, x + TAB_W - 1, y + h - (selected ? 0 : 1), 0xFF555555);
        g.item(icon, x + 6, y + 8);
    }

    /** The "back to inventory" tab on MasteryScreen (left end of its top edge). */
    static boolean overBackTab(int left, int top, double mx, double my) {
        int x = left + 4, y = top - TAB_H + 4;
        return mx >= x && mx < x + TAB_W && my >= y && my < top;
    }

    private static final class Tab extends AbstractWidget {
        private final InventoryScreen screen;

        Tab(InventoryScreen screen) {
            super(0, 0, TAB_W, TAB_H - 4, Component.literal("Gun Mastery"));
            this.screen = screen;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Gun Mastery")));
            follow();
        }

        /** Top-right corner of the inventory panel (it moves when the recipe book opens). */
        private void follow() {
            setX(screen.getLeftPos() + screen.getImageWidth() - TAB_W - 4);
            setY(screen.getTopPos() - TAB_H + 4);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
            follow();
            drawTab(g, getX(), screen.getTopPos(), false, icon(), isHovered());
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            Minecraft.getInstance().gui.setScreen(new MasteryScreen());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private MasteryTab() {}
}
