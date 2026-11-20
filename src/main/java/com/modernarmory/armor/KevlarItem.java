package com.modernarmory.armor;

import java.util.List;
import java.util.function.Consumer;

import com.modernarmory.ModernArmory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A piece of body armour from the {@link Kevlar} catalog (1.21.1): vanilla armour points from its tier's
 * material, its own durability, a walking-speed penalty by weight, drawn with the tier's GeckoLib model.
 * What it does to bullets is in {@link BallisticArmor}.
 */
public class KevlarItem extends ArmorItem implements GeoItem, KevlarWearable {
    private final ArmorPiece piece;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ItemAttributeModifiers modifiers;

    public KevlarItem(ArmorPiece piece, Properties properties) {
        super(KevlarMaterials.of(piece.model()), KevlarMaterials.type(piece.slot()), properties.durability(piece.durability()));
        this.piece = piece;
    }

    @Override
    public ArmorPiece piece() {
        return piece;
    }

    /** The material's armour points plus the weight: a share of walking speed off while worn. */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        if (modifiers == null) {
            modifiers = super.getDefaultAttributeModifiers().withModifierAdded(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(ModernArmory.id("armor_weight." + piece.slot().getName()), -piece.slowdown(),
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    EquipmentSlotGroup.bySlot(piece.slot()));
        }
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (String line : piece.tooltip()) tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    // ---- GeckoLib: the tier's model (tools/modelgen/armor/<model>.py), no animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<KevlarItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T entity, ItemStack stack, EquipmentSlot slot,
                                                                                 HumanoidModel<T> original) {
                if (renderer == null) renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<>(ModernArmory.id(piece.model())));
                return renderer;
            }
        });
    }
}
