package com.shortlink.ratelimit;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shortlink.config.ShortLinkProperties;
import com.shortlink.config.ShortLinkProperties.Rule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies a per-client token bucket before the request reaches any controller.
 * Creating links and following links have separate buckets because their
 * acceptable rates differ by orders of magnitude.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CREATE_PATH = "/api/v1/urls";
    private static final Pattern REDIRECT_PATH = Pattern.compile("^/[A-Za-z0-9_-]{4,30}$");

    private final RateLimiter limiter;
    private final ShortLinkProperties props;

    public RateLimitFilter(RateLimiter limiter, ShortLinkProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String bucket = null;
        Rule rule = null;

        if ("POST".equals(request.getMethod()) && CREATE_PATH.equals(path)) {
            bucket = "create";
            rule = props.rateLimit().create();
        } else if ("GET".equals(request.getMethod()) && REDIRECT_PATH.matcher(path).matches()) {
            bucket = "redirect";
            rule = props.rateLimit().redirect();
        }

        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitResult result = limiter.tryAcquire(bucket, clientIp(request), rule);
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/problem+json");
            response.getWriter().write(String.format(
                    "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                            + "\"detail\":\"Rate limit exceeded. Retry after %d seconds.\"}",
                    result.retryAfterSeconds()));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * X-Forwarded-For is trivially spoofable, so it is only honoured when the service
     * is explicitly configured to run behind a trusted reverse proxy.
     */
    private String clientIp(HttpServletRequest request) {
        if (props.trustProxy()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
