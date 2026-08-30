package com.cheaply.cache.service;

import com.cheaply.cache.dto.CachedSearch;
import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchCacheServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SearchCacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cacheService = new SearchCacheService(redisTemplate, objectMapper, 15, 3);
    }

    private CachedSearch sample() {
        return CachedSearch.builder()
                .products(List.of(ProductDto.builder()
                        .title("Tata Salt 1kg")
                        .pricePerUnit(new BigDecimal("25.00"))
                        .unit("kg")
                        .build()))
                .stores(List.of(StoreStatusDto.builder().name("Amazon").status("ok").count(1).build()))
                .build();
    }

    @Test
    @DisplayName("round-trips a cached search through JSON")
    void roundTripsThroughJson() throws Exception {
        String json = objectMapper.writeValueAsString(sample());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(json);

        Optional<CachedSearch> result = cacheService.get("salt");

        assertTrue(result.isPresent());
        assertEquals("Tata Salt 1kg", result.get().productsOrEmpty().get(0).getTitle());
        assertEquals(0, new BigDecimal("25.00").compareTo(result.get().productsOrEmpty().get(0).getPricePerUnit()));
    }

    @Test
    @DisplayName("stores complete results with the full TTL")
    void usesFullTtlForCompleteResults() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.set("salt", sample(), false);

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("stores partial results with the shorter TTL")
    void usesShortTtlForPartialResults() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.set("salt", sample(), true);

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("never caches an empty result set")
    void refusesToCacheEmptyResults() {
        cacheService.set("salt", CachedSearch.builder().products(List.of()).build(), false);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("a Redis outage degrades to a cache miss rather than an error")
    void survivesRedisOutage() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("redis is down"));

        assertTrue(cacheService.get("salt").isEmpty());
    }

    @Test
    @DisplayName("discards an entry it can no longer parse")
    void discardsUnreadableEntry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("{not valid json");

        assertTrue(cacheService.get("salt").isEmpty());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("keys are hashed, so user-supplied text never lands in the keyspace")
    void hashesKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.set("basmati rice", sample(), false);

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.argThat(key ->
                        key.startsWith("cheaply:search:") && !key.contains("basmati")),
                anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a blank query is not a cache lookup")
    void ignoresBlankQuery() {
        assertFalse(cacheService.get("   ").isPresent());
        verify(redisTemplate, never()).opsForValue();
    }
}
