package com.modernarmory.laser;

import com.modernarmory.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

/**
 * The laser turret's core cell: draws the turret (client.renderer.LaserTurretRenderer) and remembers
 * where the turret was left pointing. While someone is at the controls the turret follows their seat
 * (TurretSeat); when they leave, the seat writes the pose back here.
 */
public class LaserTurretBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Rest pose: head yaw relative to the base (deg, + = right) and director elevation (deg up). */
    private float restYaw, restElevation;
    /** Client: the seat currently at the controls (set by the seat each tick), or null. */
    public TurretSeat seat;

    public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_TURRET.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(LaserTurretBlock.FACING);
    }

    public float restYaw() { return restYaw; }
    public float restElevation() { return restElevation; }

    void rest(float localYaw, float elevation) {
        restYaw = localYaw;
        restElevation = elevation;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(ValueOutput out) { // 26.x: ValueOutput / ValueInput instead of CompoundTag
        super.saveAdditional(out);
        out.putFloat("TurretYaw", restYaw);
        out.putFloat("TurretElevation", restElevation);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        restYaw = in.getFloatOr("TurretYaw", 0);
        restElevation = in.getFloatOr("TurretElevation", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- GeckoLib: no keyframed animation; the renderer poses the turret ----------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
