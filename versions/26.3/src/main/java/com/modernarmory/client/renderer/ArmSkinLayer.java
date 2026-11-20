package com.modernarmory.client.renderer;

import java.util.function.BiConsumer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * First-person arms for an {@link ArmedItemRenderer}: the arm bones, textured with the local player's skin, as
 * GeckoLib 5 per-bone render tasks (the PoseStack is already posed at the bone's animated pivot). GeckoLib 4 did
 * this with a second reRender pass.
 */
public class ArmSkinLayer<T extends Item & GeoItem> extends GeoRenderLayer<T, GeoItemRenderer.RenderData, GeoRenderState> {
    public ArmSkinLayer(ArmedItemRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo,
                                 BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
        if (!ArmedItemRenderer.isFirstPerson(renderPassInfo.renderState())) return;
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Identifier skin = player.getSkin().body().texturePath();
        RenderType skinType = RenderTypes.entityTranslucent(skin);
        for (GeoBone bone : renderPassInfo.model().boneLookup().get().values()) {
            if (bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0 && ArmedItemRenderer.isArmBone(bone)) {
                consumer.accept(bone, (info, b, renderTasks) -> renderTasks.submitCustomGeometry(info.poseStack(), skinType,
                        (pose, buffer) -> renderCubes(cuboid, pose, buffer, info.packedLight(), info.packedOverlay())));
            }
        }
    }

    /** Draw a bone's cubes. The pose is at the bone pivot; cubes are defined relative to the model origin. */
    private static void renderCubes(CuboidGeoBone bone, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);
        bone.translateAwayFromPivotPoint(poseStack);
        for (GeoCube cube : bone.cubes) {
            poseStack.pushPose();
            cube.render(poseStack, buffer, light, overlay, 0xFFFFFFFF);
            poseStack.popPose();
        }
    }
}
