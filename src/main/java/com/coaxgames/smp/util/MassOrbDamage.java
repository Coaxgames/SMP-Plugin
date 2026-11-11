package com.coaxgames.smp.util;

import org.bukkit.util.BoundingBox;

public final class MassOrbDamage {
    private MassOrbDamage() {
    }

    public static double slamDamage(double fallDistance) {
        if (!Double.isFinite(fallDistance) || fallDistance < 0) {
            throw new IllegalArgumentException("Fall distance must be finite and non-negative.");
        }
        return Math.min(20.0, fallDistance * 0.5);
    }

    public static double impactFraction(
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ,
            double playerWidth, BoundingBox target
    ) {
        if (toY >= fromY || target.getMaxY() > fromY || target.getMaxY() < toY) {
            return -1.0;
        }
        double fraction = (fromY - target.getMaxY()) / (fromY - toY);
        double x = fromX + (toX - fromX) * fraction;
        double z = fromZ + (toZ - fromZ) * fraction;
        double halfWidth = playerWidth / 2.0;
        return x + halfWidth > target.getMinX() && x - halfWidth < target.getMaxX()
                && z + halfWidth > target.getMinZ() && z - halfWidth < target.getMaxZ()
                ? fraction : -1.0;
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
