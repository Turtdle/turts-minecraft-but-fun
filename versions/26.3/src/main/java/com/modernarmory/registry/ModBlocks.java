package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.block.GunsmithTableBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModernArmory.MODID);

    public static final DeferredBlock<GunsmithTableBlock> GUNSMITH_TABLE = BLOCKS.registerBlock("gunsmith_table",
            // 26.x: registerBlock creates the Properties with the block id already set
            GunsmithTableBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));

    /** Drone Relay Antenna (drone.RelayBlock): carries a drone's link around obstructions. */
    public static final DeferredBlock<com.modernarmory.drone.RelayBlock> DRONE_RELAY = BLOCKS.registerBlock("drone_relay",
            com.modernarmory.drone.RelayBlock::new, p -> p.mapColor(MapColor.COLOR_GREEN).strength(1.5f).sound(SoundType.METAL).noOcclusion());

    /** HELWS laser turret (laser.LaserTurretBlock): one cell of the 3x4x3 emplacement, placed whole by its item. */
    public static final DeferredBlock<com.modernarmory.laser.LaserTurretBlock> LASER_TURRET = BLOCKS.registerBlock("laser_turret",
            com.modernarmory.laser.LaserTurretBlock::new, p -> p.mapColor(MapColor.SAND).strength(4f, 8f).sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE)
                    .isViewBlocking((s, l, pos, box) -> false).isSuffocating((s, l, pos) -> false));

    /** Thermite fire (block.ThermiteFireBlock): the AN-M14's red-hot fire that never spreads and burns out by itself. */
    public static final DeferredBlock<com.modernarmory.block.ThermiteFireBlock> THERMITE_FIRE = BLOCKS.registerBlock("thermite_fire",
            com.modernarmory.block.ThermiteFireBlock::new, p -> p.mapColor(MapColor.FIRE).replaceable().noCollision().instabreak().lightLevel(s -> 15).sound(SoundType.WOOL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED).noLootTable());

    private ModBlocks() {}
}
