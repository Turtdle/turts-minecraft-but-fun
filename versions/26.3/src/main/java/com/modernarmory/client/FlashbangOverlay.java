package com.modernarmory.client;

import com.modernarmory.grenade.FlashEffect;
import com.modernarmory.network.FlashbangPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

/**
 * Being caught by an M84: a flash you saw whites the screen out completely, holds, then fades back; the bang
 * rings your ears whether you saw it or not (round a corner, turned away: ringing only). The flash's strength
 * only sets how long: a close, face-on flash holds for seconds and fades slowly; a weak one (far, side-on) is
 * just as blinding but clears quickly. The fade stays nearly opaque most of the way and clears at the end.
 */
public final class FlashbangOverlay {
    /** Seconds of white a glance at the glare gives (turned away from a flash in the open): under a tenth. */
    static final float GLANCE_S = 0.09f;
    private static float strength, durationS;
    private static long startNanos;

    public static void onFlash(FlashbangPayload p) {
        if (p.flash() >= FlashEffect.THRESHOLD) {
            // a second flash whites out again and lasts at least as long as what was left of the first
            float left = remaining();
            strength = Math.max(p.flash(), strength * (left > 0 ? 1 : 0));
            durationS = Math.max(duration(p.flash()), left);
            startNanos = System.nanoTime();
        } else if (p.glance() && remaining() < GLANCE_S) {
            // turned away: the room lights up white for an instant, then it's gone (never cuts a real white-out short)
            strength = 0;
            durationS = GLANCE_S;
            startNanos = System.nanoTime();
        }
        float ring = Math.max(p.bang(), p.flash());
        if (ring >= FlashEffect.THRESHOLD)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.FLASH_RING.get(), 1.0f,
                    Mth.clamp(ring, 0.2f, 1.0f)));
    }

    /** Seconds the white-out lasts at this strength (hold + fade): ~1 s for the weakest, 7 s point blank. */
    static float duration(float s) {
        return 0.8f + 6.2f * Mth.clamp(s, 0, 1);
    }

    private static float remaining() {
        return durationS <= 0 ? 0 : Math.max(0, durationS - (System.nanoTime() - startNanos) / 1e9f);
    }

    /** Opacity of the white-out right now, 0..1: fully opaque at first, whatever the strength. */
    public static float current() {
        if (durationS <= 0) return 0;
        float t = (System.nanoTime() - startNanos) / 1e9f, d = durationS;
        if (t >= d) return 0;
        float hold = d * (0.2f + 0.2f * Mth.clamp(strength, 0, 1)); // stronger flashes also hold longer
        if (t < hold) return 1;
        float x = (t - hold) / (d - hold);
        return 1 - (float) Math.pow(x, 2.5); // slow at first: still ~80% white halfway through the fade
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) { // 26.x: GUI layers extract into GuiGraphicsExtractor
        float a = current();
        if (a <= 0.003f) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (Math.round(a * 255) << 24) | 0xFFFFF4);
    }

    private FlashbangOverlay() {}
}
