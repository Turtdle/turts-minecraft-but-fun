package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.shot;

import java.util.HashSet;
import java.util.Set;

import com.modernarmory.ModernArmory;
import com.modernarmory.item.GunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory icons follow the attachments ("icons" section): the hotbar gets the same M4 bare, with a red dot,
 * with suppressor + ACOG + grip, with a scope + bipod + 40-round PMAG, and a few other guns kitted out; each
 * loadout must get its own icon (GunIcons), and the inventory is photographed.
 */
final class AutoTestIcons {
    static final int LENGTH = 40;
    private static final String[] HOTBAR = {
            "m4",
            "m4[modernarmory:attachments={OPTIC:\"red_dot\"}]",
            "m4[modernarmory:attachments={OPTIC:\"acog\",MUZZLE:\"suppressor_556\",UNDERBARREL:\"vertical_grip\"}]",
            "m4[modernarmory:attachments={OPTIC:\"sniper_scope\",UNDERBARREL:\"bipod\",MAGAZINE:\"pmag_40\"}]",
            "m4[modernarmory:attachments={OPTIC:\"holographic\",MUZZLE:\"compensator_556\",SIDE:\"laser\",STOCK:\"stock_ctr\"}]",
            "ak47",
            "awm",
            "glock17",
            "m590",
    };

    static void script(int t0) {
        Minecraft mc = Minecraft.getInstance();
        at(t0 + 1, () -> {
            cmd("gamemode creative"); cmd("clear @s");
            for (String g : HOTBAR) cmd("give @s modernarmory:" + g);
        });
        at(t0 + 8, () -> shot("icons_hotbar"));
        at(t0 + 10, () -> mc.setScreen(new InventoryScreen(mc.player)));
        at(t0 + 16, () -> shot("icons_inventory"));
        at(t0 + 18, () -> {
            Set<Object> textures = new HashSet<>();
            int guns = 0;
            for (int i = 0; i < 5; i++) { // the five M4 loadouts
                ItemStack st = mc.player.getInventory().getItem(i);
                if (st.getItem() instanceof GunItem g) {
                    guns++;
                    textures.add(GunIcons.texture(g, st));
                }
            }
            boolean ok = guns == 5 && textures.size() == 5;
            ModernArmory.LOGGER.info("[autotest] icons: {} M4 loadouts, {} different icons, {} painted so far -> {}",
                    guns, textures.size(), GunIcons.painted, ok ? "PASS" : "FAIL");
        });
        at(t0 + 20, () -> mc.setScreen(null));
    }

    private AutoTestIcons() {}
}
