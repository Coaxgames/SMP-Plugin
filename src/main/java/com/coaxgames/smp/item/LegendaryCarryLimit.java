package com.coaxgames.smp.item;

public final class LegendaryCarryLimit {
    public static final int MAX_ITEMS = 2;

    private LegendaryCarryLimit() {
    }

    public static boolean allows(boolean operator, boolean legendary, int held, int reserved, int incoming) {
        if (held < 0 || reserved < 0 || incoming < 0) {
            throw new IllegalArgumentException("Item counts cannot be negative.");
        }
        return operator || !legendary || (long) held + reserved + incoming <= MAX_ITEMS;
    }
}
