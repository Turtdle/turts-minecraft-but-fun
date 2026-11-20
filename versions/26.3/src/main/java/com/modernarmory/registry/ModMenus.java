package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.menu.GunsmithMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModernArmory.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GunsmithMenu>> GUNSMITH = MENUS.register("gunsmith",
            () -> new MenuType<>(GunsmithMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
