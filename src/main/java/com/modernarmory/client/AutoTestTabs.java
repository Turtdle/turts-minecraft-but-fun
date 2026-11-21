package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.modernarmory.ModernArmory;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModCreativeTabs;
import com.modernarmory.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Creative tabs ("tabs" section): builds the creative inventory and checks each Turt's Armory tab has items,
 * every gun is in exactly one tab (its category's), and every mod item is in some tab.
 */
final class AutoTestTabs {
    static final int LENGTH = 10;

    static void script(int t0) {
        at(t0 + 2, () -> {
            var level = Minecraft.getInstance().level;
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
            Map<Item, Integer> seen = new HashMap<>();
            boolean ok = true;
            StringBuilder sb = new StringBuilder();
            for (var h : ModCreativeTabs.TABS.getEntries()) {
                CreativeModeTab tab = h.get();
                int n = 0;
                for (ItemStack st : tab.getDisplayItems()) {
                    n++;
                    seen.merge(st.getItem(), 1, Integer::sum);
                    if (st.getItem() instanceof GunItem g && !h.getId().getPath().equals(g.spec().category().tabId)) {
                        ok = false;
                        sb.append(" misplaced:").append(h.getId().getPath()).append('/').append(st.getItem());
                    }
                }
                if (n == 0) ok = false;
                ModernArmory.LOGGER.info("[autotest] tabs: {} \"{}\" {} items", h.getId().getPath(), tab.getDisplayName().getString(), n);
            }
            Set<Item> missing = new HashSet<>();
            for (var e : ModItems.ITEMS.getEntries()) if (!seen.containsKey(e.get())) missing.add(e.get());
            for (var e : seen.entrySet())
                if (e.getKey() instanceof GunItem && e.getValue() != 1) { ok = false; sb.append(" gun in ").append(e.getValue()).append(" tabs:").append(e.getKey()); }
            ModernArmory.LOGGER.info("[autotest] tabs: {} tabs, items not in any tab: {}{} -> {}", ModCreativeTabs.TABS.getEntries().size(),
                    missing, sb, ok ? "PASS" : "FAIL");
        });
    }

    private AutoTestTabs() {}
}
