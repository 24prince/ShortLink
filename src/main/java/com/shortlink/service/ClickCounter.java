package com.shortlink.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shortlink.repository.ShortUrlRepository;

import jakarta.annotation.PreDestroy;

/**
 * Write-behind click counter.
 *
 * A redirect only does a Redis INCR (microseconds) instead of a database UPDATE.
 * A scheduled job moves the accumulated counts into MySQL in batches. The set of
 * "dirty" codes is drained with SPOP, which is atomic, so several application
 * instances can run the flush concurrently without double counting.
 */
@Component
public class ClickCounter {

    private static final Logger log = LoggerFactory.getLogger(ClickCounter.class);

    static final String COUNT_PREFIX = "clicks:count:";
    static final String DIRTY_SET = "clicks:dirty";
    private static final int BATCH_SIZE = 500;

    private final StringRedisTemplate redis;
    private final ShortUrlRepository repository;

    public ClickCounter(StringRedisTemplate redis, ShortUrlRepository repository) {
        this.redis = redis;
        this.repository = repository;
    }

    public void record(String code) {
        try {
            redis.opsForValue().increment(COUNT_PREFIX + code);
            redis.opsForSet().add(DIRTY_SET, code);
        } catch (DataAccessException e) {
            // Redis down: degrade to a direct (slower) DB write instead of losing the click.
            log.warn("Redis unavailable, writing click directly to DB: {}", e.getMessage());
            repository.addClicks(code, 1);
        }
    }

    /** Clicks recorded in Redis but not yet flushed to MySQL. */
    public long pending(String code) {
        try {
            String value = redis.opsForValue().get(COUNT_PREFIX + code);
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            return 0;
        }
    }

    @Scheduled(fixedDelayString = "${shortlink.click-flush-interval-ms:5000}")
    public synchronized void flush() {
        try {
            List<String> codes;
            do {
                codes = redis.opsForSet().pop(DIRTY_SET, BATCH_SIZE);
                if (codes == null || codes.isEmpty()) {
                    return;
                }
                for (String code : codes) {
                    flushOne(code);
                }
            } while (codes.size() == BATCH_SIZE);
        } catch (DataAccessException e) {
            log.warn("Click flush skipped, Redis unavailable: {}", e.getMessage());
        }
    }

    private void flushOne(String code) {
        String key = COUNT_PREFIX + code;
        Long count = null;
        try {
            String value = redis.opsForValue().getAndDelete(key);
            if (value == null) {
                return;
            }
            count = Long.parseLong(value);
            repository.addClicks(code, count);
        } catch (RuntimeException e) {
            log.warn("Failed to flush clicks for {}, restoring counter: {}", code, e.getMessage());
            if (count != null) {
                // DB write failed after we removed the counter: put it back so nothing is lost.
                redis.opsForValue().increment(key, count);
                redis.opsForSet().add(DIRTY_SET, code);
            }
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }
}
