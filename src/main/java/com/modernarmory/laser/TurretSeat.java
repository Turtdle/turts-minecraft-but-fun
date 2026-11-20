package com.modernarmory.laser;

import java.util.List;

import com.modernarmory.drone.DroneControl;
import com.modernarmory.drone.DroneEntity;
import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The operator's station of a laser turret: the cushioned seat behind it, and the fire control. The operator looks
 * through a sensor camera on its own mast (client.LaserOperator): the view stays on their mouse. A left click locks
 * the drone nearest the crosshair and fires: the head then tracks that drone by itself and holds the beam on it
 * until it burns down, whatever the operator looks at. The lock breaks when the drone leaves the operator's view
 * (their client says so; the server also lets go past a wide angle), when it is out of sight for a moment or out
 * of range, or on a right click. The laser heats while it fires and locks out when too hot until it has cooled.
 * Not locked, the head follows the operator's look so it is ready.
 * <p>
 * Server side runs it all; the head pose, the locked drone and the laser state are synced to every client for the
 * model, the beam and the operator's HUD.
 */
public class TurretSeat extends Entity {
    private static final EntityDataAccessor<BlockPos> CORE = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.BLOCK_POS);
    /** Turret yaw (world, Minecraft degrees) and director elevation (deg up). */
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ELEVATION = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.FLOAT);
    /** The locked drone (entity id), -1 = none. */
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.INT);
    /** The beam is on. */
    private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HEAT = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OVERHEATED = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.BOOLEAN);
    /** How far the locked drone has burned (0..1). */
    private static final EntityDataAccessor<Float> BURN = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.FLOAT);
    /** Power reaching the target (0..1: range and weather). */
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(TurretSeat.class, EntityDataSerializers.FLOAT);

    /** Lock a drone and fire / cease fire (network.LaserCommandPayload). */
    public static final int ENGAGE = 1, CEASE = 2;
    /** The lock is dropped after the drone has been out of sight this long. */
    private static final int LOST_TICKS = 20;
    /** The beam fires once the director is on the drone to within this (deg). */
    private static final double ON_TARGET_DEG = 1.5;
    /** Server fallback: the lock breaks when the drone is this far off the operator's look (deg). */
    private static final double VIEW_BREAK_DEG = 75;
    /** The beam's radius around its line when it meets a drone (blocks): a drone is hit if the beam touches it. */
    private static final double BEAM_RADIUS = 0.3;

    private final LaserSpec spec = LaserSpec.HELWS;
    private int ticksEmpty, hiddenTicks;
    /** Client: the turret pose this tick and last (smoothing); the operator's own client predicts it from their look. */
    public float clientYaw, clientElevation, prevClientYaw, prevClientElevation;
    private boolean clientPoseSet;

    public TurretSeat(EntityType<? extends TurretSeat> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ---- boarding ----------------------------------------------------------------------------

    /** Puts {@code player} at the controls of the turret whose core is at {@code core}, if nobody is there. */
    public static void board(ServerLevel level, BlockPos core, Direction facing, Player player) {
        if (player.isPassenger() || player.isSpectator()) return;
        for (TurretSeat s : seats(level, core)) {
            if (s.isVehicle()) {
                player.displayClientMessage(Component.literal("Someone is already at the controls"), true);
                return;
            }
            s.discard();
        }
        TurretSeat seat = new TurretSeat(ModEntities.TURRET_SEAT.get(), level);
        Vec3 at = TurretLayout.seat(core, facing);
        seat.moveTo(at.x, at.y, at.z, facing.toYRot(), 0);
        seat.entityData.set(CORE, core);
        if (level.getBlockEntity(core) instanceof LaserTurretBlockEntity be) {
            seat.entityData.set(YAW, facing.toYRot() + be.restYaw());
            seat.entityData.set(ELEVATION, be.restElevation());
        }
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
        if (player instanceof ServerPlayer sp) DroneControl.cue(sp, ModSounds.LASER_LOCK.get(), 0.6f, 0.8f);
    }

    static List<TurretSeat> seats(Level level, BlockPos core) {
        return level.getEntitiesOfClass(TurretSeat.class, new AABB(core).inflate(4), s -> s.core().equals(core));
    }

    /** Everyone off (the turret is going). */
    static void dismountAll(Level level, BlockPos core) {
        for (TurretSeat s : seats(level, core)) {
            s.ejectPassengers();
            s.discard();
        }
    }

    // ---- state ---------------------------------------------------------------------------------

    public BlockPos core() { return entityData.get(CORE); }
    public float turretYaw() { return entityData.get(YAW); }
    public float elevation() { return entityData.get(ELEVATION); }
    public int targetId() { return entityData.get(TARGET); }
    public boolean firing() { return entityData.get(FIRING); }
    public float heat() { return entityData.get(HEAT); }
    public boolean overheated() { return entityData.get(OVERHEATED); }
    public float burn() { return entityData.get(BURN); }
    public float power() { return entityData.get(POWER); }
    public LaserSpec spec() { return spec; }

    /** The turret's facing (from its core block), or NORTH if it is gone. */
    public Direction facing() {
        var s = level().getBlockState(core());
        return s.getBlock() instanceof LaserTurretBlock ? s.getValue(LaserTurretBlock.FACING) : Direction.NORTH;
    }

    public DroneEntity target() {
        return level().getEntity(targetId()) instanceof DroneEntity d && d.isAlive() ? d : null;
    }

    /** Where the beam starts now. */
    public Vec3 beamOrigin() {
        return TurretLayout.beamOrigin(core(), facing(), turretYaw(), elevation());
    }

    /** What the beam meets: where it ends, and the drone it is on (null if it ends on a block or in the air). */
    public record Hit(Vec3 end, DroneEntity drone) {}

    /** The beam from {@code from} along {@code dir}: the first drone it touches, else the first block, else full range. */
    public Hit beamHit(Vec3 from, Vec3 dir) {
        Vec3 to = from.add(dir.scale(spec.range()));
        var block = level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        DroneEntity best = null;
        Vec3 bestAt = null;
        for (DroneEntity d : level().getEntitiesOfClass(DroneEntity.class, new AABB(from, to).inflate(1), DroneEntity::isAlive)) {
            var at = d.getBoundingBox().inflate(BEAM_RADIUS).clip(from, to);
            if (at.isPresent() && (bestAt == null || at.get().distanceToSqr(from) < bestAt.distanceToSqr(from))) {
                best = d;
                bestAt = at.get();
            }
        }
        return best != null ? new Hit(bestAt, best) : new Hit(to, null);
    }

    public Vec3 beamEnd(Vec3 from, Vec3 dir) {
        return beamHit(from, dir).end();
    }

    /** Is there a clear line from {@code from} to {@code to}? */
    public boolean clear(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Power reaching {@code at}: range and weather. */
    public float powerAt(Vec3 at) {
        float p = spec.rangePower(beamOrigin().distanceTo(at));
        BlockPos bp = BlockPos.containing(at);
        if (level().isThundering()) p *= spec.thunderPower();
        else if (level().isRaining() && (level().isRainingAt(bp) || level().getBiome(bp).value().getPrecipitationAt(bp) != net.minecraft.world.level.biome.Biome.Precipitation.NONE))
            p *= spec.rainPower();
        return p;
    }

    // ---- commands from the operator's client (network.LaserCommandPayload) ------------------------------

    /** Lock {@code targetId} and fire, or cease fire. */
    public void command(ServerPlayer from, int action, int targetId) {
        if (getFirstPassenger() != from) return;
        if (action == CEASE) {
            release(null);
            return;
        }
        if (action != ENGAGE) return;
        if (overheated()) {
            DroneControl.cue(from, ModSounds.LASER_OVERHEAT.get(), 0.6f, 1.0f);
            return;
        }
        DroneEntity d = level().getEntity(targetId) instanceof DroneEntity e && e.isAlive() ? e : null;
        Vec3 o = beamOrigin();
        if (d == null || d.position().distanceTo(o) > spec.range() || !clear(o, d.getBoundingBox().getCenter())
                || offLook(from, d) > spec.engageCone() + 10) {
            DroneControl.cue(from, ModSounds.DRONE_BEEP.get(), 0.6f, 0.6f); // nothing to lock there
            return;
        }
        entityData.set(TARGET, d.getId());
        hiddenTicks = 0;
        DroneControl.cue(from, ModSounds.LASER_LOCK.get(), 0.8f, 1.2f);
    }

    /** Degrees between where the operator looks and the drone. */
    private static double offLook(ServerPlayer op, DroneEntity d) {
        Vec3 to = d.getBoundingBox().getCenter().subtract(op.getEyePosition()).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(op.getViewVector(1).dot(to), -1, 1)));
    }

    /** Drop the lock (a warning chirp to {@code op} if it was lost rather than finished). */
    private void release(ServerPlayer op) {
        if (targetId() < 0) return;
        entityData.set(TARGET, -1);
        entityData.set(FIRING, false);
        entityData.set(BURN, 0f);
        if (op != null) DroneControl.cue(op, ModSounds.DRONE_WARNING.get(), 0.6f, 1.2f);
    }

    // ---- ticking -------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!(level.getBlockEntity(core()) instanceof LaserTurretBlockEntity be)) {
            ejectPassengers();
            discard();
            return;
        }
        ServerPlayer op = getFirstPassenger() instanceof ServerPlayer p ? p : null;
        if (op == null) {
            entityData.set(TARGET, -1);
            if (++ticksEmpty > 2) { // left the seat: the turret stays where it was
                be.rest(TurretLayout.localYaw(facing(), turretYaw()), elevation());
                discard();
            }
            return;
        }
        ticksEmpty = 0;
        DroneEntity target = target();
        if (targetId() >= 0 && target == null) release(null); // gone: burned down, landed, crashed
        Vec3 origin = beamOrigin();
        if (target != null) {
            Vec3 c = target.getBoundingBox().getCenter();
            if (c.distanceTo(origin) > spec.range() || offLook(op, target) > VIEW_BREAK_DEG) {
                release(op);
                target = null;
            } else if (!clear(origin, c)) {
                if (++hiddenTicks > LOST_TICKS) {
                    release(op);
                    target = null;
                }
            } else {
                hiddenTicks = 0;
            }
        }
        // the head tracks the locked drone by itself; not locked, it follows where the operator looks
        if (target != null) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(origin);
            slew((float) Math.toDegrees(Math.atan2(-to.x, to.z)), (float) Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
        } else {
            slew(op.getYRot(), -op.getXRot());
        }
        float heat = heat();
        if (overheated() && heat <= spec.overheatRelease()) entityData.set(OVERHEATED, false);
        boolean firing = false;
        if (target != null && hiddenTicks == 0 && !overheated()) {
            Vec3 dir = TurretLayout.beamDirection(turretYaw(), elevation());
            Vec3 c = target.getBoundingBox().getCenter();
            firing = Math.toDegrees(Math.acos(Mth.clamp(dir.dot(c.subtract(origin).normalize()), -1, 1))) <= ON_TARGET_DEG;
        }
        if (firing) {
            Hit hit = beamHit(origin, TurretLayout.beamDirection(turretYaw(), elevation()));
            DroneEntity burning = hit.drone(); // the locked drone, or whatever flew into the beam first
            if (burning != null) {
                Vec3 c = hit.end();
                float power = powerAt(c);
                entityData.set(POWER, power);
                if (burning.lase(power / spec.dwellFor(burning.type().health()))) {
                    DroneControl.cue(op, ModSounds.LASER_LOCK.get(), 0.9f, 1.6f); // splash
                    if (burning == target) {
                        release(null);
                        firing = false;
                    }
                } else {
                    if (burning == target) entityData.set(BURN, burning.laserHeat());
                    if (tickCount % 10 == 0) level.playSound(null, c.x, c.y, c.z, ModSounds.LASER_BURN.get(), SoundSource.NEUTRAL, 0.7f, 1.0f);
                }
            }
        }
        if (firing) {
            heat += 1f / spec.heatTicks();
            if (heat >= 1) {
                heat = 1;
                entityData.set(OVERHEATED, true);
                DroneControl.cue(op, ModSounds.LASER_OVERHEAT.get(), 0.8f, 1.0f);
                release(null);
                firing = false;
            }
            if (tickCount % 20 == 0) level.playSound(null, origin.x, origin.y, origin.z, ModSounds.LASER_HUM.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        } else {
            heat = Math.max(0, heat - 1f / spec.coolTicks());
        }
        entityData.set(HEAT, heat);
        entityData.set(FIRING, firing);
    }

    /** Turret and director toward a world yaw and an elevation, at their slew rates. */
    private void slew(float wantYaw, float wantEl) {
        float dt = 0.05f;
        float yaw = turretYaw(), el = elevation();
        float dy = Mth.wrapDegrees(wantYaw - yaw);
        yaw += Mth.clamp(dy, -spec.yawRate() * dt, spec.yawRate() * dt);
        el += Mth.clamp(Mth.clamp(wantEl, spec.minElevation(), spec.maxElevation()) - el, -spec.pitchRate() * dt, spec.pitchRate() * dt);
        entityData.set(YAW, Mth.wrapDegrees(yaw));
        entityData.set(ELEVATION, el);
    }

    private void clientTick() {
        if (level().getBlockEntity(core()) instanceof LaserTurretBlockEntity be) be.seat = this;
        prevClientYaw = clientYaw;
        prevClientElevation = clientElevation;
        float yaw = turretYaw(), el = elevation();
        if (!clientPoseSet) {
            prevClientYaw = clientYaw = yaw;
            prevClientElevation = clientElevation = el;
            clientPoseSet = true;
            return;
        }
        if (getFirstPassenger() instanceof Player p && p.isLocalPlayer() && targetId() < 0) {
            // the operator's own client turns the turret at once (same slew as the server), no network lag
            float dt = 0.05f;
            clientYaw = Mth.wrapDegrees(clientYaw + Mth.clamp(Mth.wrapDegrees(p.getYRot() - clientYaw), -spec.yawRate() * dt, spec.yawRate() * dt));
            clientElevation += Mth.clamp(Mth.clamp(-p.getXRot(), spec.minElevation(), spec.maxElevation()) - clientElevation,
                    -spec.pitchRate() * dt, spec.pitchRate() * dt);
        } else {
            clientYaw = prevClientYaw + Mth.wrapDegrees(yaw - prevClientYaw);
            clientElevation = el;
        }
    }

    /** Client: the turret's world yaw at {@code pt}. */
    public float yaw(float pt) {
        return Mth.wrapDegrees(Mth.lerp(pt, prevClientYaw, prevClientYaw + Mth.wrapDegrees(clientYaw - prevClientYaw)));
    }

    public float elevation(float pt) {
        return Mth.lerp(pt, prevClientElevation, clientElevation);
    }

    // ---- riding ---------------------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return TurretLayout.dismount(core(), facing());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 256 * 256; // the beam is drawn from here
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CORE, BlockPos.ZERO);
        builder.define(YAW, 0f);
        builder.define(ELEVATION, 0f);
        builder.define(TARGET, -1);
        builder.define(FIRING, false);
        builder.define(HEAT, 0f);
        builder.define(OVERHEATED, false);
        builder.define(BURN, 0f);
        builder.define(POWER, 1f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
