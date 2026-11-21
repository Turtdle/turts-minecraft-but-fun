package com.modernarmory.grenade;

import com.modernarmory.network.FlashbangPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * M84: a 170-180 dB bang and a million-candela flash, no fragments. Eyes and ears are hit apart
 * ({@link #exposure}): the flash only blinds someone who can see it, by how close it is and how directly they
 * face it (turned away, nothing); the bang rings everyone's ears by distance, a wall muffling it. Players get
 * a white-out and ringing ears (client.FlashbangOverlay); mobs are dazed (MobSenses: they lose their target and
 * stagger about), slowed and weakened.
 */
public final class FlashEffect implements GrenadeEffect {
    /** How far the bang carries (ears; and the glare that lights the room up around you). */
    static final double RANGE = 15;
    /** How far the flash can blind someone looking at it: a million-candela flash dazzles well beyond the bang. */
    static final double FLASH_RANGE = 40;
    /** Below this a flash or bang does nothing. */
    public static final float THRESHOLD = 0.03f;
    /** Seconds a flash of this strength whites you out (hold + fade): ~1 s for the weakest, 7 s point blank. */
    public static float whiteoutSeconds(float flash) {
        return 0.8f + 6.2f * Math.max(0, Math.min(1, flash));
    }

    /** Share of the bang a wall in between lets through. */
    static final float WALL_MUFFLE = 0.6f;
    /** How fast the flash falls off as you turn from it: (1 + cos angle)/2 to this power (side-on ~1/4). */
    static final double FACING_POWER = 2.0;

    /**
     * How hard the flash hit someone's eyes and the bang their ears, each 0..1, and whether they only caught its
     * glare ({@code glance}: in the open with the flash but turned away from it, so too weak to blind; the room
     * still lights up for an instant).
     */
    public record Exposure(float flash, float bang, boolean glance) {}

    @Override
    public void detonate(ServerLevel level, GrenadeEntity g) {
        Vec3 p = g.position().add(0, 0.2, 0);
        level.playSound(null, p.x, p.y, p.z, ModSounds.FLASHBANG.get(), SoundSource.PLAYERS, 4.0f, 1.0f);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 12, 0.2, 0.2, 0.2, 0.05);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(FLASH_RANGE))) {
            if (e.isSpectator()) continue;
            Exposure x = exposure(level, e, p);
            if (x.flash() < THRESHOLD && x.bang() < THRESHOLD) continue;
            if (e instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new FlashbangPayload(x.flash(), x.bang(), x.glance()));
                // blinded: everyone watching (and the player, in third person) sees a hand go over their eyes
                if (x.flash() >= THRESHOLD) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new com.modernarmory.network.FlashCoverPayload(player.getId(), whiteoutSeconds(x.flash())));
            } else if (e instanceof Mob mob) {
                float s = Math.max(x.flash(), 0.3f * x.bang()); // dazzled, or just deafened
                if (s < THRESHOLD) continue;
                int ticks = Math.round(40 + 160 * s);
                // dazed: no target, staggering about (MobSenses); slowed and weakened, no effect swirls
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
                MobSenses.daze(mob, ticks);
            }
        }
    }

    /**
     * Eyes: closer is worse, a wall stops it outright, and it scales with how directly you face it (looking at
     * it 1, side-on ~1/4, behind you 0). Ears: closer is worse whichever way you face; a wall muffles it.
     */
    public static Exposure exposure(ServerLevel level, LivingEntity e, Vec3 p) {
        Vec3 eye = e.getEyePosition();
        double d = eye.distanceTo(p);
        if (d > FLASH_RANGE) return new Exposure(0, 0, false);
        float near = d >= RANGE ? 0 : (float) Math.pow(1 - d / RANGE, 1.3);            // the bang
        float nearEyes = (float) Math.pow(1 - d / FLASH_RANGE, 1.3);                    // the flash
        // seen if the grenade or the glare just above it is in view (a lip of floor or a crate edge doesn't hide it)
        boolean visible = clear(level, e, eye, p) || clear(level, e, eye, p.add(0, 0.4, 0));
        double cos = d < 1e-3 ? 1 : e.getLookAngle().dot(p.subtract(eye).normalize());
        float facing = (float) Math.pow((1 + cos) / 2, FACING_POWER);
        float flash = visible ? Mth.clamp(nearEyes * facing * 1.25f, 0, 1) : 0;
        float bang = Mth.clamp(near * (visible ? 1 : WALL_MUFFLE), 0, 1);
        return new Exposure(flash, bang, visible && flash < THRESHOLD && bang >= THRESHOLD);
    }

    private static boolean clear(ServerLevel level, LivingEntity e, Vec3 eye, Vec3 to) {
        return level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e))
                .getType() == HitResult.Type.MISS;
    }
}
