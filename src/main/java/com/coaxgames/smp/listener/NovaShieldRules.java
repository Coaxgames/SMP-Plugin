package com.coaxgames.smp.listener;

final class NovaShieldRules {
    static final int HITS_TO_DISABLE = 2;
    static final int DISABLE_TICKS = 10 * 20;

    private NovaShieldRules() {
    }

    static boolean qualifies(boolean axe, boolean critical, float attackStrength,
                             boolean blocked, boolean shieldCoolingDown) {
        return axe && critical && attackStrength >= 1.0F && blocked && !shieldCoolingDown;
    }

    static int nextHitCount(int current, boolean qualifyingHit) {
        if (current < 0 || current >= HITS_TO_DISABLE) {
            throw new IllegalArgumentException("Invalid shield hit count.");
        }
        return qualifyingHit ? current + 1 : current;
    }
}
