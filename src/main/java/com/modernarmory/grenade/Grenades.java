package com.modernarmory.grenade;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hand grenades, with real US Army data. Each fuze starts when the spoon flies off (the throw); the
 * pin only arms it. Adding a grenade is one entry here, a model (tools/modelgen/guns/&lt;id&gt;.py) and,
 * for a new kind of effect, a {@link GrenadeEffect}.
 */
public final class Grenades {
    private static final Map<String, GrenadeType> BY_ID = new LinkedHashMap<>();

    /** M67 fragmentation: 4-5.5 s fuze, ~400 g, 6.5 oz of Comp B; lethal ~5 m, casualties to ~15 m. */
    public static final GrenadeType M67 = add(new GrenadeType("grenade_m67", "M67 Fragmentation Grenade", 4.5f, 0.40f,
            new FragEffect(), "Blast and steel fragments. Lethal within ~5 m.", 0.15f, 0.17f));
    /** M84 stun grenade: 1-2.3 s fuze, ~240 g; 170-180 dB bang and a >1 million candela flash, no fragments. */
    public static final GrenadeType M84 = add(new GrenadeType("grenade_m84", "M84 Stun Grenade", 1.5f, 0.24f,
            new FlashEffect(), "Blinding flash and bang. Stuns, does no harm.", 0.12f, 0.14f));
    /** M18 smoke (grey screening smoke): 1.2-2 s delay, burns 50-90 s. */
    public static final GrenadeType M18 = add(new GrenadeType("grenade_m18", "M18 Smoke Grenade", 1.6f, 0.54f,
            new SmokeEffect(0x9A9A9A), "Thick grey smoke, ~10 m across, for about a minute.", 0.16f, 0.18f));
    /** M7A3 CS riot control: 1.4-3 s delay, ~430 g, burns 15-35 s (here 30 s) in a cloud of CS tear gas. */
    public static final GrenadeType M7A3 = add(new GrenadeType("grenade_m7a3", "M7A3 CS Riot Control Grenade", 2.0f, 0.43f,
            new TearGasEffect(), "Tear gas for 30 s. Blinds anyone inside; the longer they stay, the longer it lasts.", 0.16f, 0.18f));
    /** AN-M14 TH3 incendiary: 0.7-2 s delay, thermite at ~2,200 C (here: a 7 m burning patch for 20 s). */
    public static final GrenadeType ANM14 = add(new GrenadeType("grenade_anm14", "AN-M14 TH3 Incendiary Grenade", 1.5f, 0.90f,
            new IncendiaryEffect(), "Bursts into thermite flames ~7 m across for 20 s. Very hot.", 0.19f, 0.20f));

    private static GrenadeType add(GrenadeType t) {
        BY_ID.put(t.id(), t);
        return t;
    }

    public static Collection<GrenadeType> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static GrenadeType byId(String id) {
        return BY_ID.get(id);
    }

    private Grenades() {}
}
