package com.modernarmory.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Where a bullet hit a body, judged from the height of the hit on the target's hitbox, in the proportions of
 * the player model (legs the bottom 12 of 32 px, feet the bottom 4, the head from just under the eyes up).
 * Each zone has the armour slot that normally covers it.
 */
public enum BodyZone {
    HEAD(EquipmentSlot.HEAD),
    TORSO(EquipmentSlot.CHEST),
    LEGS(EquipmentSlot.LEGS),
    FEET(EquipmentSlot.FEET);

    /** How far under the eyes the head starts (blocks): the headshot line. */
    public static final double HEAD_BELOW_EYES = 0.25;
    /** Share of the body's height that is legs (12 of 32 px) and feet (4 of 32). */
    static final double LEGS_TOP = 12.0 / 32, FEET_TOP = 4.0 / 32;

    /** The armour slot that covers this zone by default (vanilla armour, kevlar of that slot). */
    public final EquipmentSlot slot;

    BodyZone(EquipmentSlot slot) {
        this.slot = slot;
    }

    /** The zone hit at height {@code y} on {@code target}, whose hitbox was {@code box} when it was hit. */
    public static BodyZone of(LivingEntity target, AABB box, double y) {
        if (y >= box.minY + target.getEyeHeight() - HEAD_BELOW_EYES) return HEAD;
        double rel = (y - box.minY) / Math.max(1e-3, box.maxY - box.minY);
        if (rel >= LEGS_TOP) return TORSO;
        return rel >= FEET_TOP ? LEGS : FEET;
    }
}
