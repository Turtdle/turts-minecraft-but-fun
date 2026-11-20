package com.modernarmory.client;

import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.GunMasteryData;
import com.modernarmory.mastery.Mastery;
import com.modernarmory.network.MasterySyncPayload;
import com.modernarmory.registry.ModPlayerData;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client side of gun mastery: receives synced data, shows it in tooltips and the HUD. */
public final class ClientMastery {
    public static void onSync(MasterySyncPayload p) {
        Player player = Minecraft.getInstance().player;
        if (player != null) player.setData(ModPlayerData.MASTERY, p.data());
    }

    static GunMasteryData data() {
        Player player = Minecraft.getInstance().player;
        return player == null ? GunMasteryData.EMPTY : player.getData(ModPlayerData.MASTERY);
    }

    static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof GunItem gun) || event.getEntity() == null) return;
        int xp = data().xp(gun.gunId()), lvl = Mastery.level(xp);
        String line = lvl >= Mastery.MAX_LEVEL ? "Mastery V (max)"
                : "Mastery " + Mastery.roman(lvl) + "  " + xp + " / " + Mastery.nextThreshold(lvl) + " XP";
        event.getToolTip().add(1, Component.literal(line).withStyle(ChatFormatting.GOLD));
    }

    /** Stars and a thin progress bar, drawn under the ammo counter by GunHud. */
    static void drawHud(GuiGraphics g, Font font, GunItem gun, int rightX, int y) {
        int xp = data().xp(gun.gunId()), lvl = Mastery.level(xp);
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= Mastery.MAX_LEVEL; i++) stars.append(i <= lvl ? '★' : '☆');
        String s = stars.toString();
        int w = font.width(s);
        g.drawString(font, s, rightX - w, y, lvl >= Mastery.MAX_LEVEL ? 0xFFFFD040 : 0xFFE0B040, true);
        if (lvl < Mastery.MAX_LEVEL) {
            int bw = w, bx = rightX - bw, by = y + 10;
            g.fill(bx, by, bx + bw, by + 2, 0x80000000);
            g.fill(bx, by, bx + Math.round(bw * Mastery.progress(xp)), by + 2, 0xFFE0B040);
        }
    }

    private ClientMastery() {}
}
