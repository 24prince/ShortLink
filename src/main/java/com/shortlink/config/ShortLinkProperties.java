package com.shortlink.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code shortlink.*} section of application.yml.
 */
@ConfigurationProperties(prefix = "shortlink")
public record ShortLinkProperties(
        String baseUrl,
        boolean trustProxy,
        Duration cacheTtl,
        RateLimits rateLimit) {

    /** One token bucket: burst size and sustained refill rate. */
    public record Rule(int capacity, double refillPerSecond) {
    }

    public record RateLimits(Rule create, Rule redirect) {
    }
}
