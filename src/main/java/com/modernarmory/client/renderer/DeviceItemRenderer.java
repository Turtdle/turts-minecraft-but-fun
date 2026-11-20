package com.modernarmory.client.renderer;

import com.modernarmory.ModernArmory;

import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * A handheld device (packed drones, the drone controller) in the hand: its GeckoLib model with the
 * first-person arms drawn in the player's skin (ArmedItemRenderer), or on its own in the inventory / on the
 * ground. View class DEVICE (view_classes.json).
 */
public class DeviceItemRenderer<T extends Item & GeoItem> extends ArmedItemRenderer<T> {
    public DeviceItemRenderer(T item, String modelId) {
        super(new DefaultedItemGeoModel<>(ModernArmory.id(modelId)));
    }
}
