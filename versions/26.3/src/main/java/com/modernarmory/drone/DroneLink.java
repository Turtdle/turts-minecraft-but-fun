package com.modernarmory.drone;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The radio link between the pilot and the drone: signal 0..1 from distance and from what is in the way.
 * <ul>
 *   <li>Distance: full strength most of the way, then it falls off fast toward the edge of range.</li>
 *   <li>Every solid block between the controller and the drone keeps only {@link DroneType#wallKeep()} of
 *       the signal (water counts double): a wall or a house only weakens a modern link; a hillside or deep
 *       underground kills it. A placed relay antenna (RelayBlock) that sees both ends carries the link around.</li>
 *   <li>Range in game: the real systems reach kilometres, but the game only sends a player the world (terrain,
 *       mobs) around their body: ~128 m for mobs and the view distance for terrain. A drone flying beyond that
 *       would see an empty world, so the range is capped at {@link #GAME_RANGE} and one chunk inside the
 *       pilot's view distance.</li>
 * </ul>
 */
public final class DroneLink {
    /** Hard cap of the link range in game (m): roughly how far the server tracks mobs for a player. */
    public static final double GAME_RANGE = 128;
    /** Below this the link is lost: no picture, no control. */
    public static final float LOST = 0.1f;
    /** A lost link comes back only above this, so it doesn't flicker at the edge of range. */
    public static final float REGAIN = 0.2f;

    /** Effective range (m) for this pilot: the type's range, the game cap and the pilot's view distance. */
    public static double range(DroneType type, ServerPlayer pilot) {
        int view = Math.min(pilot.level().getServer().getPlayerList().getViewDistance(), Math.max(2, pilot.requestedViewDistance()));
        return Math.min(type.linkRange(), Math.min(GAME_RANGE, (view - 1) * 16.0));
    }

    /** Height of a relay's antenna tip above its block's bottom (blocks). */
    static final double RELAY_TIP = 0.95; // the whips reach the top of the block

    /** The link: its signal, and the relay it goes through (null = direct). */
    public record Link(float signal, BlockPos relay) {}

    /**
     * The best link from the pilot (or owner) to a drone: straight, or through one placed relay antenna (each leg
     * with the same signal model, the weaker leg counts). The drone itself must stay within the game range of the
     * pilot's body whatever relays there are: beyond it the game sends the pilot no world to look at.
     */
    public static Link link(ServerLevel level, DroneType type, ServerPlayer pilot, Vec3 drone) {
        double range = range(type, pilot);
        Vec3 from = pilot.getEyePosition();
        if (pilot.position().distanceTo(drone) >= range) return new Link(0, null);
        float best = signal(level, type, from, drone, range);
        BlockPos via = null;
        for (BlockPos r : RelayBlockEntity.in(level)) {
            Vec3 tip = Vec3.atBottomCenterOf(r).add(0, RELAY_TIP, 0);
            if (best >= 0.99f || tip.distanceTo(from) >= range || !level.isLoaded(r)) continue;
            float s = Math.min(signal(level, type, from, tip, range), signal(level, type, tip, drone, range));
            if (s > best) {
                best = s;
                via = r;
            }
        }
        return new Link(best, via);
    }

    /** Signal from the controller at {@code from} to the drone at {@code to}, 0..1. */
    public static float signal(ServerLevel level, DroneType type, Vec3 from, Vec3 to, double range) {
        double d = from.distanceTo(to);
        if (d >= range) return 0;
        double f = 1 - Math.pow(d / range, 6);
        int walls = obstruction(level, from, to);
        return (float) Mth.clamp(f * Math.pow(type.wallKeep(), walls), 0, 1);
    }

    /** Solid blocks (water x2) on the straight line between the two points, each counted once. */
    static int obstruction(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        double len = step.length();
        if (len < 1e-3) return 0;
        int n = (int) Math.ceil(len / 0.3);
        Set<BlockPos> seen = new HashSet<>();
        int count = 0;
        for (int i = 1; i < n; i++) {
            BlockPos p = BlockPos.containing(from.add(step.scale(i / (double) n)));
            if (!seen.add(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.canOcclude() && s.isCollisionShapeFullBlock(level, p)) count++;
            else if (!s.getFluidState().isEmpty()) count += 2;
            if (count > 40) break;
        }
        return count;
    }

    private DroneLink() {}
}
