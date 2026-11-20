package com.modernarmory.attachment;

import java.util.LinkedHashMap;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment.Optic;
import com.modernarmory.attachment.Attachment.Reticle;
import com.modernarmory.ballistics.CaliberClass;

/**
 * Every attachment in the mod, with real-world-inspired effects. Calibre-specific ones fit by
 * {@link CaliberClass} (bore / pressure rating, as real muzzle devices are rated), and an
 * attachment only fits a gun whose model has its bone {@code att_<id>} (GunModels).
 */
public final class Attachments {
    public static final Map<String, Attachment> ALL = new LinkedHashMap<>();

    // ---- optics -----------------------------------------------------------------
    /** Aimpoint CompM4 / M68 CCO: 2 MOA red dot, 1x. */
    public static final Attachment RED_DOT = add(Attachment.of("red_dot", AttachmentType.OPTIC, "Red Dot Sight (M68 CCO)")
            .optic(new Optic(1, 1, null, Reticle.RED_DOT, 0)));
    /** EOTech EXPS3: 68 MOA ring + 1 MOA dot, 1x. */
    public static final Attachment HOLOGRAPHIC = add(Attachment.of("holographic", AttachmentType.OPTIC, "Holographic Sight (EOTech)")
            .adsSpeed(0.97f).optic(new Optic(1, 1, null, Reticle.HOLOGRAPHIC, 0)));
    /** Trijicon TA31 ACOG 4x32: chevron reticle, short eye relief. */
    public static final Attachment ACOG = add(Attachment.of("acog", AttachmentType.OPTIC, "ACOG 4x (TA31)")
            .adsSpeed(0.9f).optic(new Optic(4, 4, ModernArmory.id("textures/misc/scope_m2010.png"), Reticle.CHEVRON, 1.4f)));
    /** Leupold Mark 4 6.5-20x50 ER/T M5A2 (the M2010's scope): front focal plane mil-dot. */
    public static final Attachment SNIPER_SCOPE = add(Attachment.of("sniper_scope", AttachmentType.OPTIC, "Leupold Mark 4 6.5-20x")
            .adsSpeed(0.75f).optic(new Optic(6.5f, 20, ModernArmory.id("textures/misc/scope_m2010.png"), Reticle.MIL_DOT, 3f)));
    /** Vortex XM157 / M157 Fire Control: the NGSW's 1-8x LPVO (first focal plane). */
    public static final Attachment LPVO_M157 = add(Attachment.of("lpvo_m157", AttachmentType.OPTIC, "M157 Fire Control (1-8x LPVO)")
            .adsSpeed(0.85f).optic(new Optic(1, 8, ModernArmory.id("textures/misc/scope_m2010.png"), Reticle.MIL_DOT, 3f)));
    /** Aimpoint Micro T-2: tiny 2 MOA red dot on a low mount; the lightest optic, quick to aim. */
    public static final Attachment MICRO_RED_DOT = add(Attachment.of("micro_red_dot", AttachmentType.OPTIC, "Micro Red Dot (T-2)")
            .adsSpeed(1.05f).optic(new Optic(1, 1, null, Reticle.RED_DOT, 0)));

