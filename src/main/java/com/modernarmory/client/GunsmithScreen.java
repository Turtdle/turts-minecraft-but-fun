package com.modernarmory.client;

import java.util.ArrayList;
import java.util.List;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.modernarmory.menu.GunsmithMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Gunsmith Table screen: the gun slot in the middle of a felt bench with the part slots where the
 * parts mount, an arrow, and on the right a big preview of the modded gun with what its parts do.
 * Clicking anywhere on the preview takes the modded gun.
 */
public class GunsmithScreen extends AbstractContainerScreen<GunsmithMenu> {
    private static final ResourceLocation BG = ModernArmory.id("textures/gui/gunsmith.png");
    /** The result panel (GUI px, relative to the window): preview on top, stats below. */
    public static final int PX0 = 172, PY0 = 15, PX1 = 246, PY1 = 101, PREVIEW_Y1 = 58;
    private static final int GOOD = 0xFF7EE07E, BAD = 0xFFE6766A, PLAIN = 0xFFC8CDD4;
    /** Preview size (x the 16 px item), and the gun in the big middle slot. */
    private static final float PREVIEW_SCALE = 3.5f, GUN_SLOT_SCALE = 1.5f;
    /** Item-frame framing is the inventory framing x 1.67 (tools/modelgen/display.py fitted_icons). */
    private static final float FIXED_TO_GUI = 1.67f;

    public GunsmithScreen(GunsmithMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = GunsmithMenu.WIDTH;
        imageHeight = GunsmithMenu.HEIGHT;
        inventoryLabelX = GunsmithMenu.INV_X;
        inventoryLabelY = GunsmithMenu.INV_Y - 11;
    }

    private boolean overPreview(double mx, double my) {
        return mx >= leftPos + PX0 && mx < leftPos + PX1 && my >= topPos + PY0 && my < topPos + PREVIEW_Y1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overPreview(mx, my) && !menu.result().isEmpty() && (button == 0 || button == 1)) {
            slotClicked(menu.getSlot(GunsmithMenu.RESULT_SLOT), GunsmithMenu.RESULT_SLOT, button,
                    Screen.hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (overPreview(mouseX, mouseY) && !menu.result().isEmpty() && menu.getCarried().isEmpty()) {
            List<Component> tip = new ArrayList<>(getTooltipFromContainerItem(menu.result()));
            tip.add(Component.literal("Click to take the modded gun").withStyle(ChatFormatting.YELLOW));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        if (menu.gun().isEmpty()) {
            if (isHovering(GunsmithMenu.GUN_X, GunsmithMenu.GUN_Y, 16, 16, mouseX, mouseY) && menu.getCarried().isEmpty())
                g.renderTooltip(font, Component.literal("Put a gun here"), mouseX, mouseY);
            return;
        }
        // empty / X'd-out part slots: say what goes there
        for (AttachmentType t : AttachmentType.values()) {
            int[] p = GunsmithMenu.ATTACHMENT_POS[t.ordinal()];
            if (!isHovering(p[0], p[1], 16, 16, mouseX, mouseY)) continue;
            if (!menu.supports(t)) g.renderTooltip(font, Component.literal(t.label() + ": not available on this gun"), mouseX, mouseY);
            else if (menu.getSlot(GunsmithMenu.ATT_START + t.ordinal()).getItem().isEmpty())
                g.renderTooltip(font, Component.literal(t.label() + " (empty)"), mouseX, mouseY);
        }
    }

    /** The result item is drawn as the big preview, not as a 16x16 slot. */
    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        if (slot.index == GunsmithMenu.RESULT_SLOT) return;
        if (slot.index == GunsmithMenu.GUN_SLOT && slot.hasItem()) { // drawn larger to fill the big slot
            g.pose().pushPose();
            g.pose().translate(slot.x + 8, slot.y + 8, 0);
            g.pose().scale(GUN_SLOT_SCALE, GUN_SLOT_SCALE, GUN_SLOT_SCALE);
            g.renderItem(slot.getItem(), -8, -8);
            g.pose().popPose();
            return;
        }
        super.renderSlot(g, slot);
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.index != GunsmithMenu.RESULT_SLOT) super.renderSlotHighlight(g, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (!menu.gun().isEmpty()) {
            for (AttachmentType t : AttachmentType.values()) { // X out part slots this gun can't take
                if (menu.supports(t)) continue;
                int[] p = GunsmithMenu.ATTACHMENT_POS[t.ordinal()];
                int x = leftPos + p[0], y = topPos + p[1];
                g.fill(x, y, x + 16, y + 16, 0x80000000);
                for (int i = 2; i < 14; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFCC3333);
                    g.fill(x + 15 - i, y + i, x + 16 - i, y + i + 1, 0xFFCC3333);
                }
            }
        }
        renderResult(g, mouseX, mouseY);
    }

