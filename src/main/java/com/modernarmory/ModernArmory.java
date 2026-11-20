package com.modernarmory;

import org.slf4j.Logger;

import com.modernarmory.registry.ModBlocks;
import com.modernarmory.registry.ModCreativeTabs;
import com.modernarmory.registry.ModMenus;
import com.modernarmory.registry.ModDataComponents;
import com.modernarmory.registry.ModEntities;
import com.modernarmory.registry.ModItems;
import com.modernarmory.registry.ModPlayerData;
import com.modernarmory.registry.ModSounds;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ModernArmory.MODID)
public class ModernArmory {
    public static final String MODID = "modernarmory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModernArmory(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        com.modernarmory.registry.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.modernarmory.armor.KevlarMaterials.MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModPlayerData.TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
