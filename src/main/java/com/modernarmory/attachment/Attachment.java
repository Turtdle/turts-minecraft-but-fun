package com.modernarmory.attachment;

import com.modernarmory.ballistics.CaliberClass;

import net.minecraft.resources.ResourceLocation;

/**
 * One attachment and what it does. Multipliers are relative (1 = no change). The model geometry
 * lives in each gun's model as bone {@code att_<id>} (tools/modelgen/attachments.py).
 *
 * @param caliber    only fits guns whose cartridge is of this calibre class (muzzle devices, magazines); null = any
 * @param recoil     recoil multiplier
 * @param hipSpread  hip-fire spread multiplier
 * @param adsSpeed   aim-down-sights speed multiplier
 * @param velocity   muzzle velocity multiplier (suppressors add a little)
 * @param reload     reload time multiplier
 * @param magazine   magazine capacity override, 0 = unchanged
 * @param suppressed quieter report, no muzzle flash
 * @param bipod      very steady when crouched
 * @param optic      sight / magnification, null for non-optics
 */
public record Attachment(String id, AttachmentType type, String name, CaliberClass caliber, float recoil, float hipSpread,
                         float adsSpeed, float velocity, float reload, int magazine, boolean suppressed, boolean bipod,
                         Optic optic) {

    public enum Reticle { RED_DOT, HOLOGRAPHIC, CHEVRON, MIL_DOT }

    /**
     * @param magMin     magnification (reflex sights: 1)
     * @param magMax     max magnification (variable-power scopes)
     * @param overlay    scope tube texture for magnified optics; null = reflex sight seen through the glass
     * @param reticle    reticle drawn at the point of aim
     * @param eyeRelief  model units behind the rear lens the eye sits (magnified optics)
     */
    public record Optic(float magMin, float magMax, ResourceLocation overlay, Reticle reticle, float eyeRelief) {
        public boolean magnified() {
            return overlay != null;
        }
    }

    public static Builder of(String id, AttachmentType type, String name) {
        return new Builder(id, type, name);
    }

    public static final class Builder {
        private final String id, name;
        private final AttachmentType type;
        private CaliberClass caliber;
        private float recoil = 1, hipSpread = 1, adsSpeed = 1, velocity = 1, reload = 1;
        private int magazine;
        private boolean suppressed, bipod;
        private Optic optic;

        private Builder(String id, AttachmentType type, String name) {
            this.id = id;
            this.type = type;
            this.name = name;
        }

        public Builder caliber(CaliberClass c) { this.caliber = c; return this; }
        public Builder recoil(float m) { this.recoil = m; return this; }
        public Builder hipSpread(float m) { this.hipSpread = m; return this; }
        public Builder adsSpeed(float m) { this.adsSpeed = m; return this; }
        public Builder velocity(float m) { this.velocity = m; return this; }
        public Builder reload(float m) { this.reload = m; return this; }
        public Builder magazine(int rounds) { this.magazine = rounds; return this; }
        public Builder suppressed() { this.suppressed = true; return this; }
        public Builder bipod() { this.bipod = true; return this; }
        public Builder optic(Optic o) { this.optic = o; return this; }

        public Attachment build() {
            return new Attachment(id, type, name, caliber, recoil, hipSpread, adsSpeed, velocity, reload, magazine,
                    suppressed, bipod, optic);
        }
    }
}
