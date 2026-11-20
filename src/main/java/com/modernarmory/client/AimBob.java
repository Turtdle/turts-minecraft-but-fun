package com.modernarmory.client;

import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.MasteryHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Mastery perk: the higher your mastery with the gun in hand, the less your view bobs while walking aimed
 * down sights (Mastery.Effects#aimBob). Vanilla's walk bob (camera and hand) is scaled for the frame being
 * drawn and put back afterwards, so the game's own bob state is never changed.
 */
final class AimBob {
    private static LocalPlayer scaledPlayer;
    private static float savedBob, savedBobO;

    /** For the autotest: the scale applied this frame and the bob before scaling. */
    static float lastScale = 1, lastRawBob;

    /** The bob multiplier mastery gives with the held gun (1 = full bob). */
    static float masteryFactor(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof GunItem g ? MasteryHandler.effects(p, g).aimBob() : 1f;
    }

    /** The multiplier for this frame: none when hip firing, the full mastery reduction when fully aimed. */
    static float factor(LocalPlayer p) {
        float aim = ClientState.aimProgress();
        return aim <= 0 ? 1f : Mth.lerp(aim, 1f, masteryFactor(p));
    }

    static void onFramePre(RenderFrameEvent.Pre event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        float f = factor(p);
        lastScale = 1;
        if (f >= 0.999f) return;
        // 1.21.1: Player#bob / #oBob are public
        savedBob = p.bob;
        savedBobO = p.oBob;
        p.bob *= f;
        p.oBob *= f;
        scaledPlayer = p;
        lastScale = f;
        lastRawBob = savedBob;
    }

    static void onFramePost(RenderFrameEvent.Post event) {
        LocalPlayer p = scaledPlayer;
        if (p == null) return;
        scaledPlayer = null;
        p.bob = savedBob;
        p.oBob = savedBobO;
    }

    private AimBob() {}
}
