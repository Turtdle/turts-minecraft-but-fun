package com.modernarmory.client.renderer;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * A grenade in the hand (with first-person arms, ArmedItemRenderer), or in flight: the thrown grenade is
 * drawn by GrenadeEntityRenderer with display context NONE, which hides the pin and the spoon (the spoon
 * flies off when it is thrown).
 */
public class GrenadeRenderer extends ArmedItemRenderer<GrenadeItem> {
    public GrenadeRenderer(GrenadeItem item) {
        super(new DefaultedItemGeoModel<>(ModernArmory.id(item.type().id())));
    }

    @Override
    public void preRender(PoseStack poseStack, GrenadeItem animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            boolean thrown = renderPerspective == ItemDisplayContext.NONE;
            model.getBone("pin").ifPresent(b -> b.setHidden(thrown));
            model.getBone("spoon").ifPresent(b -> b.setHidden(thrown));
            model.getBone("grenade").ifPresent(b -> { if (thrown) b.setHidden(false); });
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
