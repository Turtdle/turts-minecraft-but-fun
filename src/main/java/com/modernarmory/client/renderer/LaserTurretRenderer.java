package com.modernarmory.client.renderer;

import com.modernarmory.ModernArmory;
import com.modernarmory.laser.LaserTurretBlockEntity;
import com.modernarmory.laser.TurretLayout;
import com.modernarmory.laser.TurretSeat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The laser turret, drawn whole from its core cell (geo from tools/modelgen/guns/laser_turret.py, front
 * along the block's facing). The turret and the beam director take the seat's pose while someone is at the
 * controls, else the pose they were left in.
 */
public class LaserTurretRenderer extends GeoBlockRenderer<LaserTurretBlockEntity> {
    public LaserTurretRenderer() {
        super(new Model());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot())); // the model's nose is -Z (north)
    }

    @Override
    public AABB getRenderBoundingBox(LaserTurretBlockEntity be) {
        return TurretLayout.bounds(be.getBlockPos(), be.facing());
    }

    @Override
    public boolean shouldRenderOffScreen(LaserTurretBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    /** Head yaw relative to the base (deg, + = right) and director elevation (deg up) at {@code pt}. */
    static float[] pose(LaserTurretBlockEntity be, float pt) {
        TurretSeat s = be.seat;
        if (s == null || s.isRemoved()) return new float[] {be.restYaw(), be.restElevation()};
        return new float[] {TurretLayout.localYaw(be.facing(), s.yaw(pt)), s.elevation(pt)};
    }

    static final class Model extends GeoModel<LaserTurretBlockEntity> {
        @Override
        public ResourceLocation getModelResource(LaserTurretBlockEntity be) {
            return ModernArmory.id("geo/item/laser_turret.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(LaserTurretBlockEntity be) {
            return ModernArmory.id("textures/item/laser_turret.png");
        }

        @Override
        public ResourceLocation getAnimationResource(LaserTurretBlockEntity be) {
            return ModernArmory.id("animations/item/laser_turret.animation.json");
        }

        @Override
        public void setCustomAnimations(LaserTurretBlockEntity be, long instanceId, AnimationState<LaserTurretBlockEntity> state) {
            float[] p = pose(be, state.getPartialTick());
            GeoBone turret = getAnimationProcessor().getBone("turret"), director = getAnimationProcessor().getBone("director");
            if (turret != null) turret.setRotY(-p[0] * Mth.DEG_TO_RAD); // + local yaw turns it right (clockwise from above)
            if (director != null) director.setRotX(p[1] * Mth.DEG_TO_RAD);
        }
    }
}
