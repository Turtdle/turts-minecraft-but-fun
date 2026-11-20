package com.modernarmory.client.renderer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A GeckoLib item held with first-person arms (guns, grenades). Bones named {@code right_arm} /
 * {@code left_arm} (and their children) are skipped in the main pass ({@link #adjustModelBonesForRender}, the
 * GeckoLib 5 equivalent of overriding renderCubesOfBone) and drawn by {@link ArmSkinLayer} as per-bone render
 * tasks with the local player's skin, so the hands animate together with the item. Subclasses that override
 * {@link #adjustModelBonesForRender} call super first.
 */
public abstract class ArmedItemRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    protected ArmedItemRenderer(GeoModel<T> model) {
        super(model);
        withRenderLayer(new ArmSkinLayer<>(this));
    }

    static boolean isFirstPerson(GeoRenderState renderState) {
        ItemDisplayContext context = renderState.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    static boolean isThirdPerson(GeoRenderState renderState) {
        ItemDisplayContext context = renderState.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        return context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    static boolean isArmBone(GeoBone bone) {
        for (GeoBone b = bone; b != null; b = b.parent()) {
            String n = b.name();
            if (n.equals("right_arm") || n.equals("left_arm")) return true;
        }
        return false;
    }

    /** Main pass: the item only; the arm cubes are drawn by {@link ArmSkinLayer}. */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
        for (GeoBone bone : renderPassInfo.model().boneLookup().get().values()) {
            if (isArmBone(bone)) snapshots.get(bone).skipRender(true);
        }
    }
}
