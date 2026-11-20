package com.modernarmory.client;

import com.modernarmory.gun.GunCarry;
import com.modernarmory.item.GunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * Inventory: while the mouse is over a gun (or one is held on the cursor), every other gun the player carries is
 * outlined, and the count against the limit (gun.GunCarry) is shown over the panel ("Guns 2/3", red if over).
 */
public final class GunSlotHighlight {
    /** Outline and fill of a highlighted gun slot. */
    static final int OUTLINE = 0xFFFFC233, FILL = 0x50FFC233;
    /** For screenshots: highlight as if a gun were hovered. */
    static boolean forceActive;

    /** 1.21.1: drawn in the container's foreground pass, already translated to the panel's top-left. */
    static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Slot hovered = screen.getSlotUnderMouse();
        ItemStack carried = screen.getMenu().getCarried();
        boolean active = forceActive || carried.getItem() instanceof GunItem
                || (hovered != null && hovered.getItem().getItem() instanceof GunItem);
        if (!active) return;
        GuiGraphics g = event.getGuiGraphics();
        for (Slot s : screen.getMenu().slots) {
            if (s == hovered || s.container != player.getInventory() || !(s.getItem().getItem() instanceof GunItem)) continue;
            box(g, s.x, s.y);
        }
        int count = GunCarry.count(player), limit = GunCarry.limit(player);
        g.drawString(Minecraft.getInstance().font, "Guns " + count + "/" + limit, 8, -10, count > limit ? 0xFFFF5555 : 0xFFFFE08A, true);
    }

    static void box(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, FILL);
        g.fill(x - 1, y - 1, x + 17, y, OUTLINE);
        g.fill(x - 1, y + 16, x + 17, y + 17, OUTLINE);
        g.fill(x - 1, y, x, y + 16, OUTLINE);
        g.fill(x + 16, y, x + 17, y + 16, OUTLINE);
    }

    private GunSlotHighlight() {}
}
