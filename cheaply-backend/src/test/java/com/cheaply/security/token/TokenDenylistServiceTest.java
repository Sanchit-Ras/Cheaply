package com.cheaply.security.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenDenylistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenDenylistService denylistService;

    @Test
    @DisplayName("stores the revocation with a TTL matching the token's remaining life")
    void revokeStoresWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        denylistService.revoke("token-id", Duration.ofMinutes(5));

        verify(valueOperations).set(eq("cheaply:denylist:token-id"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("does not store a revocation for an already-expired token")
    void ignoresExpiredToken() {
        denylistService.revoke("token-id", Duration.ZERO);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("reports a revoked token as denylisted")
    void detectsRevokedToken() {
        when(redisTemplate.hasKey("cheaply:denylist:token-id")).thenReturn(true);

        assertTrue(denylistService.isDenylisted("token-id"));
    }

    @Test
    @DisplayName("fails open when Redis is unreachable, so an outage does not lock everyone out")
    void failsOpenOnRedisOutage() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis is down"));

        assertFalse(denylistService.isDenylisted("token-id"));
    }

    @Test
    @DisplayName("treats a missing token id as not revoked without touching Redis")
    void ignoresBlankTokenId() {
        assertFalse(denylistService.isDenylisted("  "));
        verify(redisTemplate, never()).hasKey(anyString());
    }
}
