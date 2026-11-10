package com.shortlink.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.shortlink.config.ShortLinkProperties.Rule;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Distributed token-bucket rate limiter. All state lives in Redis and every decision is
 * made by one Lua script, so the limit holds across any number of application instances
 * and there is no read-modify-write race between them.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final MeterRegistry metrics;

    @SuppressWarnings("rawtypes")
    public RateLimiter(StringRedisTemplate redis, RedisScript<List> script, MeterRegistry metrics) {
        this.redis = redis;
        this.script = script;
        this.metrics = metrics;
    }

    public RateLimitResult tryAcquire(String bucket, String clientId, Rule rule) {
        String key = "rl:" + bucket + ":" + clientId;
        try {
            List<?> result = redis.execute(
                    script,
                    List.of(key),
                    String.valueOf(rule.capacity()),
                    String.valueOf(rule.refillPerSecond()),
                    "1");

            boolean allowed = ((Number) result.get(0)).longValue() == 1L;
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfter = ((Number) result.get(2)).longValue();

            metrics.counter("shortlink.ratelimit", "bucket", bucket,
                    "result", allowed ? "allowed" : "blocked").increment();
            return new RateLimitResult(allowed, rule.capacity(), remaining, retryAfter);
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable, failing open: {}", e.getMessage());
            metrics.counter("shortlink.ratelimit", "bucket", bucket, "result", "fail_open").increment();
            return RateLimitResult.failOpen(rule.capacity());
        }
    }
}
