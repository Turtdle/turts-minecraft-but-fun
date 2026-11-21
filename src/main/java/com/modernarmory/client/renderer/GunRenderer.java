package com.modernarmory.client.renderer;

import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.client.ClientState;
import com.modernarmory.gun.GunParts;
import com.modernarmory.gun.PartVisibility;
import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * Renders a gun: first-person arms come from {@link ArmedItemRenderer}; fitted attachments are shown,
 * and the {@code muzzle_flash} locator gets a brief flash in first person after each shot.
 */
public class GunRenderer extends ArmedItemRenderer<GunItem> {
    public GunRenderer(GunItem gun) {
        super(new DefaultedItemGeoModel<>(ModernArmory.id(gun.modelId())));
    }

    /** Which locator the muzzle flash comes from this frame; null = suppressed (no flash). */
    private String flashBone = "muzzle_flash";
    private boolean laserOn;

    /** In GUIs the gun is its flat icon, painted for the attachments fitted (GunIcons; GunGuiModel sends GUIs here). */
    @Override
    public void renderByItem(net.minecraft.world.item.ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI && stack.getItem() instanceof GunItem gun) {
            com.modernarmory.client.GunIcons.drawGui(gun, stack, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        super.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
    }

    /**
     * Show only the fitted attachments: every attachment is a bone {@code att_<id>} in the gun's model
     * (tools/modelgen/attachments.py). Default parts they replace are hidden, the flip-up rear
     * sights (PartVisibility.IRON_SIGHTS) fold away under an optic, and parts with a {@link PartVisibility} rule in the gun's
     * spec (folding front BUIS, the M2's tripod) follow it.
     */
    @Override
    public void preRender(PoseStack poseStack, GunItem animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender && currentItemStack != null) {
            GunStats stats = GunStats.of(animatable, currentItemStack);
            GunAttachments atts = stats.attachments();
            Set<String> fitted = GunParts.fitted(animatable, atts);
            Attachment muzzle = atts.get(AttachmentType.MUZZLE);
            boolean hasMuzzle = muzzle != null && fitted.contains(muzzle.id());
            for (GeoBone b : model.topLevelBones()) applyVisibility(b, fitted, atts, animatable);
            flashBone = stats.suppressed() ? null
                    : hasMuzzle && model.getBone("flash_" + muzzle.id()).isPresent() ? "flash_" + muzzle.id() : "muzzle_flash";
            laserOn = fitted.contains("laser");
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private void applyVisibility(GeoBone bone, Set<String> fitted, GunAttachments atts, GunItem gun) {
        Boolean hide = GunParts.hidden(bone.getName(), gun, atts, fitted, isFirstPerson()); // same rule as the icon (GunIcons)
        if (hide != null) bone.setHidden(hide);
        for (GeoBone c : bone.getChildBones()) applyVisibility(c, fitted, atts, gun);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, GunItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        if (!armPass && !isReRender && isFirstPerson() && bone.getName().equals(flashBone)) {
            float age = ClientState.secondsSinceShot();
            if (age < 0.045f) renderMuzzleFlash(poseStack, bufferSource, bone, age);
        }
        if (!armPass && !isReRender && laserOn && (isFirstPerson() || isThirdPerson()) && bone.getName().equals("beam_laser")) renderLaserBeam(poseStack, bufferSource, bone);
    }

    /** AN/PEQ-15 visible laser: a thin red beam fading out ahead of the emitter (the dot is a particle). */
    private void renderLaserBeam(PoseStack poseStack, MultiBufferSource buffers, GeoBone bone) {
        poseStack.pushPose();
        poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        float r = 0.12f / 16f, len = isFirstPerson() ? 3.0f : 6.0f;
        for (int i = 0; i < 2; i++) { // two crossed planes so it reads from any angle
            float x = i == 0 ? r : 0, y = i == 0 ? 0 : r;
            vc.addVertex(m, -x, -y, 0).setColor(255, 30, 30, 170);
            vc.addVertex(m, x, y, 0).setColor(255, 30, 30, 170);
            vc.addVertex(m, x, y, -len).setColor(255, 30, 30, 0);
            vc.addVertex(m, -x, -y, -len).setColor(255, 30, 30, 0);
            vc.addVertex(m, -x, -y, -len).setColor(255, 30, 30, 0);
            vc.addVertex(m, x, y, -len).setColor(255, 30, 30, 0);
            vc.addVertex(m, x, y, 0).setColor(255, 30, 30, 170);
            vc.addVertex(m, -x, -y, 0).setColor(255, 30, 30, 170);
        }
        poseStack.popPose();
    }

    /** A few crossed, additive quads (star-shaped flash) at the muzzle, oriented along -Z. */
    private void renderMuzzleFlash(PoseStack poseStack, MultiBufferSource buffers, GeoBone bone, float age) {
        float fade = 1f - age / 0.045f;
        float len = 5.0f / 16f * (0.7f + 0.3f * fade), rad = 1.6f / 16f;
        poseStack.pushPose();
        poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ClientState.shotSeed() * 360f));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 60f));
            Matrix4f m = poseStack.last().pose();
            int a = (int) (230 * fade);
            // flame plane in Y/Z: bright at the muzzle, fading toward the tip
            vc.addVertex(m, 0, -rad, 0).setColor(255, 220, 120, a);
            vc.addVertex(m, 0, rad, 0).setColor(255, 220, 120, a);
            vc.addVertex(m, 0, rad * 0.3f, -len).setColor(255, 140, 40, 0);
            vc.addVertex(m, 0, -rad * 0.3f, -len).setColor(255, 140, 40, 0);
            vc.addVertex(m, 0, -rad * 0.3f, -len).setColor(255, 140, 40, 0);
            vc.addVertex(m, 0, rad * 0.3f, -len).setColor(255, 140, 40, 0);
            vc.addVertex(m, 0, rad, 0).setColor(255, 220, 120, a);
            vc.addVertex(m, 0, -rad, 0).setColor(255, 220, 120, a);
            poseStack.popPose();
        }
        // front-facing disc so the flash also reads from directly behind
        Matrix4f m = poseStack.last().pose();
        float r = rad * 1.6f;
        int a = (int) (200 * fade);
        vc.addVertex(m, -r, 0, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, 0, r, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, r, 0, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, 0, -r, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, 0, -r, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, r, 0, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, 0, r, 0).setColor(255, 230, 150, a);
        vc.addVertex(m, -r, 0, 0).setColor(255, 230, 150, a);
        poseStack.popPose();
    }
}
