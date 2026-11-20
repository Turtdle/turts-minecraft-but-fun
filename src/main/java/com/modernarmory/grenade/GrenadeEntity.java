package com.modernarmory.grenade;

import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown hand grenade: flies, bounces and rolls with simple real physics (gravity 9.81 m/s^2, a bounce
 * that keeps ~35% of the speed into the surface and ~70% along it, rolling to a stop), its fuze counting
 * from the throw. When the fuze runs out its {@link GrenadeEffect} detonates; smoke and thermite keep
 * burning (and rolling) for a while before the grenade is gone. The server simulates; clients get
 * positions every tick and only draw particles.
 */
public class GrenadeEntity extends Projectile {
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.STRING);
    /** -1 while the fuze is burning down, then ticks since it detonated (burning grenades only). */
    private static final EntityDataAccessor<Integer> BURN = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);

    private static final double G = 9.81, DT = 0.05, RESTITUTION = 0.35, FRICTION = 0.7;
    private static final int SUBSTEPS = 4;

    private int fuze;
    private Vec3 velocity = Vec3.ZERO; // m/s (1 block = 1 m)
    private boolean resting;
    /** Client: how it is turned as it flies and rolls (worked out from its motion). */
    private GrenadeTumble tumble;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    /** Throws {@code type} from {@code thrower}'s hand: {@link ThrowKind} sets the speed (20 m/s overhand) and loft. */
    public static GrenadeEntity throwFrom(LivingEntity thrower, GrenadeType type, ThrowKind kind) {
        GrenadeEntity g = new GrenadeEntity(ModEntities.GRENADE.get(), thrower.level());
        g.setOwner(thrower);
        g.entityData.set(TYPE, type.id());
        g.fuze = type.fuzeTicks();
        Vec3 look = thrower.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
        Vec3 start = thrower.getEyePosition().add(look.scale(0.4)).add(right.scale(0.25)).add(0, -0.15, 0);
        // every grenade leaves at the throw's speed whatever it weighs, so each one lands where you expect
        Vec3 dir = look.add(0, kind.loft, 0).normalize();
        g.velocity = dir.scale(kind.speed).add(thrower.getDeltaMovement().scale(20));
        g.setPos(start);
        g.setDeltaMovement(g.velocity.scale(DT));
        thrower.level().addFreshEntity(g);
        return g;
    }

    /** Client: the grenade's tumble and roll for rendering. */
    public GrenadeTumble tumble() {
        if (tumble == null) tumble = new GrenadeTumble(getId());
        return tumble;
    }

    public GrenadeType type() {
        GrenadeType t = Grenades.byId(entityData.get(TYPE));
        return t != null ? t : Grenades.M67;
    }

    /** -1 while the fuze burns, else ticks since it went off. */
    public int burnTicks() {
        return entityData.get(BURN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, "");
        builder.define(BURN, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tumble().tick(level(), position(), getDeltaMovement());
            int burn = burnTicks();
            if (burn >= 0) type().effect().clientBurn(level(), this, burn);
            return;
        }
        if (!resting) step();
        else if (level().getBlockState(BlockPos.containing(position().add(0, -0.1, 0))).isAir()) resting = false;
        ServerLevel level = (ServerLevel) level();
        GrenadeEffect effect = type().effect();
        int burn = burnTicks();
        if (burn < 0) {
            if (--fuze <= 0) {
                effect.detonate(level, this);
                if (effect.burnTicks() > 0) entityData.set(BURN, 0);
                else discard();
            }
        } else {
            effect.burn(level, this, burn);
            if (burn + 1 >= effect.burnTicks()) discard();
            else entityData.set(BURN, burn + 1);
        }
    }

    /** One tick of flight: gravity, a little air drag, and bounces off blocks. */
    private void step() {
        Vec3 pos = position(), v = velocity;
        double dt = DT / SUBSTEPS;
        for (int i = 0; i < SUBSTEPS; i++) {
            v = v.add(0, -G * dt, 0).scale(1 - 0.05 * dt);
            Vec3 next = pos.add(v.scale(dt));
            BlockHitResult hit = level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.BLOCK) {
                pos = next;
                continue;
            }
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            double vn = v.dot(n);
            if (vn < 0) {
                if (-vn > 2.0) level().playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                        ModSounds.GRENADE_BOUNCE.get(), SoundSource.PLAYERS, (float) Mth.clamp(-vn / 10, 0.2, 1.0),
                        0.9f + random.nextFloat() * 0.2f);
                Vec3 tangential = v.subtract(n.scale(vn));
                v = tangential.scale(FRICTION).add(n.scale(-vn * RESTITUTION));
            }
            pos = hit.getLocation().add(n.scale(0.03));
            if (n.y > 0.7 && v.length() < 0.6) {
                v = Vec3.ZERO;
                resting = true;
                break;
            }
        }
        if (isInWater()) v = v.scale(0.75);
        velocity = v;
        setPos(pos);
        setDeltaMovement(v.scale(DT));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("grenade", entityData.get(TYPE));
        tag.putInt("fuze", fuze);
        tag.putInt("burn", burnTicks());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, tag.getString("grenade"));
        fuze = tag.getInt("fuze");
        entityData.set(BURN, tag.contains("burn") ? tag.getInt("burn") : -1);
    }
}
