package com.modernarmory.client;

import com.modernarmory.item.GunItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Bottom-right ammo counter: "28 / 30  BURST" plus rounds carried. */
final class GunHud {
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;

        com.modernarmory.attachment.GunStats stats = com.modernarmory.attachment.GunStats.of(gun, stack);
        int ammo = gun.ammo(stack);
        int carried = 0;
        for (ItemStack st : mc.player.getInventory().items) if (gun.isAmmo(st)) carried += st.getCount();
        Font font = mc.font;
        String main = ammo + " / " + stats.magazineSize();
        String sub = gun.fireMode(stack).label().toUpperCase() + "   " + (mc.player.isCreative() ? "∞" : carried);
        int w = g.guiWidth(), h = g.guiHeight();
        int color = ammo == 0 ? 0xFF5555 : ammo <= stats.magazineSize() / 5 ? 0xFFAA33 : 0xFFFFFF;
        g.pose().pushPose();
        g.pose().translate(w - 10, h - 34, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, main, -font.width(main), 0, color, true);
        g.pose().popPose();
        g.drawString(font, sub, w - 10 - font.width(sub), h - 18, 0xAAAAAA, true);
        if (gun.loadedTracer(stack) && gun.spec().cartridge().hasTracer()) {
            String t = "TRACER " + gun.spec().cartridge().tracer().designation();
            g.drawString(font, t, w - 10 - font.width(t), h - 8, 0xFF7A4A, true);
        }
        ClientMastery.drawHud(g, font, gun, w - 10, h - 46);
    }

    private GunHud() {}
}
