package com.modernarmory.client;

import com.modernarmory.item.GunItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Bottom-right ammo counter: "28 / 30  BURST" plus rounds carried. */
final class GunHud {
    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;

        com.modernarmory.attachment.GunStats stats = com.modernarmory.attachment.GunStats.of(gun, stack);
        int ammo = ShotPredictor.ammo(gun, stack); // less our shots still on the way to the server
        int carried = 0;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) if (gun.isAmmo(st)) carried += st.getCount();
        Font font = mc.font;
        String main = ammo + " / " + stats.magazineSize();
        String sub = gun.fireMode(stack).label().toUpperCase() + "   " + (mc.player.isCreative() ? "∞" : carried);
        int w = g.guiWidth(), h = g.guiHeight();
        // 26.x GUI text colours are ARGB (an alpha of 0 draws nothing), 1.21.1 took RGB
        int color = 0xFF000000 | (ammo == 0 ? 0xFF5555 : ammo <= stats.magazineSize() / 5 ? 0xFFAA33 : 0xFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(w - 10, h - 34);
        g.pose().scale(1.5f, 1.5f);
        g.text(font, main, -font.width(main), 0, color, true);
        g.pose().popMatrix();
        g.text(font, sub, w - 10 - font.width(sub), h - 18, 0xFFAAAAAA, true);
        if (gun.loadedTracer(stack) && gun.spec().cartridge().hasTracer()) {
            String t = "TRACER " + gun.spec().cartridge().tracer().designation();
            g.text(font, t, w - 10 - font.width(t), h - 8, 0xFFFF7A4A, true);
        }
        ClientMastery.drawHud(g, font, gun, w - 10, h - 46);
    }

    private GunHud() {}
}
