package com.modernarmory.mastery;

/**
 * Gun mastery rules, all in one place: how much XP each level needs, how XP is earned, and what
 * each level does. Tune the game by editing the constants here.
 */
public final class Mastery {
    public static final int MAX_LEVEL = 5;
    /** Total XP needed to reach level 1..5. Each level needs more than the last. */
    private static final int[] THRESHOLDS = {100, 350, 900, 2000, 4000};

    /** XP per round fired: semi/auto guns 1, manually cycled (bolt/pump) guns 3. */
    public static final int XP_PER_SHOT = 1, XP_PER_SHOT_MANUAL = 3;
    /** XP for killing a hostile mob with the gun: base + a share of its max health; headshot bonus. */
    public static final int XP_KILL_BASE = 20, XP_HEADSHOT_BONUS = 10;
    public static final float XP_KILL_PER_MAX_HEALTH = 0.5f;

    /** Per level: -10% movement spread, -4% reload time, -12% scope/breathing sway, -19% view bob while aiming (ADS; -95% at V), -12% hip-fire spread. */
    private static final float MOVE_SPREAD_PER_LEVEL = 0.10f, RELOAD_PER_LEVEL = 0.04f, SWAY_PER_LEVEL = 0.12f, AIM_BOB_PER_LEVEL = 0.19f,
            HIP_SPREAD_PER_LEVEL = 0.12f;

    /** What a mastery level does (multipliers, 1 = no change). */
    public record Effects(float movingSpread, float reload, float sway, float aimBob, float hipSpread) {
        public static final Effects NONE = new Effects(1, 1, 1, 1, 1);
    }

    public static int level(int xp) {
        int lvl = 0;
        while (lvl < MAX_LEVEL && xp >= THRESHOLDS[lvl]) lvl++;
        return lvl;
    }

    /** XP needed for the next level (or the max threshold at level 5). */
    public static int nextThreshold(int level) {
        return THRESHOLDS[Math.min(level, MAX_LEVEL - 1)];
    }

    public static int levelStart(int level) {
        return level == 0 ? 0 : THRESHOLDS[level - 1];
    }

    /** 0..1 progress through the current level. */
    public static float progress(int xp) {
        int lvl = level(xp);
        if (lvl >= MAX_LEVEL) return 1f;
        int a = levelStart(lvl), b = THRESHOLDS[lvl];
        return (xp - a) / (float) (b - a);
    }

    public static Effects effects(int level) {
        if (level <= 0) return Effects.NONE;
        return new Effects(1 - MOVE_SPREAD_PER_LEVEL * level, 1 - RELOAD_PER_LEVEL * level, 1 - SWAY_PER_LEVEL * level,
                1 - AIM_BOB_PER_LEVEL * level, 1 - HIP_SPREAD_PER_LEVEL * level);
    }

    public static String roman(int level) {
        return switch (level) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> "0"; };
    }

    private Mastery() {}
}
