package com.modernarmory.gun;

import java.util.Objects;

/**
 * One catalog entry: a gun item and everything needed to register and render it.
 *
 * @param id          item registry path, e.g. "m4" (also the mastery key)
 * @param displayName English name (used when the lang file has no entry; the autotest checks they agree)
 * @param spec        behaviour
 * @param modelId     name of the GeckoLib geo / texture / animation files and the item model JSON's
 *                    model; usually the id, but variants can share a model (the M4A1 renders as "m4")
 */
public record GunDefinition(String id, String displayName, GunSpec spec, String modelId) {
    public GunDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(spec);
        Objects.requireNonNull(modelId);
    }

    public static GunDefinition of(String id, String displayName, GunSpec.Builder spec) {
        return new GunDefinition(id, displayName, spec.build(id), id);
    }

    /** The same gun rendered with another gun's model files. */
    public GunDefinition model(String otherModelId) {
        return new GunDefinition(id, displayName, spec, otherModelId);
    }
}
