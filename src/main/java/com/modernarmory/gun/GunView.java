package com.modernarmory.gun;

import com.modernarmory.attachment.Attachment;

import net.minecraft.resources.ResourceLocation;

/**
 * How a gun is aimed (model units of the gun's model, see tools/modelgen/guns/*.py).
 *
 * @param sightY       height of the sight line: iron-sight post tip / aperture centre, or the
 *                     scope's optical axis. When aiming, the eye is put on this line, so the point of
 *                     aim is the screen centre, which is where bullets go.
 * @param eyeZ         model z where the eye sits when aiming (behind the rear sight / eyepiece)
 * @param scopeMin     magnification; 1 = iron sights (slight 1.25x zoom when aiming)
 * @param scopeMax     max magnification (scroll wheel adjusts between min and max)
 * @param scopeOverlay full-screen scope texture shown when fully aimed; null = iron sights
 * @param reticle      reticle drawn for optics (null = iron sights)
 */
public record GunView(float sightY, float eyeZ, float scopeMin, float scopeMax, ResourceLocation scopeOverlay,
                      Attachment.Reticle reticle) {
    public static GunView ironSights(float sightY, float eyeZ) {
        return new GunView(sightY, eyeZ, 1f, 1f, null, null);
    }

    /** A gun whose own sight is a fixed scope (when the scope is removed it just shoulders on this line). */
    public static GunView scope(float sightY, float eyeZ, float magMin, float magMax, ResourceLocation overlay,
                                Attachment.Reticle reticle) {
        return new GunView(sightY, eyeZ, magMin, magMax, overlay, reticle);
    }

    public boolean scoped() {
        return scopeOverlay != null;
    }
}
