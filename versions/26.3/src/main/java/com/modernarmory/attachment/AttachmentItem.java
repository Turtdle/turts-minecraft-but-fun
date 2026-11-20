package com.modernarmory.attachment;

import java.util.function.Consumer;

import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.GunModels;
import com.modernarmory.gun.GunSpec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class AttachmentItem extends Item {
    private final Attachment attachment;

    public AttachmentItem(Attachment attachment, Properties properties) {
        super(properties);
        this.attachment = attachment;
    }

    public Attachment attachment() {
        return attachment;
    }

    /**
     * Fits this gun: the gun has the slot, (for muzzle devices / mags) its cartridge is of the right
     * calibre class, and its model has this attachment (bone {@code att_<id>}).
     */
    public boolean fits(GunDefinition gun) {
        GunSpec spec = gun.spec();
        return spec.slots().contains(attachment.type())
                && (attachment.caliber() == null || attachment.caliber() == spec.cartridge().caliberClass())
                && GunModels.hasAttachment(gun.modelId(), attachment.id());
    }

    @SuppressWarnings("deprecation") // deprecated in 26.3 but still called by ItemStack when building tooltips
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        Attachment a = attachment;
        tooltip.accept(Component.literal(a.type().label()).withStyle(ChatFormatting.GOLD));
        if (a.caliber() != null) tooltip.accept(Component.literal(a.caliber().label() + " only").withStyle(ChatFormatting.DARK_GRAY));
        if (a.optic() != null) {
            String mag = a.optic().magMin() == a.optic().magMax() ? fmt(a.optic().magMin()) + "x"
                    : fmt(a.optic().magMin()) + "-" + fmt(a.optic().magMax()) + "x";
            tooltip.accept(Component.literal(mag + " magnification").withStyle(ChatFormatting.GRAY));
        }
        stat(tooltip, "Recoil", a.recoil(), true);
        stat(tooltip, "Hip-fire spread", a.hipSpread(), true);
        stat(tooltip, "Aim speed", a.adsSpeed(), false);
        stat(tooltip, "Reload time", a.reload(), true);
        stat(tooltip, "Muzzle velocity", a.velocity(), false);
        if (a.magazine() > 0) tooltip.accept(Component.literal(a.magazine() + "-round magazine").withStyle(ChatFormatting.GREEN));
        if (a.suppressed()) tooltip.accept(Component.literal("Suppressed: quieter, no muzzle flash").withStyle(ChatFormatting.GREEN));
        if (a.bipod()) tooltip.accept(Component.literal("Crouch to deploy: far less recoil and sway").withStyle(ChatFormatting.GREEN));
    }

    private static void stat(Consumer<Component> t, String name, float mult, boolean lowerIsBetter) {
        if (Math.abs(mult - 1) < 1e-3) return;
        int pct = Math.round((mult - 1) * 100);
        boolean good = lowerIsBetter ? pct < 0 : pct > 0;
        t.accept(Component.literal((pct > 0 ? "+" : "") + pct + "% " + name)
                .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static String fmt(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(f);
    }
}
