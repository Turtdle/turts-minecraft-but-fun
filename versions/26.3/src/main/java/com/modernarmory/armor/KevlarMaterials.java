package com.modernarmory.armor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.modernarmory.ModernArmory;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * One armour material per kevlar tier (26.x: plain records, no registry), built from the {@link Kevlar} catalog:
 * the vanilla armour points of each slot from that tier's pieces, the toughest piece's toughness, repaired with
 * anything tagged modernarmory:repairs_kevlar (aramid fabric). The looks come from the tier's GeckoLib model.
 */
public final class KevlarMaterials {
    /** Items that repair kevlar in an anvil. */
    public static final TagKey<Item> REPAIRS_KEVLAR = TagKey.create(Registries.ITEM, ModernArmory.id("repairs_kevlar"));
    private static final Map<String, ArmorMaterial> BY_MODEL = new LinkedHashMap<>();

    static {
        Map<String, List<ArmorPiece>> tiers = new LinkedHashMap<>();
        for (ArmorPiece p : Kevlar.all()) tiers.computeIfAbsent(p.model(), k -> new ArrayList<>()).add(p);
        tiers.forEach((model, pieces) -> {
            Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
            for (ArmorType t : ArmorType.values()) defense.put(t, 0);
            float toughness = 0;
            for (ArmorPiece p : pieces) {
                defense.put(type(p.slot()), p.defense());
                toughness = Math.max(toughness, p.toughness());
            }
            // durability base unused: every piece sets its own (KevlarItem)
            BY_MODEL.put(model, new ArmorMaterial(15, defense, 10, SoundEvents.ARMOR_EQUIP_GENERIC, toughness, 0f, REPAIRS_KEVLAR,
                    ResourceKey.create(EquipmentAssets.ROOT_ID, ModernArmory.id(model))));
        });
    }

    /** The material of a tier model. */
    public static ArmorMaterial of(String model) {
        return BY_MODEL.get(model);
    }

    /** The armour type worn in {@code slot}. */
    public static ArmorType type(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorType.HELMET;
            case CHEST -> ArmorType.CHESTPLATE;
            case LEGS -> ArmorType.LEGGINGS;
            case FEET -> ArmorType.BOOTS;
            default -> throw new IllegalArgumentException("not an armour slot: " + slot);
        };
    }

    private KevlarMaterials() {}
}
