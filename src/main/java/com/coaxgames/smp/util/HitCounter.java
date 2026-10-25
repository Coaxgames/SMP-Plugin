package com.coaxgames.smp.util;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HitCounter {
    private final long windowMillis;
    private final Map<UUID, Counter> counters = new HashMap<>();

    public HitCounter(Duration window) {
        Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Hit counter window must be positive.");
        }
        windowMillis = window.toMillis();
        if (windowMillis == 0) {
            throw new IllegalArgumentException("Hit counter window must be at least one millisecond.");
        }
    }

    public synchronized int record(UUID playerUuid) {
        return record(playerUuid, System.currentTimeMillis());
    }

    public synchronized int record(UUID playerUuid, long nowMillis) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Counter previous = counters.get(playerUuid);
        int count = previous != null && isWithinWindow(previous, nowMillis)
                ? Math.incrementExact(previous.count())
                : 1;
        counters.put(playerUuid, new Counter(count, nowMillis));
        return count;
    }

    public synchronized int getCount(UUID playerUuid, long nowMillis) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Counter counter = counters.get(playerUuid);
        if (counter == null) {
            return 0;
        }
        if (!isWithinWindow(counter, nowMillis)) {
            counters.remove(playerUuid);
            return 0;
        }
        return counter.count();
    }

    public synchronized void clear(UUID playerUuid) {
        counters.remove(Objects.requireNonNull(playerUuid, "playerUuid"));
    }

    public synchronized void clearExpired(long nowMillis) {
        counters.entrySet().removeIf(entry -> !isWithinWindow(entry.getValue(), nowMillis));
    }

    private boolean isWithinWindow(Counter counter, long nowMillis) {
        return nowMillis >= counter.lastHitMillis()
                && nowMillis - counter.lastHitMillis() <= windowMillis;
    }

    private record Counter(int count, long lastHitMillis) {
    }
}
