package com.modernarmory.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where bullets leave marks, for the client's bullet holes (client.BulletHoles registers itself as the
 * listener, so this common class never loads client code). Called client side for each entry hole, exit hole
 * and ricochet scrape.
 */
public final class BulletImpacts {
    public interface Listener {
        /** @param size the hole decal's width in blocks; {@code scrape} = a glancing ricochet mark */
        void onImpact(Level level, BlockPos pos, BlockState state, Vec3 at, Direction face, double size, boolean scrape);
    }

    private static Listener listener;

    public static void setListener(Listener l) {
        listener = l;
    }

    static void impact(Level level, BlockPos pos, BlockState state, Vec3 at, Direction face, double size, boolean scrape) {
        if (listener != null && level.isClientSide()) listener.onImpact(level, pos, state, at, face, size, scrape);
    }

    private BulletImpacts() {}
}
