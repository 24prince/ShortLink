package com.shortlink.dto;

import java.time.Instant;

public record UrlStatsResponse(
        String shortCode,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        long totalClicks) {
}
