package com.modernarmory.client.renderer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.Attachment;
import com.modernarmory.attachment.AttachmentType;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.client.ClientState;
import com.modernarmory.gun.PartVisibility;
import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Renders a gun (GeckoLib 5). First-person arms come from {@link ArmedItemRenderer} / {@link ArmSkinLayer}.
 * <ul>
 *   <li>Only the fitted attachments show: every attachment is a bone {@code att_<id>} in the gun's model
 *       (tools/modelgen/attachments.py). Default parts they replace are hidden, and the flip-up rear sight folds
 *       away under an optic, and parts with a {@link PartVisibility} rule in the gun's spec (folding front BUIS,
 *       the M2's tripod) follow it. GeckoLib 4 did this with GeoBone#setHidden in preRender; GeckoLib 5 renders from a
 *       render state, so the stack's attachments are captured in {@link #addRenderData} and applied as bone
 *       snapshot skipRender/skipChildrenRender flags.</li>
 *   <li>Muzzle flash (first person, from {@code muzzle_flash} or the fitted muzzle device's {@code flash_<id>},
 *       none when suppressed) and the laser beam (first and third person) are per-bone tasks in
 *       {@link FlashAndLaserLayer} (GeckoLib 4: renderRecursively).</li>
 * </ul>
 */
public class GunRenderer extends ArmedItemRenderer<GunItem> {
    /** What the held stack has fitted, captured when the render state is built. */
    record Fitted(Set<String> ids, Set<AttachmentType> slots, @Nullable String muzzle, boolean suppressed, boolean laser,
                  Map<String, PartVisibility> parts) {}

    static final DataTicket<Fitted> FITTED = DataTicket.create("modernarmory_fitted", Fitted.class);

    public GunRenderer(GunItem gun) {
        super(new DefaultedItemGeoModel<>(ModernArmory.id(gun.modelId())));
        withRenderLayer(new FlashAndLaserLayer(this));
    }

    @Override
    public void addRenderData(GunItem gun, @Nullable RenderData data, GeoRenderState renderState, float partialTick) {
        if (data == null) return;
        GunStats stats = GunStats.of(gun, data.itemStack());
        GunAttachments atts = stats.attachments();
        Set<String> ids = new HashSet<>();
        Set<AttachmentType> slots = new HashSet<>();
        for (AttachmentType t : AttachmentType.values()) {
            Attachment a = atts.get(t);
            if (a != null && gun.spec().slots().contains(t)) {
                ids.add(a.id());
                slots.add(t);
            }
        }
        Attachment muzzle = atts.get(AttachmentType.MUZZLE);
        String muzzleId = muzzle != null && ids.contains(muzzle.id()) ? muzzle.id() : null;
        renderState.addGeckolibData(FITTED, new Fitted(ids, slots, muzzleId, stats.suppressed(), ids.contains("laser"),
                gun.spec().parts()));
    }

