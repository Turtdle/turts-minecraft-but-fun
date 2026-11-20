package com.modernarmory.grenade;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * M7A3 CS: pours a pale cloud of tear gas (drawn like the M18's smoke) for {@value #BURN_TICKS} ticks.
 * Anyone inside is blinded, and the longer they have been in the gas, the longer it lasts once they get
 * out: {@value #BASE_TICKS} ticks plus {@value #PER_TICK_INSIDE} per tick spent inside, at most
 * {@value #MAX_TICKS}. Time in the gas adds up across clouds until the blindness it gave has worn off.
 */
final class TearGasEffect extends SmokeEffect {
    static final int BURN_TICKS = 600, BASE_TICKS = 40, MAX_TICKS = 600;
    static final float PER_TICK_INSIDE = 1.5f;
    /** CS smoke is a pale, slightly yellowish white. */
    private static final int COLOUR = 0xE4E4CF;

    /** Server: how long each entity has been in the gas (weak keys: gone with the entity). */
    private static final Map<LivingEntity, Exposure> EXPOSURE = new WeakHashMap<>();

    private static final class Exposure {
        int ticksInside;
        long lastInside = Long.MIN_VALUE / 2;
        int blindTicks;
    }

    TearGasEffect() {
        super(COLOUR, BURN_TICKS);
    }

    @Override
    public void burn(ServerLevel level, GrenadeEntity g, int tick) {
        super.burn(level, g, tick);
        double r = radius(tick);
        Vec3 p = g.position();
        AABB box = new AABB(p.x - r, p.y - 0.5, p.z - r, p.x + r, p.y + r + 1.5, p.z + r);
        long now = level.getGameTime();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz <= r * r) expose(e, now);
        }
    }

    /** {@code e} spent this tick in the gas: blinds it for as long as its time inside says. */
    private static void expose(LivingEntity e, long now) {
        Exposure x = EXPOSURE.computeIfAbsent(e, k -> new Exposure());
        if (x.lastInside == now) return; // standing in two clouds still counts once
        if (now - x.lastInside > x.blindTicks) x.ticksInside = 0; // the last dose wore off: start over
        x.ticksInside++;
        x.lastInside = now;
        x.blindTicks = blindTicks(x.ticksInside);
        // no effect swirls round the victim (ambient = false, visible = false), but the HUD icon shows
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, x.blindTicks, 0, false, false, true));
    }

    /** Ticks of blindness after {@code ticksInside} ticks in the gas. */
    static int blindTicks(int ticksInside) {
        return Math.min(MAX_TICKS, BASE_TICKS + Math.round(ticksInside * PER_TICK_INSIDE));
    }
}
