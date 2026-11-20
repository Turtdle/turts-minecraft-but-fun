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
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("TurretYaw", restYaw);
        tag.putFloat("TurretElevation", restElevation);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        restYaw = tag.getFloat("TurretYaw");
        restElevation = tag.getFloat("TurretElevation");
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
