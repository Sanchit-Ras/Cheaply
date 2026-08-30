package com.cheaply.ratelimit;

import com.cheaply.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-client throttling for the two endpoint groups that are worth abusing.
 *
 * <p>{@code POST /api/search} is unauthenticated and every cache miss forks
 * three headless Chrome processes in the scraper service, so an unthrottled
 * loop of random queries is the cheapest possible way to take the whole system
 * down. The auth endpoints are throttled separately to blunt credential
 * stuffing.
 *
 * <p>State lives in memory, which means the limit is per instance rather than
 * per cluster. That is an accepted trade for a single-instance deployment; if
 * this is ever scaled horizontally, the buckets need to move into Redis.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long IDLE_EVICTION_NANOS = TimeUnit.MINUTES.toNanos(30);
    private static final int SWEEP_EVERY_N_REQUESTS = 1_000;

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Limit limit = limitFor(request);

        if (limit == null || !properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.nanoTime();
        sweepOccasionally(now);

        String key = limit.name() + ":" + clientIdentifier(request);
        TokenBucket bucket = buckets.computeIfAbsent(
                key, k -> new TokenBucket(limit.capacity(), limit.windowNanos(), now));

        if (bucket.tryConsume(now)) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = bucket.secondsUntilNextToken(now);
        log.warn("Rate limit hit on {} {} by {} - retry in {}s",
                request.getMethod(), request.getRequestURI(), key, retryAfter);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error("Too many requests. Please retry in " + retryAfter + " second(s)."));
    }

    /**
     * @return which bucket applies to this request, or null if the path is not
     * rate limited.
     */
    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && "/api/search".equals(path)) {
            return new Limit("search", properties.getSearchCapacity(),
                    TimeUnit.SECONDS.toNanos(properties.getSearchWindowSeconds()));
        }
        if ("POST".equals(method) && path.startsWith("/api/auth/")) {
            return new Limit("auth", properties.getAuthCapacity(),
                    TimeUnit.SECONDS.toNanos(properties.getAuthWindowSeconds()));
        }
        return null;
    }

    /**
     * Identifies the caller. X-Forwarded-For is only consulted when the
     * deployment has declared that it sits behind a trusted proxy - otherwise
     * the header is attacker-controlled and the limit becomes meaningless.
     */
    private String clientIdentifier(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /**
     * Drops buckets for clients that have gone quiet, so a long-running process
     * facing a stream of distinct IPs does not accumulate entries forever.
     */
    private void sweepOccasionally(long now) {
        if (requestCounter.incrementAndGet() % SWEEP_EVERY_N_REQUESTS != 0) {
            return;
        }
        int before = buckets.size();
        buckets.entrySet().removeIf(entry -> entry.getValue().isIdle(now, IDLE_EVICTION_NANOS));
        log.debug("Rate limit bucket sweep: {} -> {}", before, buckets.size());
    }

    private record Limit(String name, int capacity, long windowNanos) {
    }
}
