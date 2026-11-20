package com.modernarmory.gun;

/**
 * The magnification range of the scope a gun is issued with, when that differs from the shared
 * scope attachment's own range (e.g. the M110's Leupold 3.5-10x fitted as the shared
 * {@code sniper_scope}). Applies only while the gun's factory-fitted optic is on it; any other
 * optic keeps its own range. Set with {@link GunSpec.Builder#issuedScopePower(float, float)}.
 *
 * @param min lowest power (the power ring starts here)
 * @param max highest power
 */
public record ScopePower(float min, float max) {
    public ScopePower {
        if (!(min >= 1f && max >= min)) throw new IllegalArgumentException("bad scope power " + min + "-" + max);
    }
}
