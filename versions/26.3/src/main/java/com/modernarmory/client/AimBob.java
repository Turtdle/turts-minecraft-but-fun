package com.modernarmory.client;

import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.MasteryHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Mastery perk: the higher your mastery with the gun in hand, the less your view bobs while walking aimed
 * down sights (Mastery.Effects#aimBob).
 *
 * <p>26.x copies the walk bob into the frame's camera render state (Camera#extract) before RenderFrameEvent.Pre,
 * and both the camera bob and the first-person hand bob (GameRenderer#bobView) read that copy. So the copy is
 * scaled here, after it was taken and before the frame is drawn; it is taken afresh every frame, so nothing needs
 * putting back and the player's own bob state is never touched. (Scaling ClientAvatarState#bob here instead, as
 * before, was too late: the frame had already been given the full bob.)
 */
final class AimBob {
    /** For the autotest: the scale applied this frame, and the frame's camera bob before and after it. */
    static float lastScale = 1, lastRawBob, lastScaledBob;

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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        lastScale = 1;
        if (p == null) return;
        float f = factor(p);
        if (f >= 0.999f) return;
        CameraEntityRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.entityRenderState;
        lastRawBob = camera.bob;
        camera.bob *= f;
        lastScaledBob = camera.bob;
        lastScale = f;
    }

    /** Nothing to restore: the camera state is extracted afresh next frame. */
    static void onFramePost(RenderFrameEvent.Post event) {}

    private AimBob() {}
}
