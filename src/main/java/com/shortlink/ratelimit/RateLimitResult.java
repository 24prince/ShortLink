package com.shortlink.ratelimit;

public record RateLimitResult(boolean allowed, long limit, long remaining, long retryAfterSeconds) {

    /** Used when Redis is unreachable: we prefer serving traffic over blocking everyone. */
    public static RateLimitResult failOpen(long limit) {
        return new RateLimitResult(true, limit, limit, 0);
    }
}
