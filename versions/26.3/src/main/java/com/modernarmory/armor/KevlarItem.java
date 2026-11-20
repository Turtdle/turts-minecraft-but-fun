package com.modernarmory.armor;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.modernarmory.ModernArmory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;

/**
 * A piece of body armour from the {@link Kevlar} catalog (26.x): worn through the humanoid-armour equippable
 * component with its tier's armour points, its own durability, a walking-speed penalty by weight, drawn with the
 * tier's GeckoLib model. What it does to bullets is in {@link BallisticArmor}.
 */
public class KevlarItem extends Item implements GeoItem, KevlarWearable {
    private final ArmorPiece piece;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public KevlarItem(ArmorPiece piece, Properties properties) {
        super(configure(piece, properties));
        this.piece = piece;
    }

    /** Armour component, the piece's durability, and the tier's armour points plus the weight penalty. */
    private static Properties configure(ArmorPiece piece, Properties p) {
        ArmorMaterial m = KevlarMaterials.of(piece.model());
        ArmorType t = KevlarMaterials.type(piece.slot());
        return p.humanoidArmor(m, t).durability(piece.durability())
                .attributes(m.createAttributes(t).withModifierAdded(Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(ModernArmory.id("armor_weight." + piece.slot().getName()), -piece.slowdown(),
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                        EquipmentSlotGroup.bySlot(piece.slot())));
    }

    @Override
    public ArmorPiece piece() {
        return piece;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        for (String line : piece.tooltip()) tooltip.accept(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    // ---- GeckoLib 5: the tier's model (tools/modelgen/armor/<model>.py), no animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private @Nullable GeoArmorRenderer<KevlarItem, HumanoidRenderState> renderer;

            @Override
            public @Nullable GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
                if (renderer == null)
                    renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<KevlarItem>(ModernArmory.id(piece.model())));
                return renderer;
            }
        });
    }
}
