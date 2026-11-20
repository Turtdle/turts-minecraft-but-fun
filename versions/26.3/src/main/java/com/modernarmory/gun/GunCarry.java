package com.modernarmory.gun;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.modernarmory.ModernArmory;
import com.modernarmory.armor.KevlarWearable;
import com.modernarmory.item.GunItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * How many guns a player can carry: {@value #BASE_GUNS}, plus whatever their armour adds (a heavy load-bearing
 * vest, tier 3 or 4, carries a third: armor.ArmorPiece#extraGuns). Every gun counts, pistols too. A player at the
 * limit can't pick up another gun; one over it (from a chest, crafting, taking the vest off) drops the extras at
 * their feet, keeping the gun in hand first, then the hotbar in order. Two or more main arms (long guns) carried in
 * a heavy vest is a heavy load: {@value #LOADED_SLOWDOWN} off walking speed. Creative and spectator players are
 * exempt.
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class GunCarry {
    /** Guns anyone can carry. */
    public static final int BASE_GUNS = 2;
    /** Walking speed lost carrying two or more main arms in a heavy vest. */
    public static final double LOADED_SLOWDOWN = 0.25;
    /** How often (ticks) the inventory is checked for guns over the limit. */
    private static final int CHECK_EVERY = 10;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final net.minecraft.resources.Identifier LOAD_ID = ModernArmory.id("gun_load");
    /** Last tick each player was told about the limit (so the message doesn't spam). */
    private static final Map<Player, Long> TOLD = new WeakHashMap<>();

    /** Guns {@code p} may carry. */
    public static int limit(Player p) {
        int extra = 0;
        for (EquipmentSlot s : ARMOR)
            if (p.getItemBySlot(s).getItem() instanceof KevlarWearable k) extra += k.piece().extraGuns();
        return BASE_GUNS + extra;
    }

    /** Whether {@code p} wears armour that lets them carry an extra gun (a tier 3 / 4 vest). */
    public static boolean heavyVest(Player p) {
        return limit(p) > BASE_GUNS;
    }

    /** Inventory slots holding guns, in keeping order: the gun in hand, the hotbar left to right, the rest. */
    public static List<Integer> gunSlots(Player p) {
        Inventory inv = p.getInventory();
        ItemStack hand = p.getMainHandItem();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).getItem() instanceof GunItem && inv.getItem(i) == hand) slots.add(i);
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).getItem() instanceof GunItem && !slots.contains(i)) slots.add(i);
        return slots;
    }

    /** Guns {@code p} carries. */
    public static int count(Player p) {
        return gunSlots(p).size();
    }

    /** Main arms (long guns) {@code p} carries. */
    public static int mainArms(Player p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i : gunSlots(p))
            if (inv.getItem(i).getItem() instanceof GunItem g && g.spec().category().mainArm()) n++;
        return n;
    }

    private static boolean exempt(Player p) {
        return p.isCreative() || p.isSpectator();
    }

    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player p = event.getPlayer();
        if (exempt(p) || !(event.getItemEntity().getItem().getItem() instanceof GunItem)) return;
        if (count(p) >= limit(p)) {
            event.setCanPickup(net.minecraft.util.TriState.FALSE);
            tell(p);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        if (p.tickCount % CHECK_EVERY == 0 && !exempt(p)) {
            List<Integer> guns = gunSlots(p);
            int limit = limit(p);
            if (guns.size() > limit) {
                Inventory inv = p.getInventory();
                for (int i : guns.subList(limit, guns.size())) {
                    ItemStack stack = inv.removeItemNoUpdate(i);
                    if (!stack.isEmpty()) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                tell(p);
            }
        }
        // two long guns in a heavy vest: the load slows you down
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean loaded = !exempt(p) && heavyVest(p) && mainArms(p) >= 2;
        if (loaded) speed.addOrUpdateTransientModifier(new AttributeModifier(LOAD_ID, -LOADED_SLOWDOWN, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (speed.hasModifier(LOAD_ID)) speed.removeModifier(LOAD_ID);
    }

    /** Whether the heavy-load slowdown is on {@code p} right now (read by tests). */
    public static boolean loaded(Player p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(LOAD_ID);
    }

    private static void tell(Player p) {
        long now = p.level().getGameTime();
        Long last = TOLD.get(p);
        if (last != null && now - last < 40) return;
        TOLD.put(p, now);
        int limit = limit(p);
        String text = "You can carry " + limit + " guns" + (limit <= BASE_GUNS ? " (a heavy vest carries " + (BASE_GUNS + 1) + ")" : "");
        p.sendOverlayMessage(Component.literal(text));
    }

    private GunCarry() {}
}
