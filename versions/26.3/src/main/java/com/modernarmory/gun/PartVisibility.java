package com.modernarmory.gun;

/**
 * When a named part (bone) of a gun's model is shown, for parts whose visibility depends on the
 * situation rather than on a fitted attachment. Set per family / gun with
 * {@link GunSpec.Builder#part(String, PartVisibility)} and applied by the gun renderer; a model
 * without that bone is unaffected, so a family can set it for all its members.
 */
public enum PartVisibility {
    /** A folding backup iron sight: laid flat when any optic is fitted (as the flip-up rear sight always is). */
    FOLDS_UNDER_OPTIC,
    /**
     * Only drawn in first person (e.g. the M2's M3 tripod): a third-person player, an icon, a
     * dropped item or an item frame shows the gun carried without it.
     */
    FIRST_PERSON_ONLY;

    /**
     * The iron-sight parts of every gun's model, by bone name: folded away (hidden) whenever an optic is fitted, so
     * nothing stands in the optic's view. Guns without one of them are unaffected.
     */
    public static final java.util.Set<String> IRON_SIGHTS = java.util.Set.of("rear_sight", "front_sight");

    /** Whether the part is hidden in this situation. */
    public boolean hidden(boolean opticFitted, boolean firstPerson) {
        return switch (this) {
            case FOLDS_UNDER_OPTIC -> opticFitted;
            case FIRST_PERSON_ONLY -> !firstPerson;
        };
    }
}