    private void renderResult(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack out = menu.result();
        int x0 = leftPos + PX0, y0 = topPos + PY0, x1 = leftPos + PX1, cx = (x0 + x1) / 2;
        if (!(out.getItem() instanceof GunItem gun)) {
            g.drawCenteredString(font, "Put a gun", cx, y0 + 28, 0xFF6F7680);
            g.drawCenteredString(font, "on the bench", cx, y0 + 39, 0xFF6F7680);
            return;
        }
        if (overPreview(mouseX, mouseY) && menu.getCarried().isEmpty())
            g.fill(x0 + 1, y0 + 1, x1 - 1, topPos + PREVIEW_Y1, 0x30FFFFFF);
        // the modded gun, big and in 3D with its parts, clipped to the panel. The inventory shows a flat sprite
        // (GUI context), so this draws the item-frame (FIXED) view turned round: the same side view, muzzle right.
        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, topPos + PREVIEW_Y1);
        g.pose().pushPose();
        g.pose().translate(cx - 3, y0 + 22, 150);
        g.pose().scale(PREVIEW_SCALE / FIXED_TO_GUI, PREVIEW_SCALE / FIXED_TO_GUI, PREVIEW_SCALE / FIXED_TO_GUI);
        g.pose().scale(16, -16, 16);
        g.pose().mulPose(Axis.YP.rotationDegrees(180));
        Lighting.setupForFlatItems();
        var buffers = g.bufferSource();
        minecraft.getItemRenderer().renderStatic(out, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, g.pose(), buffers, minecraft.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
        g.disableScissor();
        // what the parts do, compared with the bare gun
        List<Line> lines = statLines(gun, out);
        float s = 0.62f;
        g.pose().pushPose();
        g.pose().translate(x0 + 4, topPos + PREVIEW_Y1 + 2, 200);
        g.pose().scale(s, s, 1);
        for (int i = 0; i < Math.min(lines.size(), 7); i++) g.drawString(font, lines.get(i).text, 0, i * 9, lines.get(i).color, false);
        g.pose().popPose();
    }

    private record Line(String text, int color) {}

    static List<Line> statLines(GunItem gun, ItemStack stack) {
        GunStats with = GunStats.of(gun, stack), bare = GunStats.of(gun.spec(), GunAttachments.EMPTY);
        List<Line> lines = new ArrayList<>();
        pct(lines, "Recoil", with.recoil(), true);
        pct(lines, "Hip spread", with.hipSpread(), true);
        pct(lines, "Aim speed", with.adsSpeed(), false);
        pct(lines, "Reload time", with.reload(), true);
        pct(lines, "Velocity", with.velocity(), false);
        int mag = with.magazineSize(), bareMag = bare.magazineSize();
        lines.add(new Line("Magazine: " + mag, mag == bareMag ? PLAIN : mag > bareMag ? GOOD : BAD));
        lines.add(new Line(with.optic() != null ? opticLabel(with) : "Iron sights", PLAIN));
        if (with.suppressed()) lines.add(new Line("Suppressed", GOOD));
        if (with.bipod()) lines.add(new Line("Bipod", GOOD));
        return lines;
    }

    /** A multiplier as "+12%" / "-23%", green when it helps. Unchanged stats are left out. */
    private static void pct(List<Line> lines, String name, float mult, boolean lowerIsBetter) {
        int p = Math.round((mult - 1f) * 100f);
        if (p == 0) return;
        boolean good = lowerIsBetter ? p < 0 : p > 0;
        lines.add(new Line(name + ": " + (p > 0 ? "+" : "") + p + "%", good ? GOOD : BAD));
    }

    /** "Scope 6.5-20x", "ACOG 4x", "Red dot": short enough for the panel (the item tooltip has the full name). */
    private static String opticLabel(GunStats st) {
        var o = st.optic().optic();
        String mag = o.magMin() == o.magMax() ? fmt(o.magMin()) + "x" : fmt(o.magMin()) + "-" + fmt(o.magMax()) + "x";
        return switch (o.reticle()) {
            case RED_DOT -> "Red dot";
            case HOLOGRAPHIC -> "Holo sight";
            case CHEVRON -> "Prism " + mag;
            case MIL_DOT -> "Scope " + mag;
        };
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        for (AttachmentType t : AttachmentType.values()) { // small captions over the part slots
            int[] p = GunsmithMenu.ATTACHMENT_POS[t.ordinal()];
            String s = switch (t) {
                case OPTIC -> "OPTIC"; case MUZZLE -> "MUZZLE"; case UNDERBARREL -> "UNDER";
                case SIDE -> "SIDE"; case STOCK -> "STOCK"; case MAGAZINE -> "MAG";
            };
            g.pose().pushPose();
            g.pose().translate(p[0] + 8, p[1] - 6, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawCenteredString(font, s, 0, 0, 0xFFB8C8B8);
            g.pose().popPose();
        }
    }
}
