package com.modernarmory.gun;

/** A reload in progress, ticked by GunHandler once per server tick. */
public interface ReloadTask {
    /** Advance one tick; returns true when the reload has finished. */
    boolean tick();

    /** Whether pulling the trigger now stops the reload (and fires). */
    default boolean interruptible() {
        return false;
    }

    /** A reload that waits {@code ticks} and then runs {@code finish} (whole magazine / belt swap). */
    static ReloadTask timed(int ticks, Runnable finish) {
        return new ReloadTask() {
            private int left = ticks;

            @Override
            public boolean tick() {
                if (--left > 0) return false;
                finish.run();
                return true;
            }
        };
    }
}
