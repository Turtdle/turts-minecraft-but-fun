package com.modernarmory.laser;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where the laser turret's blocks go and where its parts are. The emplacement is 3 blocks wide and 4 deep:
 * the turret on its pallet takes the front 3x3 (and is 3 blocks tall), the operator's seat and console the
 * back row (one block, low). Cells are named in the turret's own frame: {@code i} 0..3 from the front to the
 * back, {@code j} 0..2 from its left to its right, {@code k} 0..2 up. The core cell (under the turret's axis)
 * holds the block entity and is the model's origin.
 * <p>
 * Model space is the geo's (tools/modelgen/guns/laser_turret.py): 16 units a block, the turret's rest aim
 * toward -Z, +X to its right, Y up, the origin on the ground under the core cell's centre.
 */
public final class TurretLayout {
    public static final int LENGTH = 4, WIDTH = 3, HEIGHT = 3;
    /** The core: under the turret's axis. */
    private static final int CORE_I = 1, CORE_J = 1;
    public static final int CORE = index(CORE_I, CORE_J, 0);
    /** The operator's row. */
    private static final int SEAT_ROW = 3;

    /** Model points (units) from the geo: the turret's yaw axis, the director's pitch axis, the beam exit at rest, the seat. */
    public static final Vec3 TURRET_PIVOT = new Vec3(0, 26, 0);
    public static final Vec3 DIRECTOR_PIVOT = new Vec3(0, 39, 0);
    public static final Vec3 APERTURE = new Vec3(0, 39, -7.3);
    public static final Vec3 SEAT = new Vec3(0, 10, 32);
    /**
     * The operator's sensor camera: on a mast above and to the left of the laser head (clear of its sweep), so the
     * operator looks where they like while the head tracks a locked drone, and sees the beam go out to it.
     */
    public static final Vec3 SENSOR = new Vec3(-14, 54, 6);
    /** Where a leaving operator stands: on the ground just behind the seat. */
    private static final Vec3 DISMOUNT = new Vec3(0, 0, 48);

    /**
     * Collision of each kind of cell (pixels), from the model: the pallet with its cabinets (up to 22 high) and the
     * pedestal fill the turret's two lower layers; the head sweeps the whole top layer (to 47.4); in the back row
     * the seat and console stand on a narrow skid (cushion top 9.5), the skid alone at the sides (4).
     */
    private static final VoxelShape FULL = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SEAT_SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 10, 16);
    private static final VoxelShape SKID_SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 4, 16);

    private static final List<int[]> CELLS = new ArrayList<>();

    static {
        for (int k = 0; k < HEIGHT; k++)
            for (int j = 0; j < WIDTH; j++)
                for (int i = 0; i < LENGTH; i++)
                    if (k == 0 || i < SEAT_ROW) CELLS.add(new int[] {i, j, k});
    }

    /** Every occupied cell as {i, j, k}. */
    public static List<int[]> cells() {
        return CELLS;
    }

    public static int index(int i, int j, int k) {
        return i + LENGTH * (j + WIDTH * k);
    }

    public static int i(int index) { return index % LENGTH; }
    public static int j(int index) { return index / LENGTH % WIDTH; }
    public static int k(int index) { return index / (LENGTH * WIDTH); }

    /** A cell's collision. */
    public static VoxelShape shape(int index) {
        if (i(index) != SEAT_ROW) return FULL;
        return j(index) == CORE_J ? SEAT_SHAPE : SKID_SHAPE;
    }

    /** The block of cell (i, j, k) of a turret whose core is at {@code core}, facing {@code facing}. */
    public static BlockPos cell(BlockPos core, Direction facing, int i, int j, int k) {
        Direction right = facing.getClockWise();
        return core.relative(facing, CORE_I - i).relative(right, j - CORE_J).above(k);
    }

    public static BlockPos cell(BlockPos core, Direction facing, int index) {
        return cell(core, facing, i(index), j(index), k(index));
    }

    /** The core of the turret a cell belongs to. */
    public static BlockPos core(BlockPos cell, Direction facing, int index) {
        Direction right = facing.getClockWise();
        return cell.relative(facing, i(index) - CORE_I).relative(right, CORE_J - j(index)).below(k(index));
    }

    /** Where it goes when placed at {@code target} (the block the player aims at): the seat row there, the turret beyond it. */
    public static BlockPos coreForTarget(BlockPos target, Direction facing) {
        return target.relative(facing, SEAT_ROW - CORE_I);
    }

    /** A model point (units) in the world, for a turret with its core at {@code core}, facing {@code facing}. */
    public static Vec3 toWorld(BlockPos core, Direction facing, Vec3 model) {
        Direction right = facing.getClockWise();
        double s = 1 / 16.0;
        return new Vec3(core.getX() + 0.5, core.getY(), core.getZ() + 0.5)
                .add(right.getStepX() * model.x * s - facing.getStepX() * model.z * s, model.y * s,
                        right.getStepZ() * model.x * s - facing.getStepZ() * model.z * s);
    }

    /** The head's yaw relative to the base (deg, + = turned right) for a world yaw. */
    public static float localYaw(Direction facing, float worldYaw) {
        return net.minecraft.util.Mth.wrapDegrees(worldYaw - facing.toYRot());
    }

    /**
     * Where the beam leaves the director (world), with the head at {@code worldYaw} (Minecraft degrees) and the
     * director at {@code elevation} (deg up).
     */
    public static Vec3 beamOrigin(BlockPos core, Direction facing, float worldYaw, float elevation) {
        float yaw = localYaw(facing, worldYaw) * net.minecraft.util.Mth.DEG_TO_RAD;
        float el = elevation * net.minecraft.util.Mth.DEG_TO_RAD;
        // the aperture about the director's pitch axis (X): + elevation lifts the -Z front
        Vec3 a = APERTURE.subtract(DIRECTOR_PIVOT);
        Vec3 pitched = new Vec3(a.x, a.y * Math.cos(el) - a.z * Math.sin(el), a.y * Math.sin(el) + a.z * Math.cos(el));
        Vec3 onTurret = pitched.add(DIRECTOR_PIVOT).subtract(TURRET_PIVOT);
        // the head about Y: + local yaw turns the -Z front toward +X (its right)
        double c = Math.cos(yaw), s = Math.sin(yaw);
        Vec3 yawed = new Vec3(onTurret.x * c - onTurret.z * s, onTurret.y, onTurret.x * s + onTurret.z * c);
        return toWorld(core, facing, yawed.add(TURRET_PIVOT));
    }

    /** Unit vector the director points along (world). */
    public static Vec3 beamDirection(float worldYaw, float elevation) {
        return Vec3.directionFromRotation(-elevation, worldYaw);
    }

    public static Vec3 seat(BlockPos core, Direction facing) {
        return toWorld(core, facing, SEAT);
    }

    public static Vec3 sensor(BlockPos core, Direction facing) {
        return toWorld(core, facing, SENSOR);
    }

    public static Vec3 dismount(BlockPos core, Direction facing) {
        return toWorld(core, facing, DISMOUNT);
    }

    /** The whole emplacement's box (render culling, entity checks). */
    public static AABB bounds(BlockPos core, Direction facing) {
        AABB box = null;
        for (int[] c : CELLS) {
            AABB b = new AABB(cell(core, facing, c[0], c[1], c[2]));
            box = box == null ? b : box.minmax(b);
        }
        return box;
    }

    /** A cell's collision box in the world. */
    public static AABB cellBox(BlockPos core, Direction facing, int[] c) {
        BlockPos p = cell(core, facing, c[0], c[1], c[2]);
        return shape(index(c[0], c[1], c[2])).bounds().move(p);
    }

    // ---- placement --------------------------------------------------------------------------

    /** Why a turret can't go somewhere (or OK). */
    public enum Problem {
        OK(""),
        BLOCKED("Not enough room: %d block(s) in the way"),
        GROUND("Needs firmer ground: %d of its cells are over a drop"),
        ENTITY("Something is standing in the way"),
        OUTSIDE("Out of the world's limits"),
        PROTECTED("You can't build here");

        private final String message;

        Problem(String message) {
            this.message = message;
        }

        public String message(int count) {
            return String.format(message, count);
        }
    }

    /**
     * A placement check.
     * @param blocked     cells already taken by something solid
     * @param unsupported bottom cells with nothing to stand on below
     */
    public record Placement(BlockPos core, Direction facing, Problem problem, List<BlockPos> blocked, List<BlockPos> unsupported) {
        public boolean ok() {
            return problem == Problem.OK;
        }

        public String message() {
            return problem.message(problem == Problem.GROUND ? unsupported.size() : blocked.size());
        }
    }

    /** At most this many bottom cells (of 12) may hang over air: the pallet has to sit on the ground. */
    public static final int MAX_UNSUPPORTED = 3;

    /** Can a turret go with its seat row at {@code target}, facing {@code facing}? */
    public static Placement check(Level level, BlockPos target, Direction facing, Player player) {
        BlockPos core = coreForTarget(target, facing);
        List<BlockPos> blocked = new ArrayList<>(), unsupported = new ArrayList<>();
        boolean outside = false, prot = false;
        for (int[] c : CELLS) {
            BlockPos p = cell(core, facing, c[0], c[1], c[2]);
            if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p)) {
                outside = true;
                continue;
            }
            if (player != null && !level.mayInteract(player, p)) prot = true;
            if (!level.getBlockState(p).canBeReplaced()) blocked.add(p);
            if (c[2] == 0) {
                BlockPos below = p.below();
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) unsupported.add(p);
            }
        }
        Problem problem = outside ? Problem.OUTSIDE : prot ? Problem.PROTECTED : !blocked.isEmpty() ? Problem.BLOCKED
                : unsupported.size() > MAX_UNSUPPORTED ? Problem.GROUND : Problem.OK;
        if (problem == Problem.OK) {
            outer:
            for (Entity e : level.getEntities((Entity) null, bounds(core, facing).deflate(0.01),
                    e -> !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !e.isSpectator() && e.isAlive())) {
                for (int[] c : CELLS) {
                    if (e.getBoundingBox().intersects(cellBox(core, facing, c))) {
                        problem = Problem.ENTITY;
                        break outer;
                    }
                }
            }
        }
        return new Placement(core, facing, problem, blocked, unsupported);
    }

    private TurretLayout() {}
}
