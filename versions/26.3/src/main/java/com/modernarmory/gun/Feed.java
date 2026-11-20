package com.modernarmory.gun;

/**
 * How a gun is fed and reloaded (strategy). Pick one per gun in {@link GunSpec.Builder#feed(Feed)};
 * the family templates in {@link GunFamilies} already do.
 * <ul>
 *   <li>{@link #MAGAZINE}: detachable box magazine swapped whole; a tactical reload keeps the
 *       chambered round (+1).</li>
 *   <li>{@link #BELT}: belt / ammo box swapped whole; nothing stays chambered (open bolt), so no +1.
 *       Reload times are long (set in the spec).</li>
 *   <li>{@link #CYLINDER}: a revolver's cylinder, emptied and refilled all at once (speedloader);
 *       every round sits in a chamber already, so no +1.</li>
 *   <li>{@link #TUBE}: tube magazine loaded one shell at a time. From empty, one shell is first
 *       dropped into the chamber ("reload_empty"), then each shell is pushed into the tube on a
 *       loop of the per-shell timer ("reload"). Pulling the trigger with a round chambered stops
 *       the reload and fires.</li>
 * </ul>
 */
public interface Feed {
    Feed MAGAZINE = new MagazineFeed("magazine", true);
    Feed BELT = new MagazineFeed("belt", false);
    Feed TUBE = new TubeFeed();
    Feed CYLINDER = new MagazineFeed("cylinder", false);

    /** Name for logs / docs. */
    String name();

    /** Rounds the gun holds after a full reload, given its magazine size and the rounds loaded now. */
    int capacity(int magazineSize, int loaded);

    /** Starts a reload, or returns null if the gun is full or there is nothing to load. */
    ReloadTask startReload(ReloadContext ctx);
}
