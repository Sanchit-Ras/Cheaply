package com.cheaply.cache.service;

import com.cheaply.cache.dto.CachedSearch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Caches completed searches in Redis.
 *
 * <p>Values are stored as plain JSON strings and parsed back into a known type.
 * The earlier implementation used a RedisTemplate configured with Jackson's
 * default typing and a permissive subtype validator, which embeds Java class
 * names in the stored payload and instantiates whatever class the payload names
 * on read. That turns any write access to Redis into remote code execution in
 * this process; binding to one concrete type removes the capability entirely.
 *
 * <p>Every Redis failure is caught and logged rather than propagated. The cache
 * is an optimisation, and a Redis outage should slow searches down, not break
 * them.
 */
@Slf4j
@Service
public class SearchCacheService {

    private static final String KEY_PREFIX = "cheaply:search:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration defaultTtl;
    private final Duration partialTtl;

    public SearchCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${cheaply.cache.search-ttl-minutes:15}") long searchTtlMinutes,
            @Value("${cheaply.cache.partial-search-ttl-minutes:3}") long partialTtlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.defaultTtl = Duration.ofMinutes(searchTtlMinutes);
        this.partialTtl = Duration.ofMinutes(partialTtlMinutes);
    }

    public Optional<CachedSearch> get(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String key = buildKey(query);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                log.debug("Cache miss for '{}'", query);
                return Optional.empty();
            }
            log.debug("Cache hit for '{}'", query);
            return Optional.of(objectMapper.readValue(raw, CachedSearch.class));
        } catch (JsonProcessingException e) {
            // A stale entry written by an older version of the DTO. Drop it
            // rather than failing the request, and let this search repopulate.
            log.warn("Discarding unreadable cache entry '{}': {}", key, e.getMessage());
            evict(query);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis read failed for '{}': {}. Continuing without cache.", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @param partial true when at least one store failed. Partial results get a
     *                much shorter TTL so a transient outage at one store does
     *                not degrade every search for the next quarter of an hour.
     */
    public void set(String query, CachedSearch value, boolean partial) {
        if (query == null || value == null || value.productsOrEmpty().isEmpty()) {
            return;
        }

        Duration ttl = partial ? partialTtl : defaultTtl;
        String key = buildKey(query);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            log.debug("Cached {} products for '{}' (ttl {}, partial={})",
                    value.productsOrEmpty().size(), query, ttl, partial);
        } catch (Exception e) {
            log.warn("Redis write failed for '{}': {}. Result not cached.", key, e.getMessage());
        }
    }

    public void evict(String query) {
        if (query == null) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(query));
        } catch (Exception e) {
            log.warn("Redis delete failed for '{}': {}", query, e.getMessage());
        }
    }

    /**
     * Keys are a hash of the normalised query rather than the query itself.
     * User-supplied text in a key is awkward for two reasons: it can contain
     * anything up to the 120-character limit, and search terms are then legible
     * to anyone with access to the keyspace. A hash is fixed-length and opaque.
     */
    private String buildKey(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM; this branch is unreachable.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
