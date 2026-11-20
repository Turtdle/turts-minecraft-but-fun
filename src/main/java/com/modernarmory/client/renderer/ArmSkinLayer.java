package com.modernarmory.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Second render pass: the first-person arm bones, textured with the local player's skin. */
public class ArmSkinLayer<T extends Item & GeoItem> extends GeoRenderLayer<T> {
    private final ArmedItemRenderer<T> gunRenderer;

    public ArmSkinLayer(ArmedItemRenderer<T> renderer) {
        super(renderer);
        this.gunRenderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!gunRenderer.isFirstPerson()) return;
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ResourceLocation skin = player.getSkin().texture();
        RenderType skinType = RenderType.entityTranslucent(skin);
        gunRenderer.armPass = true;
        try {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, skinType,
                    bufferSource.getBuffer(skinType), partialTick, packedLight, packedOverlay, 0xFFFFFFFF);
        } finally {
            gunRenderer.armPass = false;
        }
    }
}
