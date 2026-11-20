package com.modernarmory.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * A GeckoLib item held with first-person arms (guns, grenades). Bones named {@code right_arm} /
 * {@code left_arm} (and their children) are skipped in the main pass and drawn by {@link ArmSkinLayer}
 * in a second pass with the local player's skin, so the hands animate together with the item.
 */
public abstract class ArmedItemRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    boolean armPass;

    protected ArmedItemRenderer(GeoModel<T> model) {
        super(model);
        addRenderLayer(new ArmSkinLayer<>(this));
    }

    public boolean isFirstPerson() {
        return renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    public boolean isThirdPerson() {
        return renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    static boolean isArmBone(GeoBone bone) {
        for (GeoBone b = bone; b != null; b = b.getParent()) {
            String n = b.getName();
            if (n.equals("right_arm") || n.equals("left_arm")) return true;
        }
        return false;
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (isArmBone(bone) != armPass) return; // main pass: the item only; arm pass: arms only
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }
}
