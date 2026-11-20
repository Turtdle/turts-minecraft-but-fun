package com.modernarmory.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a bullet hits, for its hole (client.BulletHoles, row order of textures/misc/bullet_holes.png) and its
 * impact effects. Read from the block's sound type, which already groups blocks by what they are made of.
 */
public enum ImpactMaterial {
    STONE, WOOD, METAL, GLASS, EARTH, FABRIC;

    /** The material of {@code state}, or null for things that keep no hole (air, plants, leaves, liquids, invisible blocks like barriers). */
    public static @Nullable ImpactMaterial of(BlockState state) {
        if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
                || state.getRenderShape() == RenderShape.INVISIBLE) return null;
        SoundType s = state.getSoundType();
        if (s == SoundType.WOOD || s == SoundType.BAMBOO_WOOD || s == SoundType.CHERRY_WOOD || s == SoundType.NETHER_WOOD
                || s == SoundType.STEM || s == SoundType.SCAFFOLDING || s == SoundType.LADDER || s == SoundType.CHISELED_BOOKSHELF)
            return WOOD;
        if (s == SoundType.METAL || s == SoundType.COPPER || s == SoundType.ANVIL || s == SoundType.CHAIN
                || s == SoundType.NETHERITE_BLOCK || s == SoundType.LANTERN || s == SoundType.COPPER_BULB || s == SoundType.HEAVY_CORE
                || s == SoundType.COPPER_GRATE || s == SoundType.VAULT || s == SoundType.TRIAL_SPAWNER)
            return METAL;
        if (s == SoundType.GLASS) return GLASS;
        if (s == SoundType.GRAVEL || s == SoundType.SAND || s == SoundType.SNOW || s == SoundType.GRASS || s == SoundType.ROOTED_DIRT
                || s == SoundType.MUD || s == SoundType.SOUL_SAND || s == SoundType.SOUL_SOIL || s == SoundType.MOSS
                || s == SoundType.POWDER_SNOW || s == SoundType.FROGLIGHT || s == SoundType.SUSPICIOUS_SAND || s == SoundType.SUSPICIOUS_GRAVEL)
            return EARTH;
        if (s == SoundType.WOOL || s == SoundType.MOSS_CARPET) return FABRIC;
        return STONE;
    }
}
