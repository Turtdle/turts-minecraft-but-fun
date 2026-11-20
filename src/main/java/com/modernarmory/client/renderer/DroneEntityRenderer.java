package com.modernarmory.client.renderer;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A flying drone: its item model's geo (tools/modelgen/guns/&lt;id&gt;.py) at the type's render scale, turned
 * to its attitude (DroneFlight: it can bank, pitch and flip); rotors spin with the motor effort (alternate directions, as
 * on a real quad), the scout's gimbal follows the camera pitch, the player's arms are left out.
 */
public class DroneEntityRenderer extends GeoEntityRenderer<DroneEntity> {
    private static final String[] ROTORS = {"rotor_fl", "rotor_fr", "rotor_rl", "rotor_rr"};
    private static final float[] SPIN = {1, -1, -1, 1};

    public DroneEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(DroneEntity d, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        // the body's full attitude (it can fly inverted), turned about its middle
        float lift = d.getBbHeight() * 0.5f;
        pose.translate(0, lift, 0);
        pose.mulPose(new org.joml.Quaternionf(d.prevAttitude).slerp(d.attitude(), partialTick));
        pose.translate(0, -lift, 0);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack pose, DroneEntity d, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float s = d.type().renderScale() * 16f; // geo units are 1/16 block
        super.scaleModelForRender(widthScale * s, heightScale * s, pose, d, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (ArmedItemRenderer.isArmBone(bone)) return; // the first-person arms of the item model
        super.renderCubesOfBone(pose, bone, buffer, packedLight, packedOverlay, colour);
    }

    /** The drone item's geo / texture / animations, picked by the drone's type; rotors and gimbal driven here. */
    static final class Model extends GeoModel<DroneEntity> {
        @Override
        public ResourceLocation getModelResource(DroneEntity d) {
            return ModernArmory.id("geo/item/" + d.type().id() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(DroneEntity d) {
            return ModernArmory.id("textures/item/" + d.type().id() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(DroneEntity d) {
            return ModernArmory.id("animations/item/" + d.type().id() + ".animation.json");
        }

        @Override
        public void setCustomAnimations(DroneEntity d, long instanceId, AnimationState<DroneEntity> state) {
            float angle = Mth.lerp(state.getPartialTick(), d.prevRotorAngle, d.rotorAngle) * Mth.DEG_TO_RAD;
            for (int i = 0; i < ROTORS.length; i++) {
                GeoBone b = getAnimationProcessor().getBone(ROTORS[i]);
                if (b != null) b.setRotY(angle * SPIN[i]);
            }
            if (d.type().gimbal()) { // the gimbal holds the camera's pitch whatever the airframe does
                GeoBone g = getAnimationProcessor().getBone("gimbal");
                org.joml.Vector3f f = d.attitude().transform(new org.joml.Vector3f(0, 0, -1));
                float bodyDown = (float) -Math.toDegrees(Math.asin(Mth.clamp(f.y, -1, 1)));
                if (g != null) g.setRotX(-Mth.clamp(d.cameraPitch() - bodyDown, -60, 110) * Mth.DEG_TO_RAD);
            }
        }
    }
}
