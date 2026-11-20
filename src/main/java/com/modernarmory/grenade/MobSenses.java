package com.modernarmory.grenade;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * What grenades do to mobs' senses (server side).
 *
 * <p><b>Smoke:</b> every burning smoke (or tear gas) cloud is registered here each tick. A mob can't pick a
 * target it would have to look at through {@value #OPAQUE_M} m or more of smoke, and drops the one it has once
 * the smoke comes between them, so it stops chasing and a skeleton stops shooting. Within {@value #TOUCH_M} m it
 * still finds you by feel. The cloud is taken as a cylinder of its radius and up to a radius tall (where the
 * particles are), thinning with the smoke over its last seconds.
 *
 * <p><b>Flash:</b> a flashed mob is dazed for a while ({@link #daze}): it can't take a target, and it staggers
 * about to random spots nearby, turning this way and that.
 */
@EventBusSubscriber(modid = com.modernarmory.ModernArmory.MODID)
public final class MobSenses {
    /** Metres of smoke a mob can't see through. */
    static final double OPAQUE_M = 2.0;
    /** Closer than this a mob finds its target whatever the smoke. */
    static final double TOUCH_M = 2.0;
    /** Step along a line of sight when measuring the smoke on it. */
    private static final double STEP_M = 0.25;
    /** A dazed mob picks a new spot to stagger to every so many ticks, this far away at most, at this speed. */
    private static final int STAGGER_EVERY = 20;
    private static final double STAGGER_RANGE = 4.0, STAGGER_SPEED = 0.8;
    /** A mob with a target re-checks the smoke between them every so many ticks. */
    private static final int SIGHT_CHECK_EVERY = 5;

    /** One burning cloud: ground centre, radius, how thick it still is (1 = full, fading to 0), last tick seen. */
    private record Cloud(Vec3 base, double radius, float density, long seen) {
        boolean contains(Vec3 p) {
            double dx = p.x - base.x, dz = p.z - base.z;
            return p.y >= base.y - 0.5 && p.y <= base.y + radius + 0.5 && dx * dx + dz * dz <= radius * radius;
        }
    }

    private static final Map<ServerLevel, Map<Integer, Cloud>> CLOUDS = new WeakHashMap<>();
    /** Dazed mobs and the game tick their daze ends (weak keys: gone with the mob). */
    private static final Map<Mob, Long> DAZED = new WeakHashMap<>();

    /** The cloud of grenade {@code g} this tick: {@code radius} wide, {@code density} 0..1 thick. */
    static void smoke(ServerLevel level, GrenadeEntity g, double radius, float density) {
        CLOUDS.computeIfAbsent(level, k -> new HashMap<>())
                .put(g.getId(), new Cloud(g.position(), radius, density, level.getGameTime()));
    }

    /** Dazes {@code mob} for {@code ticks}: it loses its target and staggers about. */
    static void daze(Mob mob, int ticks) {
        long until = mob.level().getGameTime() + ticks;
        DAZED.merge(mob, until, Math::max);
        forget(mob);
    }

    /** Whether {@code mob} is dazed right now. */
    public static boolean dazed(Mob mob) {
        Long until = DAZED.get(mob);
        return until != null && mob.level().getGameTime() < until;
    }

    /** Metres of smoke (weighted by how thick it is) on the line from {@code a} to {@code b}. */
    public static double smokeBetween(ServerLevel level, Vec3 a, Vec3 b) {
        Map<Integer, Cloud> clouds = CLOUDS.get(level);
        if (clouds == null || clouds.isEmpty()) return 0;
        long now = level.getGameTime();
        clouds.values().removeIf(c -> now - c.seen() > 2); // burnt out or picked up
        if (clouds.isEmpty()) return 0;
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len / STEP_M));
        double step = len / n, total = 0;
        for (int i = 0; i < n; i++) {
            Vec3 p = a.lerp(b, (i + 0.5) / n);
            float thickest = 0;
            for (Cloud c : clouds.values()) if (c.density() > thickest && c.contains(p)) thickest = c.density();
            total += thickest * step;
        }
        return total;
    }

    /** Whether smoke hides {@code target} from {@code mob}. */
    public static boolean smokeHides(Mob mob, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        Vec3 eye = mob.getEyePosition(), to = target.getEyePosition();
        return eye.distanceTo(to) > TOUCH_M && smokeBetween(level, eye, to) >= OPAQUE_M;
    }

    @SubscribeEvent
    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return; // losing a target is always allowed
        if (dazed(mob) || smokeHides(mob, target)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onMobTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!DAZED.isEmpty()) {
            Long until = DAZED.get(mob);
            if (until != null) {
                if (level.getGameTime() >= until) DAZED.remove(mob);
                else {
                    stagger(mob);
                    return;
                }
            }
        }
        if ((mob.tickCount + mob.getId()) % SIGHT_CHECK_EVERY != 0) return;
        LivingEntity target = mob.getTarget();
        if (target != null && smokeHides(mob, target)) forget(mob);
    }

    /** Drops the mob's target, goal-driven or brain-driven. */
    private static void forget(Mob mob) {
        mob.setTarget(null);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /** One tick of a dazed mob: no target, lurching to a random spot now and then, head swinging about. */
    private static void stagger(Mob mob) {
        if (mob.getTarget() != null) forget(mob);
        RandomSource r = mob.getRandom();
        if ((mob.tickCount + mob.getId()) % STAGGER_EVERY == 0) {
            double a = r.nextDouble() * Math.PI * 2, d = 1.5 + r.nextDouble() * (STAGGER_RANGE - 1.5);
            mob.getNavigation().moveTo(mob.getX() + Math.cos(a) * d, mob.getY(), mob.getZ() + Math.sin(a) * d, STAGGER_SPEED);
        }
        if (r.nextInt(6) == 0) {
            float yaw = mob.getYRot() + (r.nextFloat() - 0.5f) * 70f;
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setXRot((r.nextFloat() - 0.5f) * 40f);
        }
    }

    private MobSenses() {}
}