    // ---- muzzle ----------------------------------------------------------------
    public static final Attachment SUPPRESSOR_556 = add(Attachment.of("suppressor_556", AttachmentType.MUZZLE, "5.56 Suppressor")
            .caliber(CaliberClass.RIFLE_556).suppressed().recoil(0.9f).velocity(1.01f).adsSpeed(0.95f));
    public static final Attachment COMPENSATOR_556 = add(Attachment.of("compensator_556", AttachmentType.MUZZLE, "5.56 Compensator")
            .caliber(CaliberClass.RIFLE_556).recoil(0.75f));
    /** AAC Titan-QD (the M2010's suppressor). */
    public static final Attachment SUPPRESSOR_300 = add(Attachment.of("suppressor_300", AttachmentType.MUZZLE, ".300 Suppressor (Titan-QD)")
            .caliber(CaliberClass.RIFLE_30).suppressed().recoil(0.85f).velocity(1.01f).adsSpeed(0.95f));
    /**
     * 9 mm suppressor (pistol / SMG can, ~5 in): M882 is subsonic, so what is left is mostly the
     * action; it lengthens the gun noticeably.
     */
    public static final Attachment SUPPRESSOR_9MM = add(Attachment.of("suppressor_9mm", AttachmentType.MUZZLE, "9mm Suppressor")
            .caliber(CaliberClass.PISTOL_9MM).suppressed().recoil(0.9f).velocity(1.01f).adsSpeed(0.95f));
    /**
     * .30 cal suppressor (SIG SLX / KAC QDC class): rated for 7.62x51, 6.8x51 and .300, so one can
     * fits the M7, XM8, M250, Mk 17, Mk 20, M110 family and M240.
     */
    public static final Attachment SUPPRESSOR_762 = add(Attachment.of("suppressor_762", AttachmentType.MUZZLE, "7.62 Suppressor (.30 cal)")
            .caliber(CaliberClass.RIFLE_30).suppressed().recoil(0.85f).velocity(1.01f).adsSpeed(0.94f));
    /** Short .30 cal can for the NGSW guns (6.8x51): less length and weight, a little less quiet. */
    public static final Attachment SUPPRESSOR_68_SHORT = add(Attachment.of("suppressor_68_short", AttachmentType.MUZZLE, "6.8 Short Suppressor")
            .caliber(CaliberClass.RIFLE_30).suppressed().recoil(0.9f).velocity(1.0f).adsSpeed(0.97f));
    /** Short .30 cal can for the 7.62x51 rifles (M110 family, Mk 17, Mk 20). */
    public static final Attachment SUPPRESSOR_762_SHORT = add(Attachment.of("suppressor_762_short", AttachmentType.MUZZLE, "7.62 Short Suppressor")
            .caliber(CaliberClass.RIFLE_30).suppressed().recoil(0.9f).velocity(1.0f).adsSpeed(0.97f));
    /** .30 cal three-prong compensator / flash hider: cuts muzzle rise. */
    public static final Attachment COMPENSATOR_762 = add(Attachment.of("compensator_762", AttachmentType.MUZZLE, "7.62 Compensator")
            .caliber(CaliberClass.RIFLE_30).recoil(0.78f));
    /** .338 magnum suppressor (AML 338 / Titan-QD .338 class): big, heavy, takes the magnum's blast. */
    public static final Attachment SUPPRESSOR_338 = add(Attachment.of("suppressor_338", AttachmentType.MUZZLE, ".338 Suppressor")
            .caliber(CaliberClass.MAGNUM_338).suppressed().recoil(0.8f).velocity(1.01f).adsSpeed(0.9f));
    /**
     * Barrett two-chamber "arrowhead" .50 BMG muzzle brake (M107): vents most of the gas sideways,
     * roughly halving felt recoil.
     */
    public static final Attachment MUZZLE_BRAKE_50 = add(Attachment.of("muzzle_brake_50", AttachmentType.MUZZLE, ".50 BMG Muzzle Brake")
            .caliber(CaliberClass.BMG_50).recoil(0.5f));

    // ---- underbarrel -----------------------------------------------------------
    public static final Attachment VERTICAL_GRIP = add(Attachment.of("vertical_grip", AttachmentType.UNDERBARREL, "Vertical Grip")
            .recoil(0.85f).hipSpread(0.9f));
    public static final Attachment ANGLED_GRIP = add(Attachment.of("angled_grip", AttachmentType.UNDERBARREL, "Angled Grip")
            .recoil(0.92f).adsSpeed(1.2f));
    /** Harris bipod: rock steady when crouched (deployed), a little clumsy otherwise. */
    public static final Attachment BIPOD = add(Attachment.of("bipod", AttachmentType.UNDERBARREL, "Bipod")
            .bipod().adsSpeed(0.92f));

    // ---- side rail ---------------------------------------------------------------
    /** AN/PEQ-15: visible laser, much better hip fire. */
    public static final Attachment LASER = add(Attachment.of("laser", AttachmentType.SIDE, "Laser (AN/PEQ-15)")
            .hipSpread(0.65f));

    // ---- stock -----------------------------------------------------------------
    public static final Attachment STOCK_CTR = add(Attachment.of("stock_ctr", AttachmentType.STOCK, "Magpul CTR Stock")
            .recoil(0.9f));

    // ---- magazine --------------------------------------------------------------
    public static final Attachment PMAG_40 = add(Attachment.of("pmag_40", AttachmentType.MAGAZINE, "40-Round PMAG")
            .caliber(CaliberClass.RIFLE_556).magazine(40).reload(1.12f).adsSpeed(0.95f));

    private static Attachment add(Attachment.Builder b) {
        Attachment a = b.build();
        ALL.put(a.id(), a);
        return a;
    }

    public static Attachment byId(String id) {
        return id == null ? null : ALL.get(id);
    }

    private Attachments() {}
}
