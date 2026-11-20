package com.modernarmory.attachment;

import com.modernarmory.item.GunItem;
import com.modernarmory.gun.GunSpec;

import net.minecraft.world.item.ItemStack;

/** A gun's effective stats with its attachments applied. */
public record GunStats(GunSpec spec, GunAttachments attachments, float recoil, float hipSpread, float adsSpeed,
                       float velocity, float reload, int magazineSize, boolean suppressed, boolean bipod,
                       Attachment optic) {

    public static GunStats of(GunItem gun, ItemStack stack) {
        return of(gun.spec(), gun.attachments(stack));
    }

    /** Stats of a gun with these attachments fitted (e.g. its factory-fitted ones). */
    public static GunStats of(GunSpec spec, GunAttachments atts) {
        float recoil = 1, hip = 1, ads = 1, vel = 1, reload = 1;
        int mag = spec.magazineSize();
        boolean suppressed = false, bipod = false;
        Attachment optic = null;
        for (AttachmentType t : AttachmentType.values()) {
            Attachment a = atts.get(t);
            if (a == null || !spec.slots().contains(t)) continue;
            recoil *= a.recoil();
            hip *= a.hipSpread();
            ads *= a.adsSpeed();
            vel *= a.velocity();
            reload *= a.reload();
            if (a.magazine() > 0) mag = a.magazine();
            suppressed |= a.suppressed();
            bipod |= a.bipod();
            if (a.optic() != null) optic = a;
        }
        return new GunStats(spec, atts, recoil, hip, ads, vel, reload, mag, suppressed, bipod, optic);
    }

    /** Seconds to bring the sights up from the hip: the gun's own time slowed / sped up by its attachments. */
    public double adsSeconds() {
        return spec.adsSeconds() / adsSpeed;
    }

    public int reloadTicks(boolean empty) {
        return Math.round((empty ? spec.emptyReloadTicks() : spec.reloadTicks()) * reload);
    }
}
