package com.modernarmory.entity;

import com.modernarmory.Config;
import com.modernarmory.ballistics.G7Drag;
import com.modernarmory.ballistics.HitboxHistory;
import com.modernarmory.ballistics.LagCompensation;
import com.modernarmory.ballistics.Penetration;
import com.modernarmory.ballistics.ProjectileSpec;
import com.modernarmory.registry.ModDamageTypes;
import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModSounds;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.PositionAndRotation;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * A real ballistic projectile (1 block = 1 m).
 *
 * <p>Each game tick (50 ms) is split into 10 sub-steps. In each sub-step the bullet:
 * <ul>
 *   <li>slows down from aerodynamic drag, using the G7 drag curve and the cartridge's ballistic
 *       coefficient. Drag is solved exactly (v' = v / (1 + k v dt)), so it stays stable even in
 *       water, where it stops within a metre or two;</li>
 *   <li>falls under gravity (9.81 m/s^2);</li>
 *   <li>is ray-traced against blocks and entities along the exact path, so it can't tunnel
 *       through anything despite moving ~44 blocks per tick.</li>
 * </ul>
 * Impacts: damage scales with remaining kinetic energy (with a bonus above the fragmentation
 * velocity, and for headshots). Bullets penetrate thin or soft blocks, losing energy, shatter
 * glass, and ricochet off stone or metal at grazing angles.
 *
 * <p>The server is authoritative for damage. Clients run the same simulation from the exact
 * spawn state for tracers, impact effects and the supersonic crack of near misses, so no
 * position packets are needed.
 */
public class BulletEntity extends Projectile implements IEntityWithComplexSpawn {
    private static final double TICK = 0.05;
    private static final int SUBSTEPS = 10;
    private static final double DT = TICK / SUBSTEPS;
    private static final double GRAVITY = 9.81;
    private static final double MIN_SPEED = 40.0;
    private static final int MAX_LIFE_TICKS = 100;
    /** For the autotest: ricochets on the server since startup, and the most any one projectile made. */
    public static int serverRicochets, serverMostBounces;

    private Vec3 velocity = Vec3.ZERO; // m/s
    private double massKg = 0.004;
    private double bcG7 = 0.15;
    private double fragmentationVelocity;
    private double muzzleEnergy = 1;
    private float baseDamage;
    /** Least headshot damage at muzzle energy (ProjectileSpec.headshotDamage); 0 = none. */
    private float headshotFloor;
    /** The lowest body-armour level that stops it at full power (ProjectileSpec.stoppedBy). */
    private com.modernarmory.ballistics.ArmorLevel stoppedBy = com.modernarmory.ballistics.ArmorLevel.IIIA;
    /** What armour did to the most recent hit (read by tests). */
    private com.modernarmory.armor.BallisticArmor.Outcome lastArmor = com.modernarmory.armor.BallisticArmor.Outcome.NONE;
    private double distance;
    /** Tracer glow colour 0xRRGGBB, or -1 for a ball round; burnout distance (m). Synced at spawn. */
    private int tracerRgb = -1;
    private com.modernarmory.ballistics.Ricochet ricochet = com.modernarmory.ballistics.Ricochet.BULLET;
    private int bounces;
    private double tracerBurnout;
    /** Client only: muzzle minus eye at spawn, so the tracer can start at the gun (client.TracerOrigin). */
    private Vec3 tracerOffset = Vec3.ZERO;
    private boolean inWater;
    private boolean crackPlayed;
    private String gunId;
    private boolean lastHeadshot;
    /** Server: ticks to rewind targets by (lag compensation, LagCompensation); 0 = test them where they are. */
    private int rewind;
    /** Server: entity hits since start (the hit-registration autotest reads it). */
    public static int entityHits;
    /**
     * Server, set by GunHandler while it fires a client-predicted shot: the shooter's client already flies
     * its own copy of these bullets (client.ShotPredictor), so they are only sent to everyone else.
     */
    public static boolean shooterPredicts;
    /** Server: not sent to the shooter, whose client predicted it (see {@link #shooterPredicts}). */
    private boolean predicted;

    /** Item id of the gun that fired this bullet (server side), or null. */
    public String gunId() {
        return gunId;
    }

    /** What armour did to the most recent entity hit: nothing, vanilla armour, through kevlar, stopped by it. */
    public com.modernarmory.armor.BallisticArmor.Outcome lastArmor() {
        return lastArmor;
    }

    /** Whether the most recent entity hit was a headshot (read by mastery on kills). */
    public boolean lastHitWasHeadshot() {
        return lastHeadshot;
    }
    private final IntOpenHashSet alreadyHit = new IntOpenHashSet();

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Fires one projectile (a bullet or one pellet) from {@code origin} along {@code direction}.
     *
     * @param speed  muzzle velocity, m/s (with attachment effects)
     * @param gunId  the gun item that fired it (for mastery XP on kills); null = unknown
     */
    public static BulletEntity fire(Level level, LivingEntity shooter, Vec3 origin, Vec3 direction, ProjectileSpec projectile,
                                    double speed, String gunId, com.modernarmory.ballistics.Tracer tracer) {
        BulletEntity b = new BulletEntity(ModEntities.BULLET.get(), level);
        b.setOwner(shooter);
        b.gunId = gunId;
        if (tracer != null) {
            b.tracerRgb = tracer.rgb();
            b.tracerBurnout = tracer.burnoutM();
        }
        b.setPos(origin);
        b.velocity = direction.normalize().scale(speed);
        b.massKg = projectile.massKg();
        b.bcG7 = projectile.bcG7();
        b.fragmentationVelocity = projectile.fragmentationVelocity();
        b.muzzleEnergy = projectile.referenceEnergy();
        b.baseDamage = projectile.baseDamage();
        b.headshotFloor = projectile.headshotDamage();
        b.stoppedBy = projectile.stoppedBy();
        b.ricochet = projectile.ricochet();
        b.setDeltaMovement(b.velocity.scale(TICK));
        b.updateRotation();
        if (gunId != null) b.rewind = LagCompensation.ticks(shooter); // gunfire is aimed at what the shooter saw
        if (level.isClientSide()) {
            // the shooter's own prediction: a client-only copy that flies, hits and leaves holes like the real one
            b.tracerOffset = ClientAccess.tracerOffset(shooter, b.velocity);
            ClientAccess.addPredicted(b);
        } else {
            b.predicted = shooterPredicts;
            level.addFreshEntity(b);
        }
        return b;
    }

    /** A predicted shot's real bullets go to everyone but the shooter, who sees their own copy. */
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) {
        return !(predicted && player == getOwner()) && super.broadcastToPlayer(player);
    }

    // ------------------------------------------------------------------ simulation

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > MAX_LIFE_TICKS) {
            this.discard();
            return;
        }
        Vec3 pos = this.position();
        for (int i = 0; i < SUBSTEPS; i++) {
            pos = step(pos);
            if (pos == null) {
                this.discard();
                return;
            }
        }
        this.setPos(pos);
        this.setDeltaMovement(this.velocity.scale(TICK));
        this.updateRotation();
    }

    /** Advance one sub-step. Returns the new position, or null if the bullet stopped. */
    private Vec3 step(Vec3 pos) {
        double speed = velocity.length();
        if (speed < MIN_SPEED) return null;
        // Entities only tick inside the simulation distance. A bullet that flew beyond it would freeze
        // in place forever (still loaded and tracked), then reappear as a stray tracer whenever a
        // player came close again. Despawn it the moment it leaves ticking chunks instead.
        if (!level().isClientSide() && (!level().hasChunkAt(BlockPos.containing(pos))
                || !((net.minecraft.server.level.ServerLevel) level()).isPositionEntityTicking(BlockPos.containing(pos)))) return null;

        boolean water = level().getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (water && !inWater && level().isClientSide()) splash(pos);
        inWater = water;

        // Exact solution of dv/dt = -k v^2 over DT with k from the G7 curve at this speed.
        double k = G7Drag.dragK(speed, bcG7, water ? G7Drag.WATER_DENSITY : G7Drag.AIR_DENSITY);
        double kvdt = k * speed * DT;
        double newSpeed = speed / (1.0 + kvdt);
        double along = Math.log1p(kvdt) / k;
        Vec3 dir = velocity.scale(1.0 / speed);
        Vec3 next = pos.add(dir.scale(along)).add(0, -0.5 * GRAVITY * DT * DT, 0);
        velocity = dir.scale(newSpeed).add(0, -GRAVITY * DT, 0);

        return trace(pos, next);
    }

    /** Ray-trace pos -> next, resolving hits. Returns where the bullet ends up, or null if stopped. */
    private Vec3 trace(Vec3 from, Vec3 to) {
        for (int guard = 0; guard < 8; guard++) {
            BlockHitResult blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
            Vec3 segmentEnd = hitBlock ? blockHit.getLocation() : to;

            EntityHitResult entityHit = entityHit(from, segmentEnd);
            if (entityHit != null) {
                alreadyHit.add(entityHit.getEntity().getId());
                distance += from.distanceTo(entityHit.getLocation());
                hitEntity(entityHit.getEntity(), entityHit.getLocation());
                return null; // bullets dump their energy into whatever they hit
            }

            if (!hitBlock) {
                nearMissCheck(from, to);
                distance += from.distanceTo(to);
                return to;
            }

            distance += from.distanceTo(blockHit.getLocation());
            Vec3[] next = hitBlock(blockHit, from, to);
            if (next == null) return null;
            from = next[0];
            to = next[1];
        }
        return to;
    }

    /**
     * The first entity on from -> to. On the server, gunfire tests each target's hitbox as it was
     * {@link #rewind} ticks ago (HitboxHistory): where the shooter's client drew it when they fired.
     */
    private EntityHitResult entityHit(Vec3 from, Vec3 to) {
        // targets may have moved since: search wide enough to find where they were
        AABB search = new AABB(from, to).inflate(1.0 + 0.6 * rewind);
        Entity best = null;
        Vec3 bestAt = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level().getEntities(this, search, this::canHitEntity)) {
            AABB box = rewind > 0 ? HitboxHistory.boxAt(e, rewind) : e.getBoundingBox();
            Vec3 at = box.contains(from) ? from : box.clip(from, to).orElse(null);
            if (at == null) continue;
            double d = from.distanceToSqr(at);
            if (d < bestD) {
                best = e;
                bestAt = at;
                bestD = d;
            }
        }
        return best == null ? null : new EntityHitResult(best, bestAt);
    }

    // ------------------------------------------------------------------ impacts

    private void hitEntity(Entity target, Vec3 hitPos) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        double speed = velocity.length();
        double energy = 0.5 * massKg * speed * speed;
        float damage = com.modernarmory.ballistics.DamageModel.atImpact(baseDamage, energy, muzzleEnergy);
        if (fragmentationVelocity > 0 && speed >= fragmentationVelocity) damage *= 1.25f;
        // judged on the box the bullet was tested against (rewound), so a headshot is where the shooter saw the head
        com.modernarmory.armor.BodyZone zone = target instanceof LivingEntity living
                ? com.modernarmory.armor.BodyZone.of(living, HitboxHistory.boxAt(living, rewind), hitPos.y) : null;
        lastHeadshot = zone == com.modernarmory.armor.BodyZone.HEAD;
        if (lastHeadshot) {
            damage = com.modernarmory.ballistics.DamageModel.headshot(damage, Config.HEADSHOT_MULTIPLIER.get().floatValue(),
                    headshotFloor, energy, muzzleEnergy);
        }
        damage *= Config.DAMAGE_MULTIPLIER.get().floatValue();
        // the armour over the spot that was hit: stops it (blunt trauma only), slows it, or isn't there
        lastArmor = com.modernarmory.armor.BallisticArmor.Outcome.NONE;
        net.minecraft.sounds.SoundEvent armorSound = null;
        if (zone != null) {
            var armored = com.modernarmory.armor.BallisticArmor.resolve((LivingEntity) target, zone, stoppedBy,
                    energy / muzzleEnergy, damage);
            damage = armored.damage();
            lastArmor = armored.outcome();
            armorSound = com.modernarmory.armor.BallisticArmor.sound(armored);
            // everyone near hears the armour take it, where it hit (the shooter gets it as their hit marker instead)
            if (armorSound != null)
                level().playSound(getOwner() instanceof net.minecraft.world.entity.player.Player shooter ? shooter : null,
                        hitPos.x, hitPos.y, hitPos.z, armorSound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f,
                        0.92f + getRandom().nextFloat() * 0.16f);
        }

        DamageSource source = new DamageSource(serverLevel.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BulletDodge.damageType(target, serverLevel.getGameTime())), this, getOwner());
        // (bullet damage bypasses the hurt cooldown: tag bypasses_cooldown)
        if (target.hurtServer(serverLevel, source, damage)) {
            entityHits++;
            if (gunId != null) com.modernarmory.server.HitMarkers.onHit(getOwner(), target, lastHeadshot,
                    lastArmor == com.modernarmory.armor.BallisticArmor.Outcome.STOPPED ? armorSound : null);
        }
    }

    /**
     * Resolves a block impact. Returns the next {from, to} segment if the bullet continues
     * (penetration or ricochet), or null if it stopped.
     */
    private Vec3[] hitBlock(BlockHitResult hit, Vec3 from, Vec3 to) {
        BlockPos bp = hit.getBlockPos();
        BlockState state = level().getBlockState(bp);
        float hardness = state.getDestroySpeed(level(), bp);
        Vec3 at = hit.getLocation();
        double speed = velocity.length();
        Vec3 dir = velocity.scale(1.0 / speed);
        Direction face = hit.getDirection();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double remaining = Math.max(0, to.distanceTo(at));

        // Ricochet: grazing hits on hard surfaces skip off, losing ~45% of their speed.
        double grazeDeg = 90.0 - Math.toDegrees(Math.acos(Mth.clamp(-dir.dot(normal), -1, 1)));
        if (Penetration.canRicochet(state, hardness) && grazeDeg < ricochet.maxGrazeDeg() && bounces < ricochet.maxBounces() && speed > 150) {
            double kept = ricochet.speedAfter(grazeDeg);
            bounces++;
            if (!level().isClientSide()) {
                serverRicochets++;
                serverMostBounces = Math.max(serverMostBounces, bounces);
            }
            Vec3 reflected = dir.subtract(normal.scale(2 * dir.dot(normal)));
            reflected = reflected.add((random.nextDouble() - 0.5) * 0.08, random.nextDouble() * 0.04,
                    (random.nextDouble() - 0.5) * 0.08).normalize();
            velocity = reflected.scale(speed * kept);
            if (level().isClientSide()) impactEffects(state, bp, at, face, normal, true);
            Vec3 start = at.add(normal.scale(0.01));
            return new Vec3[] {start, start.add(reflected.scale(remaining * kept))};
        }

        // Penetration: energy cost = material resistance x thickness along the path.
        double resistance = Penetration.resistance(state, hardness);
        if (Double.isFinite(resistance)) {
            VoxelShape shape = state.getCollisionShape(level(), bp);
            BlockHitResult exit = shape.clip(at.add(dir.scale(2.0)), at, bp);
            Vec3 exitPos = exit != null ? exit.getLocation() : at.add(dir);
            double thickness = exitPos.distanceTo(at);
            double cost = resistance * thickness;
            double energy = 0.5 * massKg * speed * speed;
            if (cost < energy) {
                velocity = dir.scale(Math.sqrt(2 * (energy - cost) / massKg));
                if (!level().isClientSide() && Penetration.shatters(state) && Config.BULLETS_BREAK_GLASS.get()) {
                    level().destroyBlock(bp, false, getOwner());
                } else if (level().isClientSide()) {
                    impactEffects(state, bp, at, face, normal, false);
                    Direction exitFace = exit != null ? exit.getDirection() : Direction.getApproximateNearest(dir);
                    impactEffects(state, bp, exitPos, exitFace, dir, false); // the exit hole
                }
                Vec3 start = exitPos.add(dir.scale(0.002));
                return new Vec3[] {start, start.add(dir.scale(Math.max(0, remaining - thickness)))};
            }
        }

        if (level().isClientSide()) impactEffects(state, bp, at, face, normal, false);
        return null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !alreadyHit.contains(target.getId()) && super.canHitEntity(target);
    }

    // ------------------------------------------------------------------ client effects

    /** Bullet-hole width in blocks: a little bigger for heavier bullets (.50 BMG), the same for every pellet. */
    private double holeSize() {
        return Math.min(0.4, 0.22 + massKg * 4);
    }

    private void impactEffects(BlockState state, BlockPos pos, Vec3 at, Direction face, Vec3 normal, boolean ricochet) {
        Level level = level();
        BulletImpacts.impact(level, pos, state, at, face, holeSize(), ricochet);
        materialEffects(level, ImpactMaterial.of(state), state, at, normal);
        for (int i = 0; i < 8; i++) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                    normal.x * 0.15 + (random.nextDouble() - 0.5) * 0.15,
                    normal.y * 0.15 + random.nextDouble() * 0.1,
                    normal.z * 0.15 + (random.nextDouble() - 0.5) * 0.15);
        }
        level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, normal.x * 0.02, 0.02, normal.z * 0.02);
        if (ricochet) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, normal.x * 0.3 + (random.nextDouble() - 0.5) * 0.3,
                        normal.y * 0.3 + random.nextDouble() * 0.2, normal.z * 0.3 + (random.nextDouble() - 0.5) * 0.3);
            }
            level.playLocalSound(at.x, at.y, at.z, ModSounds.RICOCHET.get(), SoundSource.PLAYERS, 0.8f,
                    0.85f + random.nextFloat() * 0.3f, false);
        } else {
            level.playLocalSound(at.x, at.y, at.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.7f,
                    1.2f + random.nextFloat() * 0.3f, false);
        }
    }

    /** What the material throws off on top of the block crumbs: sparks off metal, splinters off wood, dust off stone. */
    private void materialEffects(Level level, ImpactMaterial m, BlockState state, Vec3 at, Vec3 n) {
        if (m == null) return;
        switch (m) {
            case METAL -> {
                for (int i = 0; i < 7; i++)
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, n.x * 0.25 + (random.nextDouble() - 0.5) * 0.5,
                            n.y * 0.25 + random.nextDouble() * 0.3, n.z * 0.25 + (random.nextDouble() - 0.5) * 0.5);
                level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, n.x * 0.2, 0.1, n.z * 0.2);
            }
            case WOOD -> {
                for (int i = 0; i < 6; i++) // splinters: bigger, faster crumbs thrown well out
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                            n.x * 0.35 + (random.nextDouble() - 0.5) * 0.3, n.y * 0.35 + random.nextDouble() * 0.25,
                            n.z * 0.35 + (random.nextDouble() - 0.5) * 0.3);
            }
            case STONE -> {
                level.addParticle(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), at.x, at.y, at.z,
                        n.x * 0.05, 0.05, n.z * 0.05);
                for (int i = 0; i < 2; i++)
                    level.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, n.x * 0.04 + (random.nextDouble() - 0.5) * 0.02,
                            0.02, n.z * 0.04 + (random.nextDouble() - 0.5) * 0.02);
            }
            case EARTH -> {
                for (int i = 0; i < 6; i++) // a puff of dirt thrown up
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                            n.x * 0.1 + (random.nextDouble() - 0.5) * 0.15, 0.15 + random.nextDouble() * 0.15,
                            n.z * 0.1 + (random.nextDouble() - 0.5) * 0.15);
            }
            case GLASS -> {
                for (int i = 0; i < 8; i++)
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                            (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.3);
            }
            case FABRIC -> { }
        }
    }

    private void splash(Vec3 at) {
        for (int i = 0; i < 6; i++) {
            level().addParticle(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, (random.nextDouble() - 0.5) * 0.2, 0.3, (random.nextDouble() - 0.5) * 0.2);
            level().addParticle(ParticleTypes.BUBBLE, at.x, at.y - 0.2, at.z, 0, -0.1, 0);
        }
    }

    /** Supersonic bullets passing close to the local player make an audible "crack". */
    private void nearMissCheck(Vec3 from, Vec3 to) {
        if (!level().isClientSide() || crackPlayed || velocity.lengthSqr() < G7Drag.SPEED_OF_SOUND * G7Drag.SPEED_OF_SOUND) return;
        Player local = ClientAccess.localPlayer();
        if (local == null || local == getOwner()) return;
        Vec3 ear = local.getEyePosition();
        Vec3 seg = to.subtract(from);
        double t = Mth.clamp(ear.subtract(from).dot(seg) / Math.max(seg.lengthSqr(), 1e-9), 0, 1);
        Vec3 closest = from.add(seg.scale(t));
        double miss = closest.distanceTo(ear);
        if (miss < 4.0) {
            crackPlayed = true;
            level().playLocalSound(closest.x, closest.y, closest.z, ModSounds.BULLET_CRACK.get(), SoundSource.PLAYERS,
                    (float) (1.0 - miss / 5.0), 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    // ------------------------------------------------------------------ rendering helpers

    public Vec3 velocity() {
        return velocity;
    }

    /** Tracer glow colour (0xRRGGBB) while it is still burning, else -1 (ball, or burnt out). */
    public int tracerGlow() {
        return tracerRgb >= 0 && distance < tracerBurnout ? tracerRgb : -1;
    }

    /** Client only: where the tracer starts relative to the bullet's true (eye) path; fades with distance. */
    public Vec3 tracerOffset() {
        return tracerOffset;
    }

    /** Metres flown so far (used to grow the tracer streak from the muzzle). */
    public double distance() {
        return distance;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 256.0 * 256.0;
    }

    // ------------------------------------------------------------------ networking

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(velocity.x);
        buf.writeDouble(velocity.y);
        buf.writeDouble(velocity.z);
        buf.writeDouble(massKg);
        buf.writeDouble(bcG7);
        Entity owner = getOwner();
        buf.writeVarInt(owner != null ? owner.getId() : -1);
        buf.writeVarInt(this.tickCount);
        buf.writeInt(tracerRgb);
        buf.writeDouble(tracerBurnout);
        buf.writeVarInt(Math.min(ricochet.maxBounces(), 1_000_000));
        buf.writeDouble(ricochet.maxGrazeDeg());
        buf.writeDouble(ricochet.speedKept());
        buf.writeDouble(ricochet.speedKeptMax());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        velocity = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        massKg = buf.readDouble();
        bcG7 = buf.readDouble();
        int ownerId = buf.readVarInt();
        this.tickCount = buf.readVarInt(); // age on the server: stale bullets expire immediately
        tracerRgb = buf.readInt();
        tracerBurnout = buf.readDouble();
        int maxBounces = buf.readVarInt();
        ricochet = new com.modernarmory.ballistics.Ricochet(maxBounces >= 1_000_000 ? Integer.MAX_VALUE : maxBounces,
                buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (ownerId >= 0) setOwner(level().getEntity(ownerId));
        tracerOffset = ClientAccess.tracerOffset(getOwner(), velocity);
        setDeltaMovement(velocity.scale(TICK));
        updateRotation();
        this.xRotO = getXRot();
        this.yRotO = getYRot();
    }

    /**
     * Clients fly the bullet themselves; ignore server position corrections. (26.x replaced
     * Entity.lerpTo with an InterpolationHandler: claiming every update as handled drops it.)
     */
    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return IGNORE_POSITION_UPDATES;
    }

    private static final InterpolationHandler IGNORE_POSITION_UPDATES = new InterpolationHandler() {
        @Override
        public @Nullable PositionAndRotation target() {
            return null;
        }

        @Override
        public boolean interpolateTo(@Nullable PositionPath position, float yRot, float xRot, boolean hasRotation) {
            return true;
        }

        @Override
        public void interpolate() {}

        @Override
        public void applyPredictedMovement(Vec3 delta) {}

        @Override
        public boolean hasActiveInterpolation() {
            return false;
        }

        @Override
        public void cancel() {}
    };

    @Override
    public void lerpMotion(Vec3 movement) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean isPickable() {
        return false;
    }
}
