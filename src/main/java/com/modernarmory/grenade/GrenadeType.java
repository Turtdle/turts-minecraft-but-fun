package com.modernarmory.grenade;

/**
 * One kind of hand grenade (catalog: {@link Grenades}).
 *
 * @param id          registry path of the item (and its model files, tools/modelgen/guns/&lt;id&gt;.py)
 * @param name        display name
 * @param fuzeSeconds delay from the spoon flying off (the throw) to the effect
 * @param massKg      thrown weight
 * @param effect      what it does when the fuze runs out
 * @param description one line for the tooltip
 * @param releaseOver  seconds into the overhand "throw" animation when the grenade leaves the hand
 * @param releaseUnder seconds into "throw_under" when it leaves the hand
 */
public record GrenadeType(String id, String name, float fuzeSeconds, float massKg, GrenadeEffect effect, String description,
                          float releaseOver, float releaseUnder) {
    public int fuzeTicks() {
        return Math.round(fuzeSeconds * 20);
    }

    /** Ticks from letting go of the button to the grenade leaving the hand (matches the throw animation). */
    public int releaseTicks(boolean underhand) {
        return Math.max(1, Math.round((underhand ? releaseUnder : releaseOver) * 20));
    }
}
