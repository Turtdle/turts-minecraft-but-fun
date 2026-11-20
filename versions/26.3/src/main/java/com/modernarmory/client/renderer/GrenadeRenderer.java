package com.modernarmory.client.renderer;

import com.geckolib.constant.DataTickets;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeItem;

import net.minecraft.world.item.ItemDisplayContext;

/**
 * A grenade in the hand (with first-person arms, ArmedItemRenderer), or in flight: the thrown grenade is
 * drawn by GrenadeEntityRenderer with display context NONE, which hides the pin and the spoon (the spoon
 * flies off when it is thrown). GeckoLib 4 did this with GeoBone#setHidden in preRender; GeckoLib 5 uses
 * bone snapshot flags, which only last for this render.
 */
public class GrenadeRenderer extends ArmedItemRenderer<GrenadeItem> {
    public GrenadeRenderer(GrenadeItem item) {
        super(new DefaultedItemGeoModel<>(ModernArmory.id(item.type().id())));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        boolean thrown = renderPassInfo.renderState().getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE) == ItemDisplayContext.NONE;
        if (!thrown) return;
        snapshots.ifPresent("pin", b -> b.skipRender(true).skipChildrenRender(true));
        snapshots.ifPresent("spoon", b -> b.skipRender(true).skipChildrenRender(true));
        snapshots.ifPresent("grenade", b -> b.skipRender(false).skipChildrenRender(false));
    }
}