    /** Main pass: the gun with its fitted attachments only (the arms are hidden by {@link ArmedItemRenderer}). */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        Fitted fitted = renderPassInfo.getGeckolibData(FITTED);
        if (fitted == null) return;
        boolean firstPerson = isFirstPerson(renderPassInfo.renderState());
        for (GeoBone bone : renderPassInfo.model().boneLookup().get().values()) {
            if (isArmBone(bone)) continue;
            String n = bone.name();
            boolean hide = false;
            PartVisibility rule = fitted.parts().get(n);
            if (rule != null) hide = rule.hidden(fitted.slots().contains(AttachmentType.OPTIC), firstPerson);
            else if (n.startsWith("att_")) hide = !fitted.ids().contains(n.substring(4));
            else if (n.equals("muzzle_default")) hide = fitted.slots().contains(AttachmentType.MUZZLE);
            else if (n.equals("stock_default")) hide = fitted.slots().contains(AttachmentType.STOCK);
            else if (n.equals("magazine_default")) hide = fitted.slots().contains(AttachmentType.MAGAZINE);
            else if (PartVisibility.IRON_SIGHTS.contains(n)) hide = fitted.slots().contains(AttachmentType.OPTIC);
            // GeckoLib 4's setHidden also hid the bone's children
            if (hide) snapshots.get(bone).skipRender(true).skipChildrenRender(true);
        }
    }

    /** The bone the muzzle flash comes from this frame; null = suppressed (no flash). */
    static @Nullable String flashBone(RenderPassInfo<GeoRenderState> renderPassInfo) {
        Fitted fitted = renderPassInfo.getGeckolibData(FITTED);
        if (fitted == null) return "muzzle_flash";
        if (fitted.suppressed()) return null;
        if (fitted.muzzle() != null && renderPassInfo.model().getBone("flash_" + fitted.muzzle()).isPresent())
            return "flash_" + fitted.muzzle();
        return "muzzle_flash";
    }

    static boolean laserOn(RenderPassInfo<GeoRenderState> renderPassInfo) {
        Fitted fitted = renderPassInfo.getGeckolibData(FITTED);
        return fitted != null && fitted.laser();
    }

    /**
     * The gun's extras as GeckoLib 5 per-bone render tasks (the PoseStack is already posed at the bone's animated
     * pivot): the laser beam, and a short muzzle flash after each shot (first person).
     */
    static class FlashAndLaserLayer extends GeoRenderLayer<GunItem, GeoItemRenderer.RenderData, GeoRenderState> {
        private static final float FLASH_SECONDS = 0.045f;

        FlashAndLaserLayer(GunRenderer renderer) {
            super(renderer);
        }

        @Override
        public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo,
                                     BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
            boolean firstPerson = isFirstPerson(renderPassInfo.renderState());
            // AN/PEQ-15 visible laser: a thin red beam fading out ahead of the emitter (first and third person)
            if (laserOn(renderPassInfo) && (firstPerson || isThirdPerson(renderPassInfo.renderState()))) {
                float len = firstPerson ? 3.0f : 6.0f;
                renderPassInfo.model().getBone("beam_laser").ifPresent(bone -> consumer.accept(bone,
                        (info, b, renderTasks) -> renderTasks.submitCustomGeometry(info.poseStack(), RenderTypes.lightning(),
                                (pose, buffer) -> renderLaserBeam(pose, buffer, len))));
            }
            if (!firstPerson) return;
            float age = ClientState.secondsSinceShot();
            String flashBone = flashBone(renderPassInfo); // null = suppressed: no flash
            if (age < FLASH_SECONDS && flashBone != null) {
                float seed = ClientState.shotSeed();
                renderPassInfo.model().getBone(flashBone).ifPresent(bone -> consumer.accept(bone,
                        (info, b, renderTasks) -> renderTasks.submitCustomGeometry(info.poseStack(), RenderTypes.lightning(),
                                (pose, buffer) -> renderMuzzleFlash(pose, buffer, age, seed))));
            }
        }

        /** Laser beam along -Z from the emitter bone's pivot: two crossed planes so it reads from any angle. */
        private static void renderLaserBeam(PoseStack.Pose pose, VertexConsumer vc, float len) {
            Matrix4f m = pose.pose();
            float r = 0.12f / 16f;
            for (int i = 0; i < 2; i++) {
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
        }

        /** A few crossed, additive quads (star-shaped flash) at the muzzle, oriented along -Z. */
        private static void renderMuzzleFlash(PoseStack.Pose pose, VertexConsumer vc, float age, float seed) {
            float fade = 1f - age / FLASH_SECONDS;
            float len = 5.0f / 16f * (0.7f + 0.3f * fade), rad = 1.6f / 16f;
            PoseStack poseStack = new PoseStack();
            poseStack.last().set(pose);
            poseStack.rotateAround(Axis.ZP.rotationDegrees(seed * 360f), 0, 0, 0);
            for (int i = 0; i < 3; i++) {
                poseStack.pushPose();
                poseStack.rotateAround(Axis.ZP.rotationDegrees(i * 60f), 0, 0, 0);
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
        }
    }
}
