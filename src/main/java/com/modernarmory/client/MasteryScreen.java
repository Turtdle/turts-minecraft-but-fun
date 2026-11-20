package com.modernarmory.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.GunMasteryData;
import com.modernarmory.mastery.Mastery;
import com.modernarmory.registry.ModItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Gun mastery overview, opened from the tab on the survival inventory (MasteryTab): every gun with its
 * level, XP towards the next level and what its level does. Most-used guns first.
 */
public class MasteryScreen extends Screen {
    static final int W = 248, H = 214, ROW = 22, LIST_TOP = 22, LIST_ROWS = 8;
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, INK = 0xFF404040,
            MUTED = 0xFF6B6B6B, WELL = 0xFF8B8B8B, ROW_A = 0xFFB7B7B7, ROW_B = 0xFFAEAEAE, GOLD = 0xFFE0B040,
            GOLD_MAX = 0xFFFFD040, BAR_BG = 0xFF3A3A3A;

    private record Entry(GunItem gun, ItemStack icon, int xp, int level) {}

    private final List<Entry> entries = new ArrayList<>();
    private int left, top;
    private float scroll; // rows

    public MasteryScreen() {
        super(Component.literal("Gun Mastery"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        entries.clear();
        GunMasteryData data = ClientMastery.data();
        int index = 0;
        List<Entry> list = new ArrayList<>();
        for (var holder : ModItems.guns().values()) {
            GunItem gun = holder.get();
            int xp = data.xp(gun.gunId());
            list.add(new Entry(gun, new ItemStack(gun), xp, Mastery.level(xp)));
        }
        // most-used first; untouched guns keep catalog order
        list.sort(Comparator.comparingInt((Entry e) -> -e.xp()));
        entries.addAll(list);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - LIST_ROWS);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (float) sy, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (MasteryTab.overBackTab(left, top, mx, my)) {
            backToInventory();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            backToInventory();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void backToInventory() {
        if (minecraft != null && minecraft.player != null) minecraft.setScreen(new InventoryScreen(minecraft.player));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt); // dimmed world background
        MasteryTab.drawTab(g, left + 4, top, false, new ItemStack(net.minecraft.world.item.Items.CHEST), MasteryTab.overBackTab(left, top, mx, my));
        MasteryTab.drawTab(g, left + W - 32, top, true, MasteryTab.icon(), false);
        bevel(g, left, top, W, H);
        g.drawString(font, title, left + 8, top + 7, INK, false);
        int total = entries.stream().mapToInt(Entry::level).sum();
        String stars = "Stars: " + total + " / " + entries.size() * Mastery.MAX_LEVEL;
        g.drawString(font, stars, left + W - 8 - font.width(stars), top + 7, MUTED, false);

        // the list well
        int lx = left + 7, ly = top + LIST_TOP, lw = W - 14 - 8, lh = ROW * LIST_ROWS;
        g.fill(lx - 1, ly - 1, lx + lw + 9, ly + lh + 1, WELL);
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int first = (int) scroll;
        float frac = scroll - first;
        Entry hovered = null;
        for (int i = first; i < Math.min(entries.size(), first + LIST_ROWS + 1); i++) {
            int y = ly + Math.round((i - scroll) * ROW);
            Entry e = entries.get(i);
            g.fill(lx, y, lx + lw, y + ROW, i % 2 == 0 ? ROW_A : ROW_B);
            drawRow(g, e, lx, y, lw);
            if (mx >= lx && mx < lx + lw && my >= Math.max(y, ly) && my < Math.min(y + ROW, ly + lh)) hovered = e;
        }
        g.disableScissor();
        // scrollbar
        int sbx = lx + lw + 2, sbh = lh;
        g.fill(sbx, ly, sbx + 6, ly + sbh, 0xFF6F6F6F);
        int knob = Math.max(12, sbh * LIST_ROWS / Math.max(LIST_ROWS, entries.size()));
        int ky = ly + (maxScroll() == 0 ? 0 : Math.round((sbh - knob) * scroll / maxScroll()));
        g.fill(sbx, ky, sbx + 6, ky + knob, 0xFFE0E0E0);
        g.fill(sbx + 1, ky + 1, sbx + 6, ky + knob, 0xFFA0A0A0);
        small(g, "Scroll for more guns", left + 8, top + H - 13, MUTED);
        if (hovered != null) g.renderTooltip(font, tooltip(hovered), java.util.Optional.empty(), mx, my);
        else if (MasteryTab.overBackTab(left, top, mx, my)) g.renderTooltip(font, Component.literal("Inventory"), mx, my);
    }

    private void drawRow(GuiGraphics g, Entry e, int x, int y, int w) {
        g.renderItem(e.icon(), x + 3, y + 3);
        g.drawString(font, e.icon().getHoverName(), x + 23, y + 3, INK, false);
        // stars
        StringBuilder s = new StringBuilder();
        for (int i = 1; i <= Mastery.MAX_LEVEL; i++) s.append(i <= e.level() ? '★' : '☆');
        g.drawString(font, s.toString(), x + w - 4 - font.width(s.toString()), y + 3, e.level() >= Mastery.MAX_LEVEL ? GOLD_MAX : GOLD, false);
        // XP bar + numbers
        int bx = x + 23, by = y + 14, bw = w - 23 - 70;
        g.fill(bx, by, bx + bw, by + 4, BAR_BG);
        g.fill(bx, by, bx + Math.round(bw * Mastery.progress(e.xp())), by + 4, e.level() >= Mastery.MAX_LEVEL ? GOLD_MAX : GOLD);
        String xp = e.level() >= Mastery.MAX_LEVEL ? "MAX" : e.xp() + " / " + Mastery.nextThreshold(e.level()) + " XP";
        small(g, xp, x + w - 4 - Math.round(font.width(xp) * 0.75f), by - 1, INK);
    }

    private List<Component> tooltip(Entry e) {
        List<Component> t = new ArrayList<>();
        t.add(e.icon().getHoverName());
        t.add(Component.literal("Mastery " + Mastery.roman(e.level()) + (e.level() >= Mastery.MAX_LEVEL ? " (max)" : "")).withStyle(s -> s.withColor(GOLD)));
        Mastery.Effects fx = Mastery.effects(e.level());
        if (e.level() == 0) t.add(Component.literal("No bonuses yet").withStyle(s -> s.withColor(0xAAAAAA)));
        else {
            t.add(Component.literal(pct(fx.movingSpread()) + " spread while moving").withStyle(s -> s.withColor(0x7EE07E)));
            t.add(Component.literal(pct(fx.reload()) + " reload time").withStyle(s -> s.withColor(0x7EE07E)));
            t.add(Component.literal(pct(fx.sway()) + " sway").withStyle(s -> s.withColor(0x7EE07E)));
            t.add(Component.literal(pct(fx.aimBob()) + " view bob while aiming").withStyle(s -> s.withColor(0x7EE07E)));
            t.add(Component.literal(pct(fx.hipSpread()) + " hip-fire spread").withStyle(s -> s.withColor(0x7EE07E)));
        }
        if (e.level() < Mastery.MAX_LEVEL)
            t.add(Component.literal((Mastery.nextThreshold(e.level()) - e.xp()) + " XP to Mastery " + Mastery.roman(e.level() + 1)).withStyle(s -> s.withColor(0xAAAAAA)));
        return t;
    }

    private static String pct(float mult) {
        return Math.round((mult - 1) * 100) + "%";
    }

    private void small(GuiGraphics g, String s, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Vanilla-style raised panel. */
    static void bevel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, SHADOW);
    }
}
