package com.modernarmory.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.item.GunItem;
import com.modernarmory.gun.GunView;
import com.modernarmory.gun.ScopePower;

import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The sight picture a gun has right now, from its fitted optic. The optic's sight line is read
 * from the gun's model (locator {@code sight_<optic id>}, tools/modelgen/attachments.py), so every
 * optic lines up on every gun without hand-tuned numbers.
 * <ul>
 *   <li>no optic: the gun's own sights (iron sights; a scope-only gun like the M2010 just shoulders)</li>
 *   <li>reflex sight (red dot / holo): eye on the window's axis, cheek on the stock as usual</li>
 *   <li>magnified optic: eye on the axis, eye-relief behind the eyepiece; scope overlay + zoom (the
 *       factory-fitted scope uses the gun's issued power range, GunSpec.issuedScopePower)</li>
 * </ul>
 */
public final class GunViews {
    private static final Map<String, Optional<float[]>> LOCATORS = new HashMap<>();
    private static Object cacheOwner;

    public static GunView resolve(GunItem gun, ItemStack stack) {
        GunView base = gun.spec().sights();
        Attachment optic = GunStats.of(gun, stack).optic();
        if (optic == null) {
            // a removable scope taken off: shoulder on the same line; a built-in one (no optic slot, e.g. the AUG) stays
            return base.scoped() && gun.spec().slots().contains(AttachmentType.OPTIC) ? GunView.ironSights(base.sightY(), base.eyeZ()) : base;
        }
        Attachment.Optic o = optic.optic();
        float[] p = locator(gun.modelId(), "sight_" + optic.id());
        float sightY = p != null ? p[0] : base.sightY();
        if (!o.magnified()) return new GunView(sightY, base.eyeZ(), 1f, 1f, null, o.reticle());
        float eyeZ = p != null ? p[1] + o.eyeRelief() : base.eyeZ();
        // the factory-fitted scope stands in for the gun's real issued scope: use that one's power range
        ScopePower issued = gun.spec().issuedScopePower();
        boolean factoryOptic = optic.id().equals(gun.spec().defaultAttachments().get(AttachmentType.OPTIC));
        float min = issued != null && factoryOptic ? issued.min() : o.magMin();
        float max = issued != null && factoryOptic ? issued.max() : o.magMax();
        return new GunView(sightY, eyeZ, min, max, o.overlay(), o.reticle());
    }

    /** (y, z) of a locator bone in the gun's model, model units; null if the model lacks it. */
    private static float[] locator(String modelId, String bone) {
        Map<?, BakedGeoModel> models = GeckoLibCache.getBakedModels();
        if (cacheOwner != models) { // resource reload replaced the cache
            LOCATORS.clear();
            cacheOwner = models;
        }
        return LOCATORS.computeIfAbsent(modelId + "#" + bone, k -> {
            BakedGeoModel m = GeckoLibCache.getBakedModels().get(ModernArmory.id("geo/item/" + modelId + ".geo.json"));
            if (m == null) return Optional.empty();
            Optional<GeoBone> b = m.getBone(bone);
            return b.map(g -> new float[] {g.getPivotY(), g.getPivotZ()});
        }).orElse(null);
    }

    private GunViews() {}
}
