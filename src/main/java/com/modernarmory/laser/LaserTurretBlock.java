package com.modernarmory.laser;

import com.modernarmory.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One cell of a laser turret emplacement (TurretLayout). It is placed whole by its item and is one thing:
 * breaking any cell takes the whole turret down and gives the item back (in survival); a cell
 * lost any other way (an explosion, a command) collapses the rest. The core cell's block entity draws the
 * turret; the other cells only give it its collision. Right-clicking any cell takes the operator's seat.
 */
public class LaserTurretBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, TurretLayout.LENGTH * TurretLayout.WIDTH * TurretLayout.HEIGHT - 1);
    /** Set while this code takes a turret apart, so the cells it removes don't start collapses of their own. */
    private static boolean collapsing;

    public LaserTurretBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, TurretLayout.CORE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL);
    }

    public static BlockPos core(BlockPos pos, BlockState state) {
        return TurretLayout.core(pos, state.getValue(FACING), state.getValue(CELL));
    }

    // ---- shape, light, rendering ---------------------------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return TurretLayout.shape(state.getValue(CELL));
    }

    /** No visual shape: the laser and the third-person camera see through to the model, not the cells. */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // the core's block entity draws the whole turret
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true; // the model is lit like the open air around it, not like the inside of a solid block
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.LASER_TURRET.get());
    }

    // ---- block entity (core only) ----------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CELL) == TurretLayout.CORE ? new LaserTurretBlockEntity(pos, state) : null;
    }

    /** The operator's sensor camera sits in the turret's cells: clicks from the seat never mine the turret. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return player.getVehicle() instanceof TurretSeat ? 0 : super.getDestroyProgress(state, player, level, pos);
    }

    // ---- the operator's seat -------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos core = core(pos, state);
        if (!(sl.getBlockEntity(core) instanceof LaserTurretBlockEntity)) return InteractionResult.FAIL;
        TurretSeat.board(sl, core, state.getValue(FACING), player);
        return InteractionResult.CONSUME;
    }

    // ---- one thing: break one cell, lose the turret ------------------------------------------------

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) collapse(level, core(pos, state), state.getValue(FACING), pos, !player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.isClientSide || collapsing) return;
        Direction facing = state.getValue(FACING);
        BlockPos core = core(pos, state);
        if (!complete(level, core, facing)) collapse(level, core, facing, null, true);
    }

    /** Every cell of the turret is still there, facing the same way. */
    public static boolean complete(Level level, BlockPos core, Direction facing) {
        for (int[] c : TurretLayout.cells()) {
            BlockState s = level.getBlockState(TurretLayout.cell(core, facing, c[0], c[1], c[2]));
            if (!(s.getBlock() instanceof LaserTurretBlock) || s.getValue(FACING) != facing
                    || s.getValue(CELL) != TurretLayout.index(c[0], c[1], c[2])) return false;
        }
        return true;
    }

    /**
     * Removes what is left of a turret (except {@code keep}, which the caller is removing itself), giving the
     * item back once if {@code drop}.
     */
    public static void collapse(Level level, BlockPos core, Direction facing, BlockPos keep, boolean drop) {
        if (collapsing) return;
        collapsing = true;
        try {
            TurretSeat.dismountAll(level, core);
            boolean any = false;
            for (int[] c : TurretLayout.cells()) {
                BlockPos p = TurretLayout.cell(core, facing, c[0], c[1], c[2]);
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof LaserTurretBlock) || s.getValue(FACING) != facing
                        || s.getValue(CELL) != TurretLayout.index(c[0], c[1], c[2])) continue;
                any = true;
                if (!p.equals(keep)) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop && (any || keep != null)) Block.popResource(level, core.above(), new ItemStack(ModItems.LASER_TURRET.get()));
        } finally {
            collapsing = false;
        }
    }

    /** Places a whole turret (the placement was checked). */
    public static void build(Level level, BlockPos core, Direction facing, Block block) {
        collapsing = true;
        try {
            for (int[] c : TurretLayout.cells())
                level.setBlock(TurretLayout.cell(core, facing, c[0], c[1], c[2]), block.defaultBlockState().setValue(FACING, facing)
                        .setValue(CELL, TurretLayout.index(c[0], c[1], c[2])), Block.UPDATE_ALL);
        } finally {
            collapsing = false;
        }
    }
}
