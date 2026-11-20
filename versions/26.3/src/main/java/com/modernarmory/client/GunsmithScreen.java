package com.modernarmory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.modernarmory.menu.GunsmithMenu;
import com.modernarmory.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Gunsmith Table screen: the gun slot in the middle of a felt bench with the part slots where the
 * parts mount, an arrow, and on the right a big preview of the modded gun with what its parts do.
 * Clicking anywhere on the preview takes the modded gun.
 * <p>
 * 26.x GUI: screens extract render state instead of drawing (render -> extractRenderState, renderBg ->
 * extractBackground, renderLabels -> extractLabels, renderSlot -> extractSlot), the image size is final
 * and passed to the constructor, tooltips are queued with setTooltipForNextFrame, and the 2D pose is a
 * Matrix3x2fStack. The result slot opts out of the slot highlight itself (Slot.isHighlightable).
 */
public class GunsmithScreen extends AbstractContainerScreen<GunsmithMenu> {
    private static final Identifier BG = ModernArmory.id("textures/gui/gunsmith.png");
    /** The result panel (GUI px, relative to the window): preview on top, stats below. */
    public static final int PX0 = 172, PY0 = 15, PX1 = 246, PY1 = 101, PREVIEW_Y1 = 58;
    private static final int GOOD = 0xFF7EE07E, BAD = 0xFFE6766A, PLAIN = 0xFFC8CDD4;
    /** Preview size (x the 16 px item), and the gun in the big middle slot. */
    private static final float PREVIEW_SCALE = 3.5f, GUN_SLOT_SCALE = 1.5f;

    public GunsmithScreen(GunsmithMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, GunsmithMenu.WIDTH, GunsmithMenu.HEIGHT);
        inventoryLabelX = GunsmithMenu.INV_X;
        inventoryLabelY = GunsmithMenu.INV_Y - 11;
    }

    private boolean overPreview(double mx, double my) {
        return mx >= leftPos + PX0 && mx < leftPos + PX1 && my >= topPos + PY0 && my < topPos + PREVIEW_Y1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 26.x numbers mouse buttons like AbstractContainerScreen.getContainerClickButton: 1 = left, 3 = right
        int button = switch (event.button()) { case 1 -> 0; case 3 -> 1; default -> event.button(); };
        if (overPreview(event.x(), event.y()) && !menu.result().isEmpty() && (button == 0 || button == 1)) {
            slotClicked(menu.getSlot(GunsmithMenu.RESULT_SLOT), GunsmithMenu.RESULT_SLOT, button,
                    event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (overPreview(mouseX, mouseY) && !menu.result().isEmpty() && menu.getCarried().isEmpty()) {
            ItemStack out = menu.result();
            List<Component> tip = new ArrayList<>(getTooltipFromContainerItem(out));
            tip.add(Component.literal("Click to take the modded gun").withStyle(ChatFormatting.YELLOW));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), out, mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
        if (menu.gun().isEmpty()) {
            if (isHovering(GunsmithMenu.GUN_X, GunsmithMenu.GUN_Y, 16, 16, mouseX, mouseY) && menu.getCarried().isEmpty())
                g.setTooltipForNextFrame(Component.literal("Put a gun here"), mouseX, mouseY);
            return;
        }
        // empty / X'd-out part slots: say what goes there
        for (AttachmentType t : AttachmentType.values()) {
            int[] p = GunsmithMenu.ATTACHMENT_POS[t.ordinal()];
            if (!isHovering(p[0], p[1], 16, 16, mouseX, mouseY)) continue;
            if (!menu.supports(t)) g.setTooltipForNextFrame(Component.literal(t.label() + ": not available on this gun"), mouseX, mouseY);
            else if (menu.getSlot(GunsmithMenu.ATT_START + t.ordinal()).getItem().isEmpty())
                g.setTooltipForNextFrame(Component.literal(t.label() + " (empty)"), mouseX, mouseY);
        }
    }

    /** The result item is drawn as the big preview, the gun in the middle a size up. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index == GunsmithMenu.RESULT_SLOT) return;
        if (slot.index == GunsmithMenu.GUN_SLOT && slot.hasItem()) {
            g.pose().pushMatrix();
            g.pose().translate(slot.x + 8, slot.y + 8);
            g.pose().scale(GUN_SLOT_SCALE, GUN_SLOT_SCALE);
            g.item(slot.getItem(), -8, -8);
            g.pose().popMatrix();
            return;
        }
        super.extractSlot(g, slot, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
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
        extractResult(g, mouseX, mouseY);
    }

    private void extractResult(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ItemStack out = menu.result();
        int x0 = leftPos + PX0, y0 = topPos + PY0, x1 = leftPos + PX1, cx = (x0 + x1) / 2;
        if (!(out.getItem() instanceof GunItem gun)) {
            g.centeredText(font, "Put a gun", cx, y0 + 28, 0xFF6F7680);
            g.centeredText(font, "on the bench", cx, y0 + 39, 0xFF6F7680);
            return;
        }
        if (overPreview(mouseX, mouseY) && menu.getCarried().isEmpty())
            g.fill(x0 + 1, y0 + 1, x1 - 1, topPos + PREVIEW_Y1, 0x30FFFFFF);
        // the modded gun, big and in 3D with its parts (its GUI transform is a side view), clipped to the panel.
        // RENDER_3D makes the item definition pick the 3D model over the inventory sprite.
        ItemStack shown = out.copy();
        shown.set(ModDataComponents.RENDER_3D.get(), Unit.INSTANCE);
        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, topPos + PREVIEW_Y1);
        g.pose().pushMatrix();
        g.pose().translate(cx - 3, y0 + 22);
        g.pose().scale(PREVIEW_SCALE, PREVIEW_SCALE);
        g.fakeItem(shown, -8, -8);
        g.pose().popMatrix();
        g.disableScissor();
        // what the parts do, compared with the bare gun
        List<Line> lines = statLines(gun, out);
        float s = 0.62f;
        g.pose().pushMatrix();
        g.pose().translate(x0 + 4, topPos + PREVIEW_Y1 + 2);
        g.pose().scale(s, s);
        for (int i = 0; i < Math.min(lines.size(), 7); i++) g.text(font, lines.get(i).text, 0, i * 9, lines.get(i).color, false);
        g.pose().popMatrix();
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
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
        for (AttachmentType t : AttachmentType.values()) { // small captions over the part slots
            int[] p = GunsmithMenu.ATTACHMENT_POS[t.ordinal()];
            String s = switch (t) {
                case OPTIC -> "OPTIC"; case MUZZLE -> "MUZZLE"; case UNDERBARREL -> "UNDER";
                case SIDE -> "SIDE"; case STOCK -> "STOCK"; case MAGAZINE -> "MAG";
            };
            g.pose().pushMatrix();
            g.pose().translate(p[0] + 8, p[1] - 6);
            g.pose().scale(0.5f, 0.5f);
            g.centeredText(font, s, 0, 0, 0xFFB8C8B8);
            g.pose().popMatrix();
        }
    }
}
