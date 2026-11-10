package com.shortlink.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.shortlink.config.ShortLinkProperties;
import com.shortlink.config.ShortLinkProperties.RateLimits;
import com.shortlink.config.ShortLinkProperties.Rule;

import jakarta.servlet.FilterChain;

class RateLimitFilterTest {

    private final Rule createRule = new Rule(5, 1.0);
    private final Rule redirectRule = new Rule(100, 50.0);

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = mock(RateLimiter.class);
    }

    private RateLimitFilter filter(boolean trustProxy) {
        ShortLinkProperties props = new ShortLinkProperties(
                "http://localhost", trustProxy, Duration.ofHours(1),
                new RateLimits(createRule, redirectRule));
        return new RateLimitFilter(limiter, props);
    }

    @Test
    void blocksWith429AndRetryAfterWhenBucketIsEmpty() throws Exception {
        when(limiter.tryAcquire(eq("create"), any(), eq(createRule)))
                .thenReturn(new RateLimitResult(false, 5, 0, 7));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/urls");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(false).doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("7", response.getHeader("Retry-After"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void letsAllowedRequestsThroughWithRateLimitHeaders() throws Exception {
        when(limiter.tryAcquire(eq("redirect"), any(), eq(redirectRule)))
                .thenReturn(new RateLimitResult(true, 100, 99, 0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(false).doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("99", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("100", response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    void ignoresRequestsThatAreNotRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/urls/abc1234/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(false).doFilter(request, response, new MockFilterChain());

        verifyNoInteractions(limiter);
        assertEquals(200, response.getStatus());
    }

    @Test
    void usesForwardedForOnlyWhenProxyIsTrusted() throws Exception {
        when(limiter.tryAcquire(any(), any(), any()))
                .thenReturn(new RateLimitResult(true, 5, 4, 0));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/urls");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        filter(true).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        verify(limiter).tryAcquire("create", "203.0.113.9", createRule);

        filter(false).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        verify(limiter).tryAcquire("create", "10.0.0.1", createRule);
    }
}
