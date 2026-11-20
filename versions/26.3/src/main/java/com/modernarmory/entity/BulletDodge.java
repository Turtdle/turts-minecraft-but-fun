package com.modernarmory.entity;

import java.util.Map;
import java.util.WeakHashMap;

import com.modernarmory.Config;
import com.modernarmory.ModernArmory;
import com.modernarmory.registry.ModDamageTypes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Mobs that teleport away from projectiles (endermen; entity type tag
 * {@code modernarmory:dodges_first_bullet}) dodge the first bullet: it is dealt as a projectile, so
 * vanilla makes them teleport. For a while after that (config bulletDodgeCooldownSeconds) bullets
 * use {@link ModDamageTypes#BULLET_DIRECT}, which is not a projectile, so they hit and anger the mob.
 * Server side only; the memory is per loaded entity and is not saved.
 */
public final class BulletDodge {
    public static final TagKey<EntityType<?>> DODGES_FIRST_BULLET =
            TagKey.create(Registries.ENTITY_TYPE, ModernArmory.id("dodges_first_bullet"));

    private static final Map<Entity, Long> LAST_DODGE = new WeakHashMap<>();

    /** The damage type a bullet hitting this target now should use (records the dodge). */
    public static ResourceKey<DamageType> damageType(Entity target, long gameTime) {
        // 26.x: EntityType has no is(TagKey); check the registry holder
        if (!target.getType().builtInRegistryHolder().is(DODGES_FIRST_BULLET)) return ModDamageTypes.BULLET;
        long cooldownTicks = Config.BULLET_DODGE_COOLDOWN_SECONDS.get() * 20L;
        Long last = LAST_DODGE.get(target);
        if (last != null && gameTime - last < cooldownTicks) return ModDamageTypes.BULLET_DIRECT;
        LAST_DODGE.put(target, gameTime);
        return ModDamageTypes.BULLET;
    }

    private BulletDodge() {}
}
