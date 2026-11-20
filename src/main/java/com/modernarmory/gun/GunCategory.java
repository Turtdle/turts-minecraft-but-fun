package com.modernarmory.gun;

/**
 * What kind of gun it is: each kind is its own creative inventory tab (registry.ModCreativeTabs), in this
 * order. A gun gets its category from its family (GunFamilies); the tab title is lang key
 * itemGroup.modernarmory.&lt;tabId&gt;.
 */
public enum GunCategory {
    PISTOL("pistols"),
    SMG("smgs"),
    RIFLE("rifles"),
    MARKSMAN("marksman_rifles"),
    SNIPER("sniper_rifles"),
    SHOTGUN("shotguns"),
    MACHINE_GUN("machine_guns");

    public final String tabId;

    GunCategory(String tabId) {
        this.tabId = tabId;
    }

    /** A main arm (long gun: everything but a pistol), as opposed to a sidearm. */
    public boolean mainArm() {
        return this != PISTOL;
    }
}
