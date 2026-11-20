package com.modernarmory.client;

import com.modernarmory.grenade.GrenadeItem;
import com.modernarmory.network.GrenadeInputPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Grenade controls, client side (CS style; the server decides the throw, grenade.GrenadeHandler): with a
 * grenade in hand, left and right click don't attack or use, they are sent to the server whenever they change.
 */
public final class GrenadeInput {
    private static boolean sentLeft, sentRight;

    static boolean holdingGrenade(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof GrenadeItem;
    }

    /** No block breaking, swinging or vanilla use while a grenade is in hand. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if ((event.isAttack() || event.isUseItem()) && holdingGrenade(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean active = holdingGrenade(p) && mc.screen == null && !p.isSpectator() && !DronePilot.active();
        boolean left = active && mc.options.keyAttack.isDown(), right = active && mc.options.keyUse.isDown();
        if (left != sentLeft || right != sentRight) {
            PacketDistributor.sendToServer(new GrenadeInputPayload(left, right));
            sentLeft = left;
            sentRight = right;
        }
    }

    private GrenadeInput() {}
}
