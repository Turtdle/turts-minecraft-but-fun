package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.drone.RelayBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModernArmory.MODID);

    /** Drone Relay Antenna: lists itself for the drone link while loaded. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayBlockEntity>> DRONE_RELAY = BLOCK_ENTITIES.register("drone_relay",
            () -> new BlockEntityType<>(RelayBlockEntity::new, ModBlocks.DRONE_RELAY.get())); // 26.x: no Builder

    /** Laser turret core cell: draws the turret, keeps its head's rest pose. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.modernarmory.laser.LaserTurretBlockEntity>> LASER_TURRET =
            BLOCK_ENTITIES.register("laser_turret", () -> new BlockEntityType<>(com.modernarmory.laser.LaserTurretBlockEntity::new,
                    ModBlocks.LASER_TURRET.get()));

    private ModBlockEntities() {}
}
