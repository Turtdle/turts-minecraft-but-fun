package com.modernarmory.grenade;

import com.modernarmory.Config;
import com.modernarmory.block.ThermiteFireBlock;
import com.modernarmory.registry.ModBlocks;
import com.modernarmory.registry.ModDamageTypes;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AN-M14 TH3: the thermite bursts at once into a patch of thermite fire ~7 m across (block.ThermiteFireBlock:
 * redder than vanilla fire, never spreads), on every solid spot the burst can reach (not through walls). It
 * burns for ~20 s and dies out raggedly. It is very hot: anything standing in that fire takes heavy thermite
 * damage that goes through armour (ModDamageTypes.THERMITE), but only while it stands there. Nothing else is
 * set alight (vanilla fire at the edge only with the config incendiarySetsBlocksAlight).
 */
final class IncendiaryEffect implements GrenadeEffect {
    private static final int BURN_TICKS = ThermiteFireBlock.BURN_TICKS;
    /** The patch is {@link #MAX_RADIUS} across from the start, and shrinks in the last {@link #DIE_TICKS} as its fire goes out. */
    private static final double START_RADIUS = 1.0, MAX_RADIUS = 3.5;
    private static final int DIE_TICKS = ThermiteFireBlock.BURN_JITTER;
    /** Damage per hit, one hit every {@link #HIT_EVERY} ticks (10 a second: a few seconds in it is deadly). */
    private static final float DAMAGE = 5f, BURST_DAMAGE = 4f;
    private static final int HIT_EVERY = 10;

    /** Radius of the burning patch {@code tick} ticks after it went off (the same on server and client). */
    static double radius(int tick) {
        int left = BURN_TICKS - tick;
        if (left < DIE_TICKS) return Mth.lerp(left / (double) DIE_TICKS, START_RADIUS * 0.5, MAX_RADIUS);
        return MAX_RADIUS;
    }

    @Override
    public void detonate(ServerLevel level, GrenadeEntity g) {
        level.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.6f, 0.7f);
        level.playSound(null, g.getX(), g.getY(), g.getZ(), ModSounds.THERMITE_BURN.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        igniteBurst(level, g);
        scorch(level, g, 2.0, BURST_DAMAGE, false);
    }

    /** The burst: thermite fire on every solid spot within {@link #MAX_RADIUS} that the grenade can see. */
    private static void igniteBurst(ServerLevel level, GrenadeEntity g) {
        Vec3 c = g.position().add(0, 0.3, 0);
        BlockPos centre = BlockPos.containing(c);
        BlockState fire = ModBlocks.THERMITE_FIRE.get().defaultBlockState();
        int r = (int) Math.ceil(MAX_RADIUS);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > MAX_RADIUS * MAX_RADIUS) continue;
                for (int dy = 1; dy >= -2; dy--) { // the highest spot in the column, following the ground
                    BlockPos pos = centre.offset(dx, dy, dz);
                    BlockState here = level.getBlockState(pos);
                    if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !fire.canSurvive(level, pos)) continue;
                    Vec3 to = Vec3.atBottomCenterOf(pos).add(0, 0.2, 0);
                    if (level.clip(new ClipContext(c, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, g)).getType() == HitResult.Type.MISS)
                        level.setBlockAndUpdate(pos, fire);
                    break;
                }
            }
    }

    @Override
    public int burnTicks() {
        return BURN_TICKS;
    }

    @Override
    public void burn(ServerLevel level, GrenadeEntity g, int tick) {
        if (tick % HIT_EVERY == HIT_EVERY - 1) scorch(level, g, MAX_RADIUS + 0.5, DAMAGE, true);
        if (tick % 40 == 39 && tick < BURN_TICKS - DIE_TICKS)
            level.playSound(null, g.getX(), g.getY(), g.getZ(), ModSounds.THERMITE_BURN.get(), SoundSource.PLAYERS, 1.0f, 0.95f + g.getRandom().nextFloat() * 0.1f);
        if (tick % 30 == 15 && Config.INCENDIARY_SETS_BLOCKS_ALIGHT.get()) {
            RandomSource r = g.getRandom();
            double a = r.nextDouble() * Math.PI * 2, d = radius(tick);
            igniteBlock(level, BlockPos.containing(g.getX() + Math.cos(a) * d, g.getY(), g.getZ() + Math.sin(a) * d));
        }
    }

    /**
     * Thermite damage to every living thing within {@code r} (horizontally) of the grenade, near its height;
     * with {@code inFireOnly}, only to those standing in thermite fire.
     */
    private static void scorch(ServerLevel level, GrenadeEntity g, double r, float damage, boolean inFireOnly) {
        Vec3 p = g.position();
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.THERMITE), g, g.getOwner());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(r, 2.0, r).move(0, 0.5, 0))) {
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz > r * r || e.getY() < p.y - 2.5) continue;
            if (inFireOnly && !level.getBlockState(e.blockPosition()).is(ModBlocks.THERMITE_FIRE.get())) continue;
            e.hurtServer(level, source, damage);
        }
    }

    private static void igniteBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP))
            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
    }

    @Override
    public void clientBurn(Level level, GrenadeEntity g, int tick) {
        RandomSource r = level.getRandom();
        Vec3 p = g.position();
        if (tick == 0) { // the burst: a ring of flame thrown out, and a pop
            level.addParticle(ParticleTypes.EXPLOSION, p.x, p.y + 0.3, p.z, 0, 0, 0);
            for (int i = 0; i < 140; i++) {
                double a = r.nextDouble() * Math.PI * 2, s = 0.12 + r.nextDouble() * 0.28;
                level.addParticle(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, Math.cos(a) * s, 0.04 + r.nextDouble() * 0.12, Math.sin(a) * s);
            }
        }
        double rad = radius(tick);
        // loose flames over the patch (its fire blocks are the body of it), thickest in the middle
        int n = (int) Math.round(4 * rad * rad / 4 + 3);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * rad;
            level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                    p.x + Math.cos(a) * d, p.y + 0.05 + r.nextDouble() * 0.15, p.z + Math.sin(a) * d,
                    (r.nextDouble() - 0.5) * 0.02, 0.02 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.02);
        }
        boolean dying = tick > BURN_TICKS - DIE_TICKS;
        if (!dying) {
            if (tick % 2 == 0) level.addParticle(ParticleTypes.LAVA, p.x, p.y + 0.2, p.z, 0, 0, 0);
            for (int i = 0; i < 3; i++) // white-hot sparks from the thermite
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.25, p.z,
                        (r.nextDouble() - 0.5) * 0.6, 0.2 + r.nextDouble() * 0.4, (r.nextDouble() - 0.5) * 0.6);
        }
        if (tick % 2 == 0) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * rad;
            level.addParticle(ParticleTypes.LARGE_SMOKE, p.x + Math.cos(a) * d, p.y + 0.6, p.z + Math.sin(a) * d, 0, 0.07, 0);
        }
    }
}
