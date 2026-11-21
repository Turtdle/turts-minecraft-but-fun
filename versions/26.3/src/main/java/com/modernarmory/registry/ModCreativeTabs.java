package com.modernarmory.registry;

import java.util.function.Supplier;

import com.modernarmory.ModernArmory;
import com.modernarmory.gun.GunCategory;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative inventory tabs, right after vanilla's Combat tab: one per kind of gun ({@link GunCategory}, in its
 * order, guns in catalog order), then one "Gear" tab with everything else (Gunsmith Table, ammo, attachments,
 * grenades, body armour, drones and the laser turret). Titles are lang keys itemGroup.modernarmory.&lt;id&gt;.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModernArmory.MODID);

    /** The tab the next one goes after. */
    private static ResourceKey<CreativeModeTab> last = CreativeModeTabs.COMBAT;

    static {
        for (GunCategory c : GunCategory.values()) {
            tab(c.tabId, () -> ModItems.guns().values().stream().filter(i -> i.get().spec().category() == c)
                            .findFirst().map(i -> i.get().getDefaultInstance()).orElse(ItemStack.EMPTY),
                    (params, output) -> ModItems.guns().values().forEach(i -> {
                        if (i.get().spec().category() == c) output.accept(i.get());
                    }));
        }
        // everything that isn't a gun, in one tab: crafting, ammo, attachments, grenades, armour, drones
        tab("gear", () -> ModItems.grenades().values().iterator().next().get().getDefaultInstance(), (params, output) -> {
            output.accept(ModItems.GUNSMITH_TABLE.get());
            output.accept(ModItems.STEEL_INGOT.get());
            ModItems.ammo().forEach((id, i) -> {
                output.accept(i.get());
                var tracer = ModItems.tracerAmmo().get(id); // the tracer variant right after its ball round
                if (tracer != null) output.accept(tracer.get());
            });
            ModItems.ATTACHMENTS.values().forEach(i -> output.accept(i.get()));
            ModItems.grenades().values().forEach(i -> output.accept(i.get()));
            ModItems.kevlar().values().forEach(i -> output.accept(i.get()));
            output.accept(ModItems.ARAMID_FABRIC.get());
            output.accept(ModItems.RIFLE_PLATE.get());
            output.accept(ModItems.CERAMIC_PLATE.get());
            ModItems.drones().values().forEach(i -> output.accept(i.get()));
            output.accept(ModItems.DRONE_CONTROLLER.get());
            output.accept(ModItems.DRONE_RELAY.get());
            output.accept(ModItems.LASER_TURRET.get());
        });
    }

    private static void tab(String id, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator items) {
        ResourceKey<CreativeModeTab> before = last;
        DeferredHolder<CreativeModeTab, CreativeModeTab> h = TABS.register(id, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.modernarmory." + id))
                .withTabsBefore(before)
                .icon(icon)
                .displayItems(items)
                .build());
        last = h.getKey();
    }

    private ModCreativeTabs() {}
}
