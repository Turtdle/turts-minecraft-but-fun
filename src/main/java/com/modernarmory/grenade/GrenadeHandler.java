package com.modernarmory.grenade;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * CS-style grenade controls, server side. Pressing either mouse button pulls the pin (GrenadeItem.PIN_TICKS);
 * letting go throws: left click = overhand, right click = underhand, both released together = medium.
 * A quick click throws too: the throw goes as soon as the pin is out. Switching away before it leaves the hand
 * puts the pin back.
 */
@EventBusSubscriber(modid = com.modernarmory.ModernArmory.MODID)
public final class GrenadeHandler {
    /** Buttons let go this close together (ticks) count as released together: a medium throw. */
    private static final int TOGETHER_TICKS = 4;

    private static final class Hold {
        boolean left, right;
        long pinStart = -1;   // tick the pin pull started; -1 = not holding a grenade ready
        long bothUntil = -1;  // last tick both buttons were held
        int slot;
        ThrowKind queued;     // let go before the pin was out: thrown as soon as it is
    }

    private static final Map<ServerPlayer, Hold> HOLDS = new WeakHashMap<>();

    /** The client's buttons changed (GrenadeInputPayload). */
    public static void setInput(ServerPlayer player, boolean left, boolean right) {
        Hold h = HOLDS.computeIfAbsent(player, p -> new Hold());
        ItemStack stack = player.getMainHandItem();
        long now = player.level().getGameTime();
        boolean wasDown = h.left || h.right, down = left || right;
        if (h.left && h.right) h.bothUntil = now;
        boolean wasLeft = h.left, wasRight = h.right;
        h.left = left;
        h.right = right;
        if (!(stack.getItem() instanceof GrenadeItem grenade)) {
            h.pinStart = -1;
            h.queued = null;
            return;
        }
        if (!wasDown && down) { // first button: pull the pin
            if (h.pinStart >= 0) return; // a quick click's throw is still waiting for the pin
            if (player.getCooldowns().isOnCooldown(grenade)) return;
            h.pinStart = now;
            h.bothUntil = left && right ? now : -1;
            h.slot = player.getInventory().selected;
            grenade.pullPin(player, stack);
            return;
        }
        if (wasDown && !down && h.pinStart >= 0 && h.queued == null) { // everything let go: throw
            // both held until (nearly) the end: medium; otherwise the button that was held last decides
            ThrowKind kind = now - h.bothUntil <= TOGETHER_TICKS ? ThrowKind.MEDIUM
                    : wasLeft ? ThrowKind.OVERHAND : ThrowKind.UNDERHAND;
            if (now - h.pinStart < GrenadeItem.PIN_TICKS) {
                h.queued = kind; // a quick click: throw once the pin is out (onPlayerTick)
                return;
            }
            h.pinStart = -1;
            grenade.throwFrom(player, stack, kind);
        }
    }

    /** Throws a quick click's grenade once its pin is out; switching items (or dropping it) first puts the pin back. */
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Hold h = HOLDS.get(player);
        if (h == null || h.pinStart < 0) return;
        ItemStack stack = player.getMainHandItem();
        if (player.getInventory().selected != h.slot || !(stack.getItem() instanceof GrenadeItem grenade)) {
            h.pinStart = -1;
            h.queued = null;
            if (stack.getItem() instanceof GrenadeItem g) g.cancel(player, stack);
        } else if (h.queued != null && player.level().getGameTime() - h.pinStart >= GrenadeItem.PIN_TICKS) {
            ThrowKind kind = h.queued;
            h.queued = null;
            h.pinStart = -1;
            grenade.throwFrom(player, stack, kind);
        } else if (h.left && h.right) {
            h.bothUntil = player.level().getGameTime();
        }
    }

    private GrenadeHandler() {}
}
