package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.block.GunsmithTableBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModernArmory.MODID);

    public static final DeferredBlock<GunsmithTableBlock> GUNSMITH_TABLE = BLOCKS.register("gunsmith_table",
            () -> new GunsmithTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)
                    .sound(SoundType.WOOD)));

    /** Drone Relay Antenna (drone.RelayBlock): carries a drone's link around obstructions. */
    public static final DeferredBlock<com.modernarmory.drone.RelayBlock> DRONE_RELAY = BLOCKS.register("drone_relay",
            () -> new com.modernarmory.drone.RelayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5f)
                    .sound(SoundType.METAL).noOcclusion()));

    /** HELWS laser turret (laser.LaserTurretBlock): one cell of the 3x4x3 emplacement, placed whole by its item. */
    public static final DeferredBlock<com.modernarmory.laser.LaserTurretBlock> LASER_TURRET = BLOCKS.register("laser_turret",
            () -> new com.modernarmory.laser.LaserTurretBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4f, 8f)
                    .sound(SoundType.NETHERITE_BLOCK).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    /** Thermite fire (block.ThermiteFireBlock): the AN-M14's red-hot fire that never spreads and burns out by itself. */
    public static final DeferredBlock<com.modernarmory.block.ThermiteFireBlock> THERMITE_FIRE = BLOCKS.register("thermite_fire",
            () -> new com.modernarmory.block.ThermiteFireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).replaceable().noCollission().instabreak().lightLevel(s -> 15).sound(SoundType.WOOL).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY).noLootTable()));

    private ModBlocks() {}
}
