package com.modernarmory.gun;

import com.modernarmory.item.GunAnimations;

/**
 * Tube magazine (pump / semi-auto shotguns): holds magazine size in the tube + 1 in the chamber,
 * loaded shell by shell. See {@link Feed#TUBE}.
 */
final class TubeFeed implements Feed {

    @Override
    public String name() {
        return "tube";
    }

    @Override
    public int capacity(int magazineSize, int loaded) {
        return magazineSize + 1;
    }

    @Override
    public ReloadTask startReload(ReloadContext ctx) {
        if (ctx.loaded() >= capacity(ctx.magazineSize(), ctx.loaded())) return null;
        if (ctx.available() <= 0) {
            ctx.noAmmo();
            return null;
        }
        return new ShellByShell(ctx);
    }

    /** Chamber a shell if empty, then load the tube one shell per step until full or out of shells. */
    private final class ShellByShell implements ReloadTask {
        private final ReloadContext ctx;
        private int left;

        ShellByShell(ReloadContext ctx) {
            this.ctx = ctx;
            begin();
        }

        /** Start the next step: chamber on empty, otherwise one shell into the tube. */
        private void begin() {
            boolean empty = ctx.loaded() == 0;
            left = ctx.reloadTicks(empty);
            SoundSet s = ctx.sounds();
            if (empty) ctx.cue(s.reloadEmpty().get(), GunAnimations.RELOAD_EMPTY, true);
            else ctx.cue(s.shellInsert().get(), GunAnimations.RELOAD, true);
        }

        @Override
        public boolean tick() {
            if (--left > 0) return false;
            int got = ctx.take(1);
            if (got <= 0) return true;
            int loaded = ctx.loaded() + got;
            ctx.setLoaded(loaded);
            if (loaded >= capacity(ctx.magazineSize(), loaded) || ctx.available() <= 0) return true;
            begin();
            return false;
        }

        /** A round in the chamber can be fired at any time; with an empty chamber, finish chambering first. */
        @Override
        public boolean interruptible() {
            return ctx.loaded() > 0;
        }
    }
}
