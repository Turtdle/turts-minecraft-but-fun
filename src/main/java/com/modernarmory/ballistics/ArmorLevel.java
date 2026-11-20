package com.modernarmory.ballistics;

/**
 * Body-armour protection levels, after the US NIJ standard (0101.06), lowest first. A piece of armour
 * stops every projectile whose {@link Cartridge#stoppedBy()} level is at or below its own; anything above
 * goes through it.
 *
 * <ul>
 *   <li>{@link #IIIA}: soft aramid (Kevlar) panels: handgun rounds up to .44 Magnum, buckshot, fragments.</li>
 *   <li>{@link #III}: rifle plates (steel or polyethylene): 7.62x51 M80 ball and softer rifle rounds. The
 *       5.56 M855 steel penetrator and the newer steel-tipped NATO rounds defeat many of them.</li>
 *   <li>{@link #IV}: ceramic plates (ESAPI class): .30-06 M2 armour-piercing and everything below.</li>
 *   <li>{@link #JUGGERNAUT}: a bomb-disposal suit with rifle plates over it: beyond any NIJ level (game
 *       buff); stops the .338 magnums.</li>
 *   <li>{@link #NOTHING}: nothing a person can wear stops it (.50 BMG).</li>
 * </ul>
 */
public enum ArmorLevel {
    NONE("none"),
    IIIA("NIJ IIIA"),
    III("NIJ III"),
    IV("NIJ IV"),
    JUGGERNAUT("Juggernaut"),
    NOTHING("nothing");

    public final String label;

    ArmorLevel(String label) {
        this.label = label;
    }

    /** Whether armour of this level stops a projectile that needs {@code needed} to stop it. */
    public boolean stops(ArmorLevel needed) {
        return this != NONE && needed != NOTHING && ordinal() >= needed.ordinal();
    }

    /** One level lower (a bullet that has lost most of its energy is easier to stop); NONE stays NONE. */
    public ArmorLevel weaker() {
        return this == NONE || this == IIIA ? this : values()[ordinal() - 1];
    }
}
