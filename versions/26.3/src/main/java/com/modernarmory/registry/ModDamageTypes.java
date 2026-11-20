package com.modernarmory.registry;

import com.modernarmory.ModernArmory;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Data-driven damage types: data/modernarmory/damage_type/*.json (+ vanilla tags). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, ModernArmory.id("bullet"));
    /** A bullet that is not a projectile: endermen can't teleport away from it (see entity.BulletDodge). */
    public static final ResourceKey<DamageType> BULLET_DIRECT = ResourceKey.create(Registries.DAMAGE_TYPE, ModernArmory.id("bullet_direct"));

    /** AN-M14 thermite: very hot, goes through armour, no knockback, and does not set you burning. */
    public static final ResourceKey<DamageType> THERMITE = ResourceKey.create(Registries.DAMAGE_TYPE, ModernArmory.id("thermite"));

    private ModDamageTypes() {}
}
