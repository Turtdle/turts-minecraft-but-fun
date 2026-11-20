package com.modernarmory.drone;

import java.util.UUID;

import com.modernarmory.network.DronePilotPayload;
import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModItems;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.UUIDUtil;
import net.neoforged.fml.loading.FMLEnvironment;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;

/**
 * A deployed drone. Who flies it:
 * <ul>
 *   <li>While its pilot has the link (signal above {@link DroneLink#LOST}, battery left), the pilot's client
 *       runs {@link DroneFlight} with the stick input and sends the result every tick ({@link DronePilotPayload});
 *       the server checks the move and passes it on to everyone else.</li>
 *   <li>Otherwise the server flies it: the type's {@link LinkLoss} autopilot while a pilot is still in the
 *       camera view, a hover (then landing and motors off) when nobody is flying it, a fall with the motors off.</li>
 * </ul>
 * The payload strategy ({@link DronePayload}) handles the primary action, impacts, contact and being shot
 * down. The battery drains with flight effort; water shorts it out. A landed or idle drone is picked up by
 * right-clicking it, keeping its charge.
 */
public class DroneEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    /** Entity id of the player in the camera view, -1 = nobody. */
    private static final EntityDataAccessor<Integer> PILOT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIGNAL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    /** The link is up (with hysteresis: lost below DroneLink.LOST, back only above DroneLink.REGAIN). */
    private static final EntityDataAccessor<Boolean> LINKED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    /** The link goes through a relay antenna. */
    private static final EntityDataAccessor<Boolean> RELAYED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    /** A jammer is on the control link (see DroneJamming): no link whatever the signal. */
    private static final EntityDataAccessor<Boolean> JAMMED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    /** Rotor effort 0..1; 0 = motors off. */
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CAM_PITCH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    /** Body attitude (camera convention, see DroneFlight): flips and all. */
    private static final EntityDataAccessor<org.joml.Quaternionfc> ATTITUDE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.QUATERNION);
    /** Ticks of flight left before the payload is live; 0 = armed, -1 = nothing to arm. */
    private static final EntityDataAccessor<Integer> ARM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("flight");
    private static final RawAnimation ARMED = RawAnimation.begin().thenLoop("armed");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private Vec3 velocity = Vec3.ZERO; // m/s
    private float health = -1;
    private int lostTicks, idleTicks;
    /** Server: ticks of jamming left. */
    private int jamTicks;
    /** Server: how far a laser has burned it (0..1; at 1 it is destroyed), and when the beam was last on it. */
    private float laserHeat;
    private int lasedTick;
    private long lastPilotUpdate;
    private boolean detonated;
    /** Where the last contact check ended: the next one sweeps from here, so a fast drone can't pass through a target. */
    private Vec3 lastContactPos;

    /** Collective thrust 0..1 (of the motors' maximum). */
    private float thrust;

    /** Pilot's client: this tick's stick input (set by the client before the drone ticks): acro sticks, or velocity sticks. */
    public AcroSticks pilotSticks;
    public DroneInput pilotInput;
    /** Client: last tick's attitude, for smooth rendering in between. */
    public final org.joml.Quaternionf prevAttitude = new org.joml.Quaternionf();
    /** Client visuals: rotor angle (deg) and airframe tilt (deg), current and previous tick. */
    public float rotorAngle, prevRotorAngle;
    /** Acro: the airframe's current pitch, roll and yaw rates (deg/s), on whichever side flies it. */
    public float pitchRate, rollRate, yawRate;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    /** A new drone of {@code type} at {@code pos}; {@code airborne} = hand-launched, motors already running. */
    public static DroneEntity create(Level level, DroneType type, Player owner, Vec3 pos, float yaw, float battery, boolean airborne) {
        DroneEntity d = new DroneEntity(ModEntities.DRONE.get(), level);
        d.entityData.set(TYPE, type.id());
        d.refreshDimensions();
        d.owner = owner.getUUID();
        d.health = type.health();
        d.setBattery(battery);
        d.entityData.set(ARM, type.payload().armTicks() > 0 ? type.payload().armTicks() : -1);
        d.snapTo(pos.x, pos.y, pos.z, yaw, 0);
        d.setAttitude(DroneFlight.level(yaw));
        if (airborne) {
            d.setThrottle(0.55f);
            d.thrust = 1 / type.thrustToWeight(); // launched hovering
        }
        return d;
    }

    // ---- state ------------------------------------------------------------------------------

    public DroneType type() {
        DroneType t = Drones.byId(entityData.get(TYPE));
        return t != null ? t : Drones.SCOUT;
    }

    public int pilotId() { return entityData.get(PILOT); }
    public float battery() { return entityData.get(BATTERY); }
    public float signal() { return entityData.get(SIGNAL); }
    public boolean linked() { return entityData.get(LINKED); }
    public boolean relayed() { return entityData.get(RELAYED); }
    public boolean jammed() { return entityData.get(JAMMED); }
    public float throttle() { return entityData.get(THROTTLE); }
    public float cameraPitch() { return entityData.get(CAM_PITCH); }
    public boolean motorsOn() { return throttle() > 0; }
    public boolean lowBattery() { return battery() < 0.12f; }
    /** Payload live (only drones with something to arm). */
    public boolean armed() { return entityData.get(ARM) == 0; }
    /** Seconds until armed, 0 when armed, -1 when there is nothing to arm. */
    public float armSeconds() { int a = entityData.get(ARM); return a < 0 ? -1 : a / 20f; }
    public UUID owner() { return owner; }
    /** The body's attitude (a copy). */
    public org.joml.Quaternionf attitude() { return new org.joml.Quaternionf(entityData.get(ATTITUDE)); }
    /** Collective thrust 0..1. */
    public float thrust() { return thrust; }
    public Vec3 velocity() { return velocity; }
    public float health() { return health; }

    void setBattery(float b) { entityData.set(BATTERY, Mth.clamp(b, 0, 1)); }
    void setThrottle(float t) { entityData.set(THROTTLE, t); }
    void setThrust(float t) { thrust = t; }
    void setAttitude(org.joml.Quaternionf q) { entityData.set(ATTITUDE, new org.joml.Quaternionf(q)); }
    void setCameraPitch(float p) { entityData.set(CAM_PITCH, Mth.clamp(p, -90, 90)); }
    void setVelocity(Vec3 v) { velocity = v; setDeltaMovement(v.scale(DroneFlight.DT)); }
    void setPilot(int id) { entityData.set(PILOT, id); }
    void setSignal(float s) {
        entityData.set(SIGNAL, s);
        entityData.set(LINKED, s >= (linked() ? DroneLink.LOST : DroneLink.REGAIN));
    }

    /** Pilot's client: the server refused a move; the drone is really here. */
    public void correctTo(Vec3 pos) {
        setPos(pos);
        getPositionCodec().setBase(pos);
        setVelocity(Vec3.ZERO);
    }

    /** The pilot has the link: their client flies the drone. */
    public boolean pilotControls() {
        return pilotId() >= 0 && linked() && battery() > 0 && !detonated;
    }

    /** 26.x authority: the pilot's client simulates the drone while it has the link (as a boat's driver does). */
    @Override
    protected boolean isLocalClientAuthoritative() {
        return pilotControls() && level().getEntity(pilotId()) instanceof Player p && p.isLocalPlayer();
    }

    @Override
    public boolean isClientAuthoritative() {
        return pilotControls();
    }

    /** Pilot's client: true while it flies the drone itself. */
    public boolean flownHere() {
        return isLocalInstanceAuthoritative();
    }

    /** Watchers' clients interpolate the 20 Hz position updates (as boats do). */
    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    /** Server: the player in the camera view, if still valid (alive, here, not spectating). */
    public ServerPlayer pilot() {
        int id = pilotId();
        if (id < 0) return null;
        return level().getEntity(id) instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && p.level() == level() ? p : null;
    }

    /** Server: who gets the kill credit: the pilot, else the owner if online. */
    public LivingEntity controller() {
        ServerPlayer p = pilot();
        if (p != null) return p;
        return owner != null && level().getPlayerByUUID(owner) instanceof ServerPlayer o ? o : null;
    }

    // ---- ticking ----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) clientTick();
        else serverTick((ServerLevel) level());
    }

    private void clientTick() {
        prevRotorAngle = rotorAngle;
        prevAttitude.set(entityData.get(ATTITUDE));
        if (flownHere()) {
            double impact = type().flightMode() == FlightMode.ACRO && pilotSticks != null ? DroneFlight.stepAcro(this, pilotSticks)
                    : DroneFlight.stepAuto(this, pilotInput != null ? pilotInput : DroneInput.hold(getYRot(), cameraPitch()), true);
            // keep the base the server's relative moves are decoded against current, or the first update after the
            // server takes over (link lost) would put the drone back where it started
            getPositionCodec().setBase(position());
            sendToServer(new DronePilotPayload(getId(), position(), velocity.toVector3f(), attitude(), cameraPitch(),
                    thrust, (float) impact, onGround()));
        }
        rotorAngle += throttle() * 75f; // rotors spin with the effort
    }

    /** Client -> server from common code: only ever runs on the pilot's client (see entity.ClientAccess). */
    private static void sendToServer(DronePilotPayload p) {
        if (FMLEnvironment.getDist().isClient()) com.modernarmory.client.DronePilot.send(p);
    }

    private void serverTick(ServerLevel level) {
        DroneType t = type();
        ServerPlayer pilot = pilot();
        if (pilotId() >= 0 && pilot == null) DroneControl.release(this, true);
        if (jamTicks > 0 && --jamTicks == 0) entityData.set(JAMMED, false); // the link comes back at the next signal check
        if (laserHeat > 0 && tickCount - lasedTick > 2) laserHeat = Math.max(0, laserHeat - 0.004f); // off the beam: it cools
        if (tickCount % 5 == 0) updateSignal(level, pilot);
        if (motorsOn()) setBattery(battery() - t.drainPerTick() * (0.5f + 0.9f * throttle()));
        if (isInWater() && battery() > 0) setBattery(0); // water shorts the electronics
        if (battery() <= 0 && motorsOn()) setThrottle(0);

        if (pilotControls()) {
            lostTicks = 0;
            idleTicks = 0;
            // the pilot's client flies it; if its updates stop (lag), it just holds where it is (the server may not move it)
            if (level.getGameTime() - lastPilotUpdate > 10) setVelocity(Vec3.ZERO);
        } else if (motorsOn()) {
            DroneInput in = pilotId() >= 0 ? t.linkLoss().autopilot(this, ++lostTicks, pilot) : LinkLoss.HOVER.autopilot(this, 0, null);
            if (in == null) {
                fly(level, DroneInput.hold(getYRot(), cameraPitch()), false);
            } else {
                fly(level, in, true);
                // landed with nothing to do: the flight controller disarms the motors
                if (onGround() && in.lift() <= 0) {
                    if (++idleTicks > 40) setThrottle(0);
                } else idleTicks = 0;
            }
        } else if (!onGround() || velocity.lengthSqr() > 1e-4) {
            fly(level, DroneInput.hold(getYRot(), cameraPitch()), false);
        }
        if (isRemoved()) return;
        tickPayload(level);
        if ((motorsOn() || pilotId() >= 0) && tickCount % 20 == 0)
            level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL, chunkPosition(), 2); // the moving-entity ticket
    }

    private void fly(ServerLevel level, DroneInput in, boolean motors) {
        double impact = DroneFlight.stepAuto(this, in, motors);
        if (impact > 0) impact(level, impact);
    }

    /** A block took {@code speed} m/s off the drone (server; from its own flight or the pilot's report). */
    void impact(ServerLevel level, double speed) {
        if (speed > 3) {
            float vol = (float) Mth.clamp(speed / 12, 0.25, 1), pitch = 0.9f + random.nextFloat() * 0.2f;
            ServerPlayer pilot = pilot();
            // everyone near hears it there; the pilot hears it through the controller wherever their body is
            level.playSound(pilot, getX(), getY(), getZ(), ModSounds.DRONE_CRASH.get(), SoundSource.NEUTRAL, vol, pitch);
            if (pilot != null) DroneControl.cue(pilot, ModSounds.DRONE_CRASH.get(), vol, pitch);
        }
        type().payload().onImpact(level, this, speed);
    }

    private void tickPayload(ServerLevel level) {
        int arm = entityData.get(ARM);
        if (arm > 0 && motorsOn() && !onGround()) {
            entityData.set(ARM, --arm);
            if (arm == 0 && pilot() != null) DroneControl.cue(pilot(), ModSounds.DRONE_ARM.get(), 0.8f, 1.0f);
        }
        Vec3 from = lastContactPos != null && lastContactPos.distanceToSqr(position()) < 25 ? lastContactPos : position();
        lastContactPos = position();
        if (!motorsOn()) return;
        AABB swept = getBoundingBox().expandTowards(from.subtract(position())).inflate(0.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, swept,
                e -> e.isAlive() && !e.isSpectator() && e.getId() != pilotId() && !e.getUUID().equals(owner))) {
            type().payload().onContact(level, this, e);
            if (isRemoved()) return;
        }
    }

    private void updateSignal(ServerLevel level, ServerPlayer pilot) {
        ServerPlayer ref = pilot != null ? pilot : owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer o ? o : null;
        boolean was = linked();
        DroneLink.Link link = ref == null || jammed() ? new DroneLink.Link(0, null) : DroneLink.link(level, type(), ref, getEyePosition());
        setSignal(link.signal());
        entityData.set(RELAYED, link.relay() != null);
        linkChanged(pilot, was);
    }

    /** Tells the pilot the link went or came back; when it went, the server flies the drone from exactly here. */
    private void linkChanged(ServerPlayer pilot, boolean was) {
        if (pilot == null || was == linked()) return;
        DroneControl.cue(pilot, linked() ? ModSounds.DRONE_BEEP.get() : ModSounds.DRONE_WARNING.get(), 0.8f, 1.0f);
        if (!linked()) DroneControl.correct(pilot, this);
    }

    /** Server: jammed for at least {@code ticks} more ticks; the link drops at once (see DroneJamming). */
    void jam(int ticks) {
        jamTicks = Math.max(jamTicks, ticks);
        if (jammed()) return;
        entityData.set(JAMMED, true);
        boolean was = linked();
        setSignal(0);
        entityData.set(RELAYED, false);
        linkChanged(pilot(), was);
    }

    /** Server: the pilot's client flew the drone this tick. Moves further than the drone can fly are refused. */
    public void acceptPilotUpdate(ServerPlayer from, DronePilotPayload p) {
        if (from.getId() != pilotId() || !pilotControls()) return;
        Vec3 to = p.pos();
        double limit = type().terminalSpeed() * DroneFlight.DT * 1.5 + 0.35;
        if (to.distanceTo(position()) > limit || !Double.isFinite(to.x + to.y + to.z)) {
            DroneControl.correct(from, this);
            return;
        }
        setPos(to);
        org.joml.Quaternionf att = new org.joml.Quaternionf(p.attitude());
        if (!Float.isFinite(att.lengthSquared()) || att.lengthSquared() < 1e-6f) att = DroneFlight.level(getYRot());
        setAttitude(att.normalize());
        setYRot(DroneFlight.heading(att));
        setCameraPitch(p.pitch());
        thrust = Mth.clamp(p.thrust(), 0, 1);
        setThrottle(Mth.clamp(0.3f + 0.7f * thrust * type().thrustToWeight() / (1 + 0.5f * type().thrustToWeight()), 0.3f, 1));
        Vec3 v = new Vec3(p.velocity());
        double max = type().terminalSpeed() * 1.3;
        setVelocity(v.length() > max ? v.normalize().scale(max) : v);
        setOnGround(p.onGround());
        lastPilotUpdate = level().getGameTime();
        if (p.impact() > 0) impact((ServerLevel) level(), Math.min(p.impact(), 60));
    }

    // ---- damage, pickup -----------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { // 26.x: server-side hurt
        if (isInvulnerableToBase(source) || isRemoved() || detonated) return false;
        markHurt();
        crashDamage(amount);
        return true;
    }

    /** Server: damage to the airframe; at zero it is destroyed (and the payload decides what that means). */
    public void crashDamage(float amount) {
        if (level().isClientSide() || isRemoved() || detonated) return;
        if (health < 0) health = type().health(); // summoned without data
        health -= amount;
        if (health > 0) return;
        ServerLevel level = (ServerLevel) level();
        type().payload().onDestroyed(level, this);
        if (isRemoved()) return;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ModItems.drone(type())),
                getX(), getY() + 0.1, getZ(), 16, 0.2, 0.1, 0.2, 0.08);
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 8, 0.2, 0.1, 0.2, 0.02);
        ServerPlayer pilot = pilot();
        level.playSound(pilot, getX(), getY(), getZ(), ModSounds.DRONE_CRASH.get(), SoundSource.NEUTRAL, 1.0f, 0.8f);
        if (pilot != null) DroneControl.cue(pilot, ModSounds.DRONE_CRASH.get(), 1.0f, 0.8f); // heard however far away it is
        discard();
    }

    public float laserHeat() {
        return laserHeat;
    }

    /**
     * Server: a laser's beam is on it this tick and heats it by {@code amount} (a share of what destroys it). It
     * smokes, then burns; at full heat something gives (a motor, the battery) and it is destroyed (an armed FPV's
     * charge goes off where it is). @return it was destroyed
     */
    public boolean lase(float amount) {
        if (isRemoved() || detonated || !(level() instanceof ServerLevel level)) return false;
        laserHeat = Math.min(1, laserHeat + amount);
        lasedTick = tickCount;
        double y = getY() + getBbHeight() / 2;
        if (laserHeat > 0.15f && tickCount % 3 == 0)
            level.sendParticles(laserHeat > 0.6f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, getX(), y, getZ(), 2, 0.08, 0.04, 0.08, 0.01);
        if (laserHeat > 0.7f && tickCount % 2 == 0) level.sendParticles(ParticleTypes.FLAME, getX(), y, getZ(), 1, 0.06, 0.03, 0.06, 0.005);
        if (laserHeat < 1) return false;
        crashDamage(Math.max(health, type().health()) + 1000);
        return true;
    }

    /** The payload went off: the drone is gone and nothing more may hit or detonate it. */
    public void markDetonated() {
        detonated = true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (armed() && motorsOn()) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.literal("Armed: land it first"));
            return InteractionResult.FAIL;
        }
        // someone else's drone: hands off (and never out from under whoever is flying it)
        if (owner != null && !owner.equals(player.getUUID())) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.literal("Not your drone"));
            return InteractionResult.FAIL;
        }
        if (pilotId() >= 0 && pilotId() != player.getId()) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.literal("Someone is flying it"));
            return InteractionResult.FAIL;
        }
        if (!(level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        DroneControl.release(this, true);
        ItemStack stack = DroneItem.stackFor(type(), battery());
        if (!player.getInventory().add(stack)) spawnAtLocation(level, stack);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.DRONE_BEEP.get(), SoundSource.NEUTRAL, 0.5f, 0.8f);
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) DroneControl.onRemoved(this);
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.drone(type()));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 192 * 192;
    }

    // ---- size, data, saving -------------------------------------------------------------------

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (entityData == null) return super.getDimensions(pose);
        DroneType t = type();
        return EntityDimensions.scalable(t.width(), t.height()).withEyeHeight(t.eyeHeight());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key)) refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, "");
        builder.define(PILOT, -1);
        builder.define(BATTERY, 1f);
        builder.define(SIGNAL, 0f);
        builder.define(LINKED, false);
        builder.define(RELAYED, false);
        builder.define(JAMMED, false);
        builder.define(THROTTLE, 0f);
        builder.define(CAM_PITCH, 0f);
        builder.define(ARM, -1);
        builder.define(ATTITUDE, new org.joml.Quaternionf());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) { // 26.x: ValueOutput replaces CompoundTag
        out.putString("drone", entityData.get(TYPE));
        if (owner != null) out.store("owner", UUIDUtil.CODEC, owner);
        out.putFloat("battery", battery());
        out.putFloat("health", health);
        out.putInt("arm", entityData.get(ARM));
        out.putFloat("throttle", throttle());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(TYPE, in.getStringOr("drone", ""));
        refreshDimensions();
        setAttitude(DroneFlight.level(getYRot())); // saved drones come back level, on their heading
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        setBattery(in.getFloatOr("battery", 1));
        health = in.getFloatOr("health", type().health());
        entityData.set(ARM, in.getIntOr("arm", -1));
        setThrottle(in.getFloatOr("throttle", 0));
    }

    // ---- GeckoLib (animations from tools/modelgen/guns/<id>.py: flight, armed; rotors and gimbal are set by the renderer)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // GeckoLib 5: controllers no longer take the animatable instance
        controllers.add(new AnimationController<DroneEntity>("lights", 0, s -> motorsOn() ? s.setAndContinue(FLIGHT) : PlayState.STOP));
        controllers.add(new AnimationController<DroneEntity>("arming", 0, s -> armed() ? s.setAndContinue(ARMED) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
