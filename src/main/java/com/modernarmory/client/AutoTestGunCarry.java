package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.shot;

import java.util.function.Consumer;

import com.modernarmory.ModernArmory;
import com.modernarmory.gun.GunCarry;
import com.modernarmory.item.GunItem;
import com.modernarmory.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Gun carry limit ("guncarry" section), in survival: three guns with no vest leaves two (one dropped); in an IOTV
 * three stay, and two long guns slow you down; a fourth gun at your feet isn't picked up; taking the vest off drops
 * one and lifts the slowdown. Then the inventory with the gun highlight on is photographed.
 */
final class AutoTestGunCarry {
    static final int LENGTH = 130;
    private static volatile boolean a, b, c, d;
    private static volatile String la, lb, lc, ld;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("gamemode survival"); cmd("clear @s"); cmd("kill @e[type=item]");
            cmd("effect give @s resistance 99999 4 true");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        // 1) no vest: two guns
        at(t0 + 4, () -> { cmd("give @s modernarmory:m4"); cmd("give @s modernarmory:m590"); cmd("give @s modernarmory:glock17"); });
        at(t0 + 30, () -> server(sp -> {
            int n = GunCarry.count(sp), dropped = guns(sp);
            a = n == 2 && dropped >= 1;
            la = "no vest: carrying " + n + "/" + GunCarry.limit(sp) + ", " + dropped + " dropped";
        }));
        // 2) in an IOTV: three, and two long guns slow you
        at(t0 + 32, () -> { cmd("kill @e[type=item]"); cmd("clear @s"); });
        at(t0 + 34, () -> server(sp -> sp.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.kevlar().get("iotv").get()))));
        at(t0 + 36, () -> { cmd("give @s modernarmory:m4"); cmd("give @s modernarmory:m590"); cmd("give @s modernarmory:glock17"); });
        at(t0 + 60, () -> server(sp -> {
            int n = GunCarry.count(sp);
            b = n == 3 && guns(sp) == 0 && GunCarry.loaded(sp);
            lb = "IOTV: carrying " + n + "/" + GunCarry.limit(sp) + ", main arms " + GunCarry.mainArms(sp) + ", slowed " + GunCarry.loaded(sp);
        }));
        // 3) a fourth gun at your feet stays there
        at(t0 + 62, () -> cmd("summon item ~ ~ ~ {Item:{id:\"modernarmory:mp5\",count:1},PickupDelay:0s}"));
        at(t0 + 90, () -> server(sp -> {
            int n = GunCarry.count(sp), left = guns(sp);
            c = n == 3 && left == 1;
            lc = "fourth gun at feet: carrying " + n + ", " + left + " still on the ground";
        }));
        // 4) vest off: one drops, no slowdown
        at(t0 + 92, () -> server(sp -> { killItems(sp); sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); }));
        at(t0 + 110, () -> server(sp -> {
            int n = GunCarry.count(sp);
            d = n == 2 && guns(sp) >= 1 && !GunCarry.loaded(sp);
            ld = "vest off: carrying " + n + "/" + GunCarry.limit(sp) + ", slowed " + GunCarry.loaded(sp);
            ModernArmory.LOGGER.info("[autotest] guncarry: {}; {}; {}; {} -> {}", la, lb, lc, ld, a && b && c && d ? "PASS" : "FAIL");
        }));
        // the highlight
        at(t0 + 112, () -> { GunSlotHighlight.forceActive = true; Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)); });
        at(t0 + 120, () -> shot("64_guncarry_inventory"));
        at(t0 + 122, () -> {
            GunSlotHighlight.forceActive = false; Minecraft.getInstance().setScreen(null);
            cmd("clear @s"); cmd("kill @e[type=item]"); cmd("effect clear @s"); cmd("gamemode creative");
        });
    }

    /** Gun items lying on the ground near the player. */
    private static int guns(ServerPlayer sp) {
        return sp.level().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(8), e -> e.getItem().getItem() instanceof GunItem).size();
    }

    private static void killItems(ServerPlayer sp) {
        sp.level().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(8)).forEach(e -> e.discard());
    }

    private static void server(Consumer<ServerPlayer> job) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> job.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private AutoTestGunCarry() {}
}
