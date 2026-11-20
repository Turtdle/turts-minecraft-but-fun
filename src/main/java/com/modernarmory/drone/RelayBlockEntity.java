package com.modernarmory.drone;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.modernarmory.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps a placed relay antenna in its level's list while it is loaded (server side), for DroneLink. */
public class RelayBlockEntity extends BlockEntity {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> RELAYS = new ConcurrentHashMap<>();

    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_RELAY.get(), pos, state);
    }

    /** The loaded relays of a level. */
    public static Collection<BlockPos> in(Level level) {
        Set<BlockPos> s = RELAYS.get(level.dimension());
        return s == null ? Set.of() : s;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) RELAYS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(worldPosition.immutable());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forget();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forget();
    }

    private void forget() {
        if (level == null || level.isClientSide()) return;
        Set<BlockPos> s = RELAYS.get(level.dimension());
        if (s != null) s.remove(worldPosition);
    }
}
