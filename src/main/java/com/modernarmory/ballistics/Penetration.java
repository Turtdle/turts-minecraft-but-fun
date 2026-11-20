package com.modernarmory.ballistics;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * How much energy a bullet loses pushing through a block, per metre of material (1 block = 1 m).
 * The numbers are tuned so a 5.56 round behaves plausibly: it punches through glass, leaves and
 * thin wooden things (doors, trapdoors, fences), is stopped by a full wooden beam or a block of
 * earth, and never gets through stone or metal.
 */
public final class Penetration {
    /** Joules lost per metre travelled inside the material. Infinity = impenetrable. */
    public static double resistance(BlockState state, float hardness) {
        if (hardness < 0) return Double.POSITIVE_INFINITY; // bedrock, barriers
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) return 600; // shatters
        if (state.is(BlockTags.LEAVES)) return 300;
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) return 2_500;
        if (state.is(BlockTags.ICE)) return 6_000;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return 5_000; // wood: a 3/16-block door costs ~940 J
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return 15_000; // dirt, sand, gravel, snow
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return 2_000; // hay, sponges, leaves-ish
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return hardness >= 4.5f ? Double.POSITIVE_INFINITY : 60_000; // metal vs stone
        }
        return 8_000 + hardness * 6_000;
    }

    /** Whether bullets shatter this block when they pass through it. */
    public static boolean shatters(BlockState state) {
        return state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES);
    }

    /** Hard surfaces that can make a bullet ricochet at shallow angles. */
    public static boolean canRicochet(BlockState state, float hardness) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && hardness >= 1.0f;
    }

    private Penetration() {}
}
