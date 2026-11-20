package com.modernarmory.client.renderer;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.modernarmory.ModernArmory;
import com.modernarmory.drone.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A flying drone: its item model's geo (tools/modelgen/guns/&lt;id&gt;.py) at the type's render scale, turned
 * to its attitude (DroneFlight: it can bank, pitch and flip); rotors spin with the motor effort (alternate directions, as
 * on a real quad), the scout's gimbal follows the camera pitch, the player's arms are left out.
 * <p>
 * GeckoLib 5 renders from a render state: everything per drone is captured in {@link #addRenderData} (the
 * {@link DronePose} ticket) and applied as bone snapshot rotations in {@link #adjustModelBonesForRender}
 * (GeckoLib 4: GeoModel#setCustomAnimations).
 */
public class DroneEntityRenderer extends GeoEntityRenderer<DroneEntity, EntityRenderState> {
    private static final String[] ROTORS = {"rotor_fl", "rotor_fr", "rotor_rl", "rotor_rr"};
    private static final float[] SPIN = {1, -1, -1, 1};

    /** One drone's pose for this frame. */
    record DronePose(String type, org.joml.Quaternionf attitude, float rotorDeg, float gimbalPitch, boolean gimbal,
                     float scale, float halfHeight) {}

    static final DataTicket<DronePose> POSE = DataTicket.create("modernarmory_drone_pose", DronePose.class);

    public DroneEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.3f;
    }

    @Override
    public void addRenderData(DroneEntity d, @Nullable Void data, EntityRenderState state, float pt) {
        // EntityRenderState is a GeoRenderState at runtime (GeckoLib injects it); this project compiles without the injection
        org.joml.Quaternionf att = new org.joml.Quaternionf(d.prevAttitude).slerp(d.attitude(), pt);
        // the gimbal holds the camera's pitch whatever the airframe does: its angle relative to the body
        org.joml.Vector3f f = att.transform(new org.joml.Vector3f(0, 0, -1));
        float bodyDown = (float) -Math.toDegrees(Math.asin(Mth.clamp(f.y, -1, 1)));
        ((GeoRenderState) (Object) state).addGeckolibData(POSE, new DronePose(d.type().id(), att,
                Mth.lerp(pt, d.prevRotorAngle, d.rotorAngle), Mth.clamp(d.cameraPitch() - bodyDown, -60, 110), d.type().gimbal(),
                d.type().renderScale() * 16f, d.getBbHeight() * 0.5f));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void applyRotations(RenderPassInfo info, PoseStack pose, float nativeScale) {
        DronePose p = (DronePose) info.getGeckolibData(POSE);
        if (p == null) return;
        // the body's full attitude (it can fly inverted), turned about its middle
        pose.translate(0, p.halfHeight(), 0);
        pose.rotate(p.attitude());
        pose.translate(0, -p.halfHeight(), 0);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void scaleModelForRender(RenderPassInfo info, float widthScale, float heightScale) {
        DronePose p = (DronePose) info.getGeckolibData(POSE);
        float s = p == null ? 1 : p.scale(); // geo units are 1/16 block
        super.scaleModelForRender(info, widthScale * s, heightScale * s);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo info, BoneSnapshots snapshots) {
        for (Object o : info.model().boneLookup().get().values()) {
            GeoBone bone = (GeoBone) o;
            if (ArmedItemRenderer.isArmBone(bone)) snapshots.get(bone).skipRender(true); // the item model's first-person arms
        }
        DronePose p = (DronePose) info.getGeckolibData(POSE);
        if (p == null) return;
        float angle = p.rotorDeg() * Mth.DEG_TO_RAD;
        for (int i = 0; i < ROTORS.length; i++) {
            float a = angle * SPIN[i];
            snapshots.ifPresent(ROTORS[i], b -> b.setRotY(a));
        }
        if (p.gimbal()) snapshots.ifPresent("gimbal", b -> b.setRotX(-p.gimbalPitch() * Mth.DEG_TO_RAD));
    }

    /** The drone item's geo / texture / animations, picked by the drone's type. */
    static final class Model extends GeoModel<DroneEntity> {
        private final Map<String, DefaultedItemGeoModel<DroneEntity>> byType = new HashMap<>();

        private DefaultedItemGeoModel<DroneEntity> of(String type) {
            return byType.computeIfAbsent(type, t -> new DefaultedItemGeoModel<>(ModernArmory.id(t)));
        }

        private static String type(GeoRenderState state) {
            DronePose p = state.getGeckolibData(POSE);
            return p == null ? "drone_scout" : p.type();
        }

        @Override
        public Identifier getModelResource(GeoRenderState state) {
            return of(type(state)).getModelResource(state);
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            return of(type(state)).getTextureResource(state);
        }

        @Override
        public Identifier getAnimationResource(DroneEntity d) {
            return of(d.type().id()).getAnimationResource(d);
        }
    }
}
