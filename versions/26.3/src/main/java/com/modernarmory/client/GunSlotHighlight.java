package com.modernarmory.client;

import java.lang.reflect.Field;

import com.modernarmory.gun.GunCarry;
import com.modernarmory.item.GunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Inventory: while the mouse is over a gun (or one is held on the cursor), every other gun the player carries is
 * outlined, and the count against the limit (gun.GunCarry) is shown over the panel ("Guns 2/3", red if over).
 */
public final class GunSlotHighlight {
    /** Outline and fill of a highlighted gun slot. */
    static final int OUTLINE = 0xFFFFC233, FILL = 0x50FFC233;
    /** For screenshots: highlight as if a gun were hovered. */
    static boolean forceActive;
    private static Field leftPos, topPos, hoveredSlot;
    private static boolean broken;

    /** 26.x: the screen's foreground pass, in screen coordinates; the panel position and hovered slot are protected. */
    static void onForeground(ScreenEvent.Render.Foreground event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !fields()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int left, top;
        Slot hovered;
        try {
            left = leftPos.getInt(screen);
            top = topPos.getInt(screen);
            hovered = (Slot) hoveredSlot.get(screen);
        } catch (IllegalAccessException e) {
            broken = true;
            return;
        }
        ItemStack carried = screen.getMenu().getCarried();
        boolean active = forceActive || carried.getItem() instanceof GunItem
                || (hovered != null && hovered.getItem().getItem() instanceof GunItem);
        if (!active) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        for (Slot s : screen.getMenu().slots) {
            if (s == hovered || s.container != player.getInventory() || !(s.getItem().getItem() instanceof GunItem)) continue;
            box(g, left + s.x, top + s.y);
        }
        int count = GunCarry.count(player), limit = GunCarry.limit(player);
        g.text(Minecraft.getInstance().font, "Guns " + count + "/" + limit, left + 8, top - 10, count > limit ? 0xFFFF5555 : 0xFFFFE08A, true);
    }

    static void box(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, FILL);
        g.fill(x - 1, y - 1, x + 17, y, OUTLINE);
        g.fill(x - 1, y + 16, x + 17, y + 17, OUTLINE);
        g.fill(x - 1, y, x, y + 16, OUTLINE);
        g.fill(x + 16, y, x + 17, y + 16, OUTLINE);
    }

    private static boolean fields() {
        if (leftPos != null) return true;
        if (broken) return false;
        try {
            leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            hoveredSlot = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            hoveredSlot.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    private GunSlotHighlight() {}
}
