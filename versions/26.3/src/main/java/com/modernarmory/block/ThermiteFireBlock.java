package com.modernarmory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ScheduledTickAccess;

/**
 * Thermite fire, left by the AN-M14 (grenade.IncendiaryEffect): looks like fire but redder and hotter
 * (textures/block/thermite_fire_*, tools/texgen/thermite_fire_textures.py), stands on any solid top face,
 * never spreads, and burns out by itself {@value #BURN_TICKS} ticks (less up to {@value #BURN_JITTER}, so the
 * patch dies out raggedly) after it was lit. Like vanilla fire it lights up its surroundings and sets
 * whatever walks into it on fire; the heavy thermite damage comes from the grenade.
 */
public class ThermiteFireBlock extends BaseFireBlock {
    public static final int BURN_TICKS = 400, BURN_JITTER = 80;

    public ThermiteFireBlock(Properties properties) {
        super(properties, 1.0f);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Goes out when the block under it goes. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this) && level.getBlockState(pos).is(this))
            level.scheduleTick(pos, this, BURN_TICKS - level.getRandom().nextInt(BURN_JITTER)); // burns out
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
