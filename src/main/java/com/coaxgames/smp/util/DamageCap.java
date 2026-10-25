package com.coaxgames.smp.util;

public final class DamageCap {
    private DamageCap() {
    }

    public static double capFinalDamage(double finalDamage, double maximumFinalDamage) {
        validateDamage(finalDamage, "finalDamage");
        validateDamage(maximumFinalDamage, "maximumFinalDamage");
        return Math.min(finalDamage, maximumFinalDamage);
    }

    private static void validateDamage(double damage, String name) {
        if (!Double.isFinite(damage) || damage < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
