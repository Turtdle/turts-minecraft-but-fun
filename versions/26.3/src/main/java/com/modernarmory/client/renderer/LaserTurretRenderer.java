package com.modernarmory.client.renderer;

import org.jspecify.annotations.Nullable;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.modernarmory.ModernArmory;
import com.modernarmory.laser.LaserTurretBlockEntity;
import com.modernarmory.laser.TurretLayout;
import com.modernarmory.laser.TurretSeat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * The laser turret, drawn whole from its core cell (geo from tools/modelgen/guns/laser_turret.py, front
 * along the block's facing). The head and the beam director take the seat's pose while someone is at the
 * controls, else the pose they were left in.
 * <p>
 * GeckoLib 5 renders from a render state: the pose is captured in {@link #addRenderData} (the {@link TurretPose}
 * ticket) and applied to the bone snapshots in {@link #adjustModelBonesForRender} (GeckoLib 4: setCustomAnimations).
 */
public class LaserTurretRenderer extends GeoBlockRenderer<LaserTurretBlockEntity, BlockEntityRenderState> {
    /** One turret's pose for this frame: head yaw relative to the base (deg, + = right), director elevation (deg up), facing. */
    record TurretPose(float yaw, float elevation, Direction facing) {}

    static final DataTicket<TurretPose> POSE = DataTicket.create("modernarmory_turret_pose", TurretPose.class);

    public LaserTurretRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedItemGeoModel<>(ModernArmory.id("laser_turret")));
    }

    @Override
    public void addRenderData(LaserTurretBlockEntity be, @Nullable Void data, BlockEntityRenderState state, float pt) {
        float[] p = pose(be, pt);
        // BlockEntityRenderState is a GeoRenderState at runtime (GeckoLib injects it); this project compiles without the injection
        ((GeoRenderState) (Object) state).addGeckolibData(POSE, new TurretPose(p[0], p[1], be.facing()));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void tryRotateByBlockstate(RenderPassInfo info, PoseStack pose) {
        TurretPose p = (TurretPose) info.getGeckolibData(POSE);
        Direction facing = p == null ? Direction.NORTH : p.facing();
        pose.rotate(Axis.YP.rotationDegrees(180 - facing.toYRot())); // the model's front is -Z (north)
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo info, BoneSnapshots snapshots) {
        TurretPose p = (TurretPose) info.getGeckolibData(POSE);
        if (p == null) return;
        snapshots.ifPresent("turret", b -> b.setRotY(-p.yaw() * Mth.DEG_TO_RAD)); // + local yaw turns it right (clockwise from above)
        snapshots.ifPresent("director", b -> b.setRotX(p.elevation() * Mth.DEG_TO_RAD));
    }

    @Override
    public AABB getRenderBoundingBox(LaserTurretBlockEntity be) {
        return TurretLayout.bounds(be.getBlockPos(), be.facing());
    }

    @Override
    public boolean shouldRenderOffScreen() {
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
}
