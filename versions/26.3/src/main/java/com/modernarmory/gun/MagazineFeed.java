package com.modernarmory.gun;

import com.modernarmory.item.GunAnimations;

/**
 * Whole-unit swap: a box magazine ({@code chamberPlusOne}: the chambered round stays, so a
 * tactical reload holds magazine + 1) or a belt / ammo box (open bolt, no +1).
 * Tactical reload plays "reload", from empty "reload_empty" (bolt release / charging handle).
 */
public record MagazineFeed(String name, boolean chamberPlusOne) implements Feed {

    @Override
    public int capacity(int magazineSize, int loaded) {
        return chamberPlusOne && loaded > 0 ? magazineSize + 1 : magazineSize;
    }

    @Override
    public ReloadTask startReload(ReloadContext ctx) {
        int loaded = ctx.loaded();
        if (loaded >= capacity(ctx.magazineSize(), loaded)) return null;
        if (ctx.available() <= 0) {
            ctx.noAmmo();
            return null;
        }
        boolean empty = loaded == 0;
        int ticks = ctx.reloadTicks(empty);
        SoundSet s = ctx.sounds();
        ctx.cue((empty ? s.reloadEmpty() : s.reload()).get(), empty ? GunAnimations.RELOAD_EMPTY : GunAnimations.RELOAD, false);
        return ReloadTask.timed(ticks, () -> {
            int now = ctx.loaded();
            int need = capacity(ctx.magazineSize(), now) - now;
            if (need > 0) ctx.setLoaded(now + ctx.take(need));
        });
    }
}
