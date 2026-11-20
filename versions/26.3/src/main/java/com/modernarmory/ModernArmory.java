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

import net.minecraft.resources.Identifier;
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
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModPlayerData.TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    // 26.x renamed ResourceLocation to Identifier.
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
