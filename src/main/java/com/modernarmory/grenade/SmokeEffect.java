package com.modernarmory.grenade;

import org.joml.Vector3f;

import com.modernarmory.registry.ModSounds;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * M18: pours smoke for about a minute. The cloud grows to ~10 m across and ~5 m tall, thick enough to hide
 * whoever is behind it, in a few shades of the smoke's colour so it has depth; it thins out over the last
 * seconds. Mobs can't see through it either ({@link MobSenses}).
 */
class SmokeEffect implements GrenadeEffect {
    private static final int BURN_TICKS = 1200;
    private final int burnTicks;
    private static final double MAX_RADIUS = 5.0;
    /** Fine dust particles per tick at full strength, and big slow puffs (campfire smoke) for body. */
    private static final int DUST_PER_TICK = 80, PUFFS_PER_TICK = 3;
    private final DustParticleOptions[] shades;

    SmokeEffect(int rgb) {
        this(rgb, BURN_TICKS);
    }

    /** A cloud of colour {@code rgb} that pours for {@code burnTicks} ticks. */
    SmokeEffect(int rgb, int burnTicks) {
        this.burnTicks = burnTicks;
        Vector3f c = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        this.shades = new DustParticleOptions[] { // darker, as is, lighter
                new DustParticleOptions(new Vector3f(c).mul(0.8f), 4.0f),
                new DustParticleOptions(c, 4.0f),
                new DustParticleOptions(new Vector3f(c).mul(0.85f).add(0.15f, 0.15f, 0.15f), 4.0f)};
    }

    @Override
    public void detonate(ServerLevel level, GrenadeEntity g) {
        level.playSound(null, g.getX(), g.getY(), g.getZ(), ModSounds.SMOKE_HISS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public int burnTicks() {
        return burnTicks;
    }

    /** How thick the cloud still is, 1 down to 0 over its last 10 s. */
    private float fade(int tick) {
        return tick > burnTicks - 200 ? (burnTicks - tick) / 200f : 1f;
    }

    /** The cloud's radius {@code tick} ticks after it started pouring. */
    protected double radius(int tick) {
        return Math.min(MAX_RADIUS, 0.4 + tick / 30.0);
    }

    @Override
    public void burn(ServerLevel level, GrenadeEntity g, int tick) {
        MobSenses.smoke(level, g, radius(tick), fade(tick));
        if (tick % 40 == 39 && tick < burnTicks - 100)
            level.playSound(null, g.getX(), g.getY(), g.getZ(), ModSounds.SMOKE_HISS.get(), SoundSource.PLAYERS, 0.8f, 0.95f + g.getRandom().nextFloat() * 0.1f);
    }

    @Override
    public void clientBurn(Level level, GrenadeEntity g, int tick) {
        RandomSource r = level.getRandom();
        float fade = fade(tick);
        double radius = radius(tick);
        Vec3 p = g.position();
        // the jet out of the top
        for (int i = 0; i < 4; i++)
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.2, p.z,
                    (r.nextDouble() - 0.5) * 0.08, 0.03 + r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.08);
        // the cloud around it: fine dust in three shades, densest low down
        int n = Math.round(DUST_PER_TICK * fade);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            double h = Math.pow(r.nextDouble(), 1.6) * radius * 1.0;
            level.addParticle(shades[r.nextInt(shades.length)], p.x + Math.cos(a) * d, p.y + 0.2 + h, p.z + Math.sin(a) * d,
                    0, 0.01, 0);
        }
        // big slow puffs through the cloud for body
        int puffs = Math.round(PUFFS_PER_TICK * fade);
        for (int i = 0; i < puffs; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius * 0.9;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + Math.cos(a) * d, p.y + 0.3 + r.nextDouble() * radius * 0.6,
                    p.z + Math.sin(a) * d, (r.nextDouble() - 0.5) * 0.01, 0.004, (r.nextDouble() - 0.5) * 0.01);
        }
    }
}
