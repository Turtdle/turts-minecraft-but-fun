package com.modernarmory.client.renderer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.model.DefaultedItemGeoModel;
import com.modernarmory.ModernArmory;

import net.minecraft.world.item.Item;

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
