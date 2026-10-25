package com.coaxgames.smp.util;

public final class MaceDamage {
    private MaceDamage() {
    }

    public static double capBaseDamage(double baseDamage, double maximumDamage) {
        validate(baseDamage, "baseDamage");
        validate(maximumDamage, "maximumDamage");
        return Math.min(baseDamage, maximumDamage);
    }

    public static boolean exceedsSparkThreshold(double finalDamage) {
        validate(finalDamage, "finalDamage");
        return finalDamage > 10.0;
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
