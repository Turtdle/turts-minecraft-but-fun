package com.modernarmory.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Heavy armour: nobody sprints in a bomb suit. The walking-speed penalty itself is each piece's attribute
 * modifier (KevlarItem); this stops sprinting while any piece that is too heavy for it is worn (both sides, so
 * the client never starts the sprint animation).
 */
@EventBusSubscriber(modid = com.modernarmory.ModernArmory.MODID)
public final class ArmorWeight {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Whether {@code e} wears a piece too heavy to sprint in. */
    public static boolean tooHeavyToSprint(LivingEntity e) {
        for (EquipmentSlot s : SLOTS)
            if (e.getItemBySlot(s).getItem() instanceof KevlarWearable k && k.piece().noSprint()) return true;
        return false;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.isSprinting() && tooHeavyToSprint(p)) p.setSprinting(false);
    }

    private ArmorWeight() {}
}
