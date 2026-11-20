package com.modernarmory;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER = BUILDER
            .comment("Multiplier for all bullet damage")
            .defineInRange("damageMultiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue HEADSHOT_MULTIPLIER = BUILDER
            .comment("Damage multiplier for hits above the target's eye line minus 0.25 blocks")
            .defineInRange("headshotMultiplier", 2.5, 1.0, 100.0);

    public static final ModConfigSpec.BooleanValue BULLETS_BREAK_GLASS = BUILDER
            .comment("Whether bullets shatter glass blocks and panes")
            .define("bulletsBreakGlass", true);

    public static final ModConfigSpec.BooleanValue CREATIVE_INFINITE_AMMO = BUILDER
            .comment("Creative-mode players never run out of rounds")
            .define("creativeInfiniteAmmo", true);

    public static final ModConfigSpec.IntValue BULLET_DODGE_COOLDOWN_SECONDS = BUILDER
            .comment("Endermen (entity tag modernarmory:dodges_first_bullet) teleport away from the first bullet; for this many seconds after that, bullets hit them")
            .defineInRange("bulletDodgeCooldownSeconds", 30, 0, 3600);

    public static final ModConfigSpec.BooleanValue GRENADES_BREAK_BLOCKS = BUILDER
            .comment("M67 fragmentation grenades blow holes in the terrain (off: blast and fragments only, like the real thing)")
            .define("grenadesBreakBlocks", false);

    public static final ModConfigSpec.BooleanValue INCENDIARY_SETS_BLOCKS_ALIGHT = BUILDER
            .comment("AN-M14 incendiary grenades also start vanilla fires at the edge of their flames (vanilla fire spreads",
                    "and sets players burning; off: the grenade's own flames only, which hurt but don't set you alight)")
            .define("incendiarySetsBlocksAlight", false);

    public static final ModConfigSpec.BooleanValue DRONE_CHARGE_BREAKS_BLOCKS = BUILDER
            .comment("The FPV strike drone's charge blows holes in the terrain")
            .define("droneChargeBreaksBlocks", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
