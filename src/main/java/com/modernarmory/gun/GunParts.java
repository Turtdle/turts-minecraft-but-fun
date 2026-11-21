package com.modernarmory.gun;

import java.util.HashSet;
import java.util.Set;

import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.item.GunItem;

/**
 * Which parts (bones) of a gun's model show for the attachments it carries: the one rule the 3D model
 * (client.renderer.GunRenderer) and the inventory icon (client.GunIcons) both follow, so they always agree.
 * Every attachment is a bone {@code att_<id>} in the gun's model (tools/modelgen/attachments.py); default parts
 * an attachment replaces are hidden, the iron sights ({@link PartVisibility#IRON_SIGHTS}) fold away under an
 * optic, and parts with a {@link PartVisibility} rule in the gun's spec follow it.
 */
public final class GunParts {
    /** Ids of the attachments fitted in slots this gun has. */
    public static Set<String> fitted(GunItem gun, GunAttachments atts) {
        Set<String> fitted = new HashSet<>();
        for (AttachmentType t : AttachmentType.values()) {
            Attachment a = atts.get(t);
            if (a != null && gun.spec().slots().contains(t)) fitted.add(a.id());
        }
        return fitted;
    }

    /**
     * Whether bone {@code name} is hidden ({@code true}), shown ({@code false}) or left as modelled
     * ({@code null}). A hidden bone hides its children too.
     */
    public static Boolean hidden(String name, GunItem gun, GunAttachments atts, Set<String> fitted, boolean firstPerson) {
        java.util.EnumSet<AttachmentType> filled = java.util.EnumSet.noneOf(AttachmentType.class);
        for (AttachmentType t : AttachmentType.values()) if (has(atts, gun, t)) filled.add(t);
        return hidden(name, gun.spec().parts(), fitted, filled, firstPerson);
    }

    /**
     * The same rule from plain data: the gun's part rules, the ids of the attachments fitted and the slots they
     * fill (what a render state carries).
     */
    public static Boolean hidden(String name, java.util.Map<String, PartVisibility> parts, Set<String> fitted,
                                 Set<AttachmentType> filled, boolean firstPerson) {
        PartVisibility rule = parts.get(name);
        if (rule != null) return rule.hidden(filled.contains(AttachmentType.OPTIC), firstPerson);
        if (name.startsWith("att_")) return !fitted.contains(name.substring(4));
        if (name.equals("muzzle_default")) return filled.contains(AttachmentType.MUZZLE);
        if (name.equals("stock_default")) return filled.contains(AttachmentType.STOCK);
        if (name.equals("magazine_default")) return filled.contains(AttachmentType.MAGAZINE);
        if (PartVisibility.IRON_SIGHTS.contains(name)) return filled.contains(AttachmentType.OPTIC);
        return null;
    }

    public static boolean has(GunAttachments atts, GunItem gun, AttachmentType t) {
        return atts.get(t) != null && gun.spec().slots().contains(t);
    }

    private GunParts() {}
}
