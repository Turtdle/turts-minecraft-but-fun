package com.modernarmory.armor;

import java.util.List;

import com.modernarmory.ballistics.ArmorLevel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What worn armour does to a bullet (server side). Bullets ignore vanilla armour points (damage type tag
 * minecraft:bypasses_armor); instead the piece covering the body zone that was hit decides:
 * <ul>
 *   <li><b>stopped</b> (its level stops the round): only {@link ArmorPiece#blunt} of the damage gets through as
 *       blunt trauma, and the piece wears by about the round's damage;</li>
 *   <li><b>through</b> (the round is above its level): the round loses {@value #LOSS_PER_LEVEL} of its damage per
 *       level of the armour it went through, and the piece wears by half again;</li>
 *   <li><b>vanilla armour</b>: leather does nothing, netherite is a Level III plate (like the light military
 *       tier), every other material takes a slight {@value #VANILLA_TAKES} off.</li>
 * </ul>
 * A round that has lost most of its energy ({@value #SPENT_ENERGY} or less of the muzzle's) counts one level easier
 * to stop.
 */
public final class BallisticArmor {
    /** Damage lost per armour level a round goes through (IIIA 10%, III 20%, IV 30%, juggernaut 40%). */
    static final float LOSS_PER_LEVEL = 0.10f;
    /** Share of the damage that vanilla armour (not leather, not netherite) takes off. */
    static final float VANILLA_TAKES = 0.10f;
    /** Below this share of its muzzle energy a round is one level easier to stop. */
    static final double SPENT_ENERGY = 0.5;
    /** Wear on a piece that a round went through, per point of the round's damage. */
    static final float WEAR_THROUGH = 1.5f;

    /** Netherite: a Level III plate, as tough as the light military plates. */
    static final ArmorPiece NETHERITE = vanilla("netherite", ArmorLevel.III, 0.18f);

    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    public enum Outcome { NONE, VANILLA, THROUGH, STOPPED }

    /** A hit after armour: the damage that reaches the body, what happened, and which piece took it. */
    public record Hit(float damage, Outcome outcome, ArmorPiece piece) {}

    /**
     * Resolves a bullet of {@code damage} that hit {@code target} in {@code zone}; {@code stoppedBy} is what the
     * round needs to be stopped at full power, {@code energyShare} the share of its muzzle energy it still has.
     * Wears the piece that took it (it may break).
     */
    public static Hit resolve(LivingEntity target, BodyZone zone, ArmorLevel stoppedBy, double energyShare, float damage) {
        ArmorLevel needed = energyShare <= SPENT_ENERGY ? stoppedBy.weaker() : stoppedBy;
        ItemStack best = ItemStack.EMPTY;
        ArmorPiece bestPiece = null;
        EquipmentSlot bestSlot = null;
        boolean vanilla = false;
        EquipmentSlot vanillaSlot = null;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ArmorPiece p = pieceOf(stack, slot, zone);
            if (p != null) {
                if (bestPiece == null || p.level().ordinal() > bestPiece.level().ordinal()) {
                    best = stack;
                    bestPiece = p;
                    bestSlot = slot;
                }
            } else if (slot == zone.slot && isVanillaArmor(stack)) {
                vanilla = true;
                vanillaSlot = slot;
            }
        }
        if (bestPiece != null) {
            if (bestPiece.level().stops(needed)) {
                wear(target, best, bestSlot, damage);
                return new Hit(damage * bestPiece.blunt(), Outcome.STOPPED, bestPiece);
            }
            wear(target, best, bestSlot, damage * WEAR_THROUGH);
            return new Hit(damage * (1 - LOSS_PER_LEVEL * bestPiece.level().ordinal()), Outcome.THROUGH, bestPiece);
        }
        if (vanilla) {
            wear(target, target.getItemBySlot(vanillaSlot), vanillaSlot, damage);
            return new Hit(damage * (1 - VANILLA_TAKES), Outcome.VANILLA, null);
        }
        return new Hit(damage, Outcome.NONE, null);
    }

    /**
     * The impact sound of a hit on armour: soft aramid (IIIA) or a hard plate (Level III and up, netherite, the bomb
     * suit, other vanilla metal armour); null when no armour was hit.
     */
    public static net.minecraft.sounds.SoundEvent sound(Hit hit) {
        return switch (hit.outcome()) {
            case NONE -> null;
            case VANILLA -> com.modernarmory.registry.ModSounds.ARMOR_HIT_PLATE.get();
            case STOPPED, THROUGH -> (hit.piece().level() == ArmorLevel.IIIA
                    ? com.modernarmory.registry.ModSounds.ARMOR_HIT_SOFT : com.modernarmory.registry.ModSounds.ARMOR_HIT_PLATE).get();
        };
    }

    /** The ballistic piece {@code stack} (worn in {@code slot}) is for zone {@code zone}, or null. */
    static ArmorPiece pieceOf(ItemStack stack, EquipmentSlot slot, BodyZone zone) {
        if (stack.getItem() instanceof KevlarWearable k) return k.piece().covers(zone) ? k.piece() : null;
        if (slot == zone.slot && path(stack).startsWith("netherite_") && isVanillaArmor(stack)) return NETHERITE;
        return null;
    }

    /** Vanilla-style armour (helmet / chestplate / leggings / boots), not leather: takes a little off bullets. */
    static boolean isVanillaArmor(ItemStack stack) {
        String p = path(stack);
        if (p.startsWith("leather_")) return false;
        return p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots");
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static void wear(LivingEntity target, ItemStack stack, EquipmentSlot slot, float amount) {
        if (stack.isDamageableItem()) stack.hurtAndBreak(Math.max(1, Math.round(amount)), target, slot);
    }

    private static ArmorPiece vanilla(String id, ArmorLevel level, float blunt) {
        return new ArmorPiece(id, id, "", EquipmentSlot.CHEST, java.util.EnumSet.allOf(BodyZone.class), level, blunt,
                0, 0, 0, 0, 0, false, "vanilla " + id + " armour", 0);
    }

    private BallisticArmor() {}
}
