package com.coaxgames.smp.util;

import org.bukkit.NamespacedKey;

public final class MaceDamage {
    public static final int STAR_MACE_MIN_WIND_BURST = 1;
    public static final int STAR_MACE_MAX_WIND_BURST = 5;

    private MaceDamage() {
    }

    public static double capBaseDamage(double baseDamage, double maximumDamage) {
        validate(baseDamage, "baseDamage");
        validate(maximumDamage, "maximumDamage");
        return Math.min(baseDamage, maximumDamage);
    }

    public static int nextStarMaceWindBurst(int currentLevel, boolean hit) {
        if (currentLevel < STAR_MACE_MIN_WIND_BURST || currentLevel > STAR_MACE_MAX_WIND_BURST) {
            throw new IllegalArgumentException("currentLevel must be between I and V.");
        }
        return hit ? Math.min(STAR_MACE_MAX_WIND_BURST, currentLevel + 1) : STAR_MACE_MIN_WIND_BURST;
    }

    public static boolean advancesStarMace(double damage, NamespacedKey damageType) {
        return damage > 0.0 && NamespacedKey.minecraft("mace_smash").equals(damageType);
    }

    public static boolean startsStarMaceCooldown(int currentLevel, boolean hit) {
        if (currentLevel < STAR_MACE_MIN_WIND_BURST || currentLevel > STAR_MACE_MAX_WIND_BURST) {
            throw new IllegalArgumentException("currentLevel must be between I and V.");
        }
        return !hit && currentLevel == STAR_MACE_MAX_WIND_BURST;
    }

    public static int collapsingSparkCount(double maceDamageHealthPoints, int consecutiveHits) {
        validate(maceDamageHealthPoints, "maceDamageHealthPoints");
        if (consecutiveHits < 1) {
            throw new IllegalArgumentException("consecutiveHits must be at least one.");
        }
        long sparksFromDamage = (long) Math.floor(maceDamageHealthPoints / 2.0);
        long count = Math.addExact(sparksFromDamage, consecutiveHits - 1L);
        return Math.toIntExact(count);
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
