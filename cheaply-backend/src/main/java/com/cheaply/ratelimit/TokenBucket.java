package com.cheaply.ratelimit;

/**
 * A small token bucket: {@code capacity} tokens that refill smoothly over
 * {@code windowNanos}.
 *
 * <p>Chosen over a fixed window because a fixed window lets a caller fire two
 * full allowances back to back across a window boundary. Kept deliberately
 * tiny - the whole class is one long of state - because this runs on every
 * request to the rate-limited paths.
 */
final class TokenBucket {

    private final int capacity;
    private final long windowNanos;

    private double availableTokens;
    private long lastRefillNanos;

    TokenBucket(int capacity, long windowNanos, long nowNanos) {
        this.capacity = capacity;
        this.windowNanos = windowNanos;
        this.availableTokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /**
     * @return true if a token was available and has been consumed.
     */
    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (availableTokens >= 1.0d) {
            availableTokens -= 1.0d;
            return true;
        }
        return false;
    }

    /**
     * @return roughly how many seconds until the next token is available, for
     * the Retry-After header. Always at least 1 so clients do not hot-loop.
     */
    synchronized long secondsUntilNextToken(long nowNanos) {
        refill(nowNanos);
        if (availableTokens >= 1.0d) {
            return 0L;
        }
        double tokensNeeded = 1.0d - availableTokens;
        double nanosPerToken = (double) windowNanos / capacity;
        return Math.max(1L, (long) Math.ceil(tokensNeeded * nanosPerToken / 1_000_000_000d));
    }

    synchronized boolean isIdle(long nowNanos, long idleThresholdNanos) {
        return nowNanos - lastRefillNanos > idleThresholdNanos;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double refilled = (double) elapsed / windowNanos * capacity;
        availableTokens = Math.min(capacity, availableTokens + refilled);
        lastRefillNanos = nowNanos;
    }
}
