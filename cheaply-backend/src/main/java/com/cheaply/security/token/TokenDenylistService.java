package com.cheaply.security.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Remembers tokens that have been explicitly revoked (currently: by logging
 * out) until they would have expired anyway.
 *
 * <p>Plain JWTs are stateless, so without this a stolen token stays valid for
 * its full lifetime and logging out is purely cosmetic on the client. Entries
 * are keyed by the token's {@code jti} and given a TTL equal to the token's
 * remaining validity, so the store never grows without bound.
 *
 * <p><strong>Failure mode:</strong> if Redis is unavailable, reads fail open -
 * the token is treated as still valid. That is a deliberate trade of strictness
 * for availability, on the reasoning that a Redis outage should not lock every
 * user out of the product. The alternative (fail closed) is a one-line change
 * in {@link #isDenylisted} if this ever guards something more sensitive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private static final String KEY_PREFIX = "cheaply:denylist:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String tokenId, Duration remainingValidity) {
        if (tokenId == null || tokenId.isBlank() || remainingValidity.isZero() || remainingValidity.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "revoked", remainingValidity);
            log.debug("Revoked token {} for {}", tokenId, remainingValidity);
        } catch (Exception e) {
            log.warn("Could not record token revocation for {}: {}", tokenId, e.getMessage());
        }
    }

    public boolean isDenylisted(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
        } catch (Exception e) {
            log.warn("Denylist lookup failed for {}: {}. Treating token as valid.", tokenId, e.getMessage());
            return false;
        }
    }
}
