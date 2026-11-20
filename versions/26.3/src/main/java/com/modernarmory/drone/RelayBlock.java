package com.modernarmory.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Drone Relay Antenna: a portable radio repeater. Placed somewhere that sees both the pilot and where the drone
 * is going (a tunnel bend, a ridge, a doorway), it carries the link around the obstruction (DroneLink). Its
 * block entity only keeps it in the level's list of relays while its chunk is loaded.
 */
public class RelayBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** From the model (models/block/drone_relay.json): the radio box and the mast, facing north / turned east-west. */
    private static final VoxelShape SHAPE_NS = net.minecraft.world.phys.shapes.Shapes.or(Block.box(2.75, 0, 3.75, 13.25, 4.25, 12.25),
            Block.box(5.5, 4, 7, 10.5, 16, 9));
    private static final VoxelShape SHAPE_EW = net.minecraft.world.phys.shapes.Shapes.or(Block.box(3.75, 0, 2.75, 12.25, 4.25, 13.25),
            Block.box(7, 4, 5.5, 9, 16, 10.5));

    public RelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayBlockEntity(pos, state);
    }
}
