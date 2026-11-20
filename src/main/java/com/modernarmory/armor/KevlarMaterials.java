package com.modernarmory.armor;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.modernarmory.ModernArmory;
import com.modernarmory.registry.ModItems;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One vanilla armour material per kevlar tier (1.21.1), built from the {@link Kevlar} catalog: the vanilla armour
 * points of each slot from that tier's pieces, the toughest piece's toughness, repaired with aramid fabric. The
 * looks come from the tier's GeckoLib model, not from the material's layer textures.
 */
public final class KevlarMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ModernArmory.MODID);
    private static final Map<String, DeferredHolder<ArmorMaterial, ArmorMaterial>> BY_MODEL = new LinkedHashMap<>();

    static {
        Map<String, List<ArmorPiece>> tiers = new LinkedHashMap<>();
        for (ArmorPiece p : Kevlar.all()) tiers.computeIfAbsent(p.model(), k -> new java.util.ArrayList<>()).add(p);
        tiers.forEach((model, pieces) -> BY_MODEL.put(model, MATERIALS.register(model, () -> {
            Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type t : ArmorItem.Type.values()) defense.put(t, 0);
            float toughness = 0;
            for (ArmorPiece p : pieces) {
                defense.put(type(p.slot()), p.defense());
                toughness = Math.max(toughness, p.toughness());
            }
            return new ArmorMaterial(defense, 10, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(ModItems.ARAMID_FABRIC.get()),
                    List.of(new ArmorMaterial.Layer(ModernArmory.id(model))), toughness, 0f);
        })));
    }

    /** The material of a tier model. */
    public static Holder<ArmorMaterial> of(String model) {
        return BY_MODEL.get(model);
    }

    /** The vanilla armour type worn in {@code slot}. */
    public static ArmorItem.Type type(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
            default -> throw new IllegalArgumentException("not an armour slot: " + slot);
        };
    }

    private KevlarMaterials() {}
}
