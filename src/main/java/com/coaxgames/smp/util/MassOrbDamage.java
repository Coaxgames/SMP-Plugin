package com.coaxgames.smp.util;

public final class MassOrbDamage {
    private MassOrbDamage() {
    }

    public static double calculate(
            double fallDistance,
            double freeFallDistance,
            double damagePerBlock,
            double maximumDamage,
            int featherFallingLevel,
            double reductionPerLevel,
            double maximumReduction
    ) {
        if (!Double.isFinite(fallDistance) || fallDistance < 0
                || !Double.isFinite(freeFallDistance) || freeFallDistance < 0
                || !Double.isFinite(damagePerBlock) || damagePerBlock < 0
                || !Double.isFinite(maximumDamage) || maximumDamage < 0
                || featherFallingLevel < 0
                || !Double.isFinite(reductionPerLevel) || reductionPerLevel < 0
                || !Double.isFinite(maximumReduction) || maximumReduction < 0) {
            throw new IllegalArgumentException("Mass Orb damage inputs must be finite and non-negative.");
        }
        double baseDamage = Math.min(
                maximumDamage,
                Math.max(0.0, fallDistance - freeFallDistance) * damagePerBlock);
        double reduction = Math.min(1.0, Math.min(maximumReduction, featherFallingLevel * reductionPerLevel));
        return baseDamage * (1.0 - reduction);
    }
}
