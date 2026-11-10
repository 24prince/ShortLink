package com.shortlink.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shortlink.config.ShortLinkProperties;
import com.shortlink.domain.ShortUrl;
import com.shortlink.dto.CreateUrlRequest;
import com.shortlink.dto.UrlStatsResponse;
import com.shortlink.exception.AliasUnavailableException;
import com.shortlink.exception.LinkExpiredException;
import com.shortlink.exception.UrlNotFoundException;
import com.shortlink.repository.ShortUrlRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ShortenerService {

    private static final Logger log = LoggerFactory.getLogger(ShortenerService.class);

    /** 62^5: shifts ids so generated codes are always at least 6 characters long. */
    private static final long ID_OFFSET = 916_132_832L;
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;
    private static final String CACHE_PREFIX = "url:";
    private static final Set<String> RESERVED = Set.of("api", "actuator", "health", "error", "admin", "static");

    private final ShortUrlRepository repository;
    private final StringRedisTemplate redis;
    private final UrlValidator validator;
    private final Base62Codec codec;
    private final ClickCounter clicks;
    private final ShortLinkProperties props;
    private final Clock clock;
    private final MeterRegistry metrics;

    public ShortenerService(ShortUrlRepository repository,
                            StringRedisTemplate redis,
                            UrlValidator validator,
                            Base62Codec codec,
                            ClickCounter clicks,
                            ShortLinkProperties props,
                            Clock clock,
                            MeterRegistry metrics) {
        this.repository = repository;
        this.redis = redis;
        this.validator = validator;
        this.codec = codec;
        this.clicks = clicks;
        this.props = props;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public ShortUrl create(CreateUrlRequest request) {
        String url = validator.normalize(request.url());
        Instant now = clock.instant();
        Instant expiresAt = request.expiresInDays() == null
                ? null
                : now.plus(Duration.ofDays(request.expiresInDays()));

        String alias = request.customAlias();
        if (alias != null && !alias.isBlank()) {
            return createWithAlias(url, alias, now, expiresAt);
        }
        return createWithGeneratedCode(url, now, expiresAt);
    }

    private ShortUrl createWithAlias(String url, String alias, Instant now, Instant expiresAt) {
        if (RESERVED.contains(alias.toLowerCase()) || repository.existsByShortCode(alias)) {
            throw new AliasUnavailableException(alias);
        }
        ShortUrl entity = new ShortUrl(url, now, expiresAt);
        entity.setShortCode(alias);
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent request that took the same alias.
            throw new AliasUnavailableException(alias);
        }
    }

    /**
     * The code is derived from the auto-increment id (Base62), so two requests can never
     * generate the same code. The only way to clash is a user-chosen alias that happens to
     * equal a future generated code, which is handled by skipping to the next id.
     */
    private ShortUrl createWithGeneratedCode(String url, Instant now, Instant expiresAt) {
        for (int attempt = 0; attempt < MAX_ALLOCATION_ATTEMPTS; attempt++) {
            ShortUrl entity = new ShortUrl(url, now, expiresAt);
            repository.saveAndFlush(entity);
            String code = codec.encode(entity.getId() + ID_OFFSET);
            if (!repository.existsByShortCode(code)) {
                entity.setShortCode(code);
                return repository.save(entity);
            }
            repository.delete(entity);
        }
        throw new IllegalStateException("Could not allocate a unique short code");
    }

    /**
     * Hot path. Cache-aside: Redis first, MySQL on a miss, then populate the cache.
     * Any Redis failure silently falls back to MySQL, so Redis is an optimisation,
     * never a single point of failure.
     */
    public String resolve(String code) {
        Optional<String> cached = cacheGet(code);
        if (cached.isPresent()) {
            metrics.counter("shortlink.cache", "result", "hit").increment();
            clicks.record(code);
            return cached.get();
        }
        metrics.counter("shortlink.cache", "result", "miss").increment();

        ShortUrl entity = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        if (entity.isExpired(clock.instant())) {
            throw new LinkExpiredException(code);
        }
        cachePut(code, entity);
        clicks.record(code);
        return entity.getOriginalUrl();
    }

    public UrlStatsResponse stats(String code) {
        ShortUrl entity = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        long total = entity.getClickCount() + clicks.pending(code);
        return new UrlStatsResponse(entity.getShortCode(), entity.getOriginalUrl(),
                entity.getCreatedAt(), entity.getExpiresAt(), total);
    }

    private Optional<String> cacheGet(String code) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(CACHE_PREFIX + code));
        } catch (DataAccessException e) {
            log.warn("Cache read failed, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void cachePut(String code, ShortUrl entity) {
        Duration ttl = props.cacheTtl();
        if (entity.getExpiresAt() != null) {
            // Never cache a link beyond its expiry time.
            Duration untilExpiry = Duration.between(clock.instant(), entity.getExpiresAt());
            if (untilExpiry.compareTo(ttl) < 0) {
                ttl = untilExpiry;
            }
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(CACHE_PREFIX + code, entity.getOriginalUrl(), ttl);
        } catch (DataAccessException e) {
            log.warn("Cache write failed: {}", e.getMessage());
        }
    }
}
