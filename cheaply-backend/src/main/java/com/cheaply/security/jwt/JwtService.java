package com.cheaply.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Issues and verifies the application's JSON Web Tokens.
 *
 * <p>Every token carries three things beyond the standard claims: a
 * {@code typ} claim naming it an access or refresh token, a {@code jti}
 * identifier so an individual token can be revoked at logout, and the subject
 * (the username). Verification always checks the signature, the expiry and the
 * type together.
 */
@Slf4j
@Service
public class JwtService {

    /**
     * HS256 requires a key of at least 256 bits. A shorter secret would either
     * be rejected by the JJWT library at runtime or be trivially brute-forced.
     */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${cheaply.jwt.secret:}")
    private String secretKey;

    @Value("${cheaply.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${cheaply.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    /**
     * Refuses to start the application when the signing secret is missing or
     * too weak. The secret previously had a working hard-coded default in three
     * separate places, so any deployment that forgot to set JWT_SECRET was
     * signing tokens with a key published in the source repository.
     */
    @PostConstruct
    void validateConfiguration() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Generate one with 'openssl rand -hex 32' "
                            + "and provide it via the JWT_SECRET environment variable. "
                            + "There is deliberately no default.");
        }
        int length = secretKey.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; got " + length + " bytes.");
        }
        log.info("JWT signing key accepted ({} bytes). Access TTL {}, refresh TTL {}.",
                length,
                Duration.ofMillis(accessTokenExpirationMs),
                Duration.ofMillis(refreshTokenExpirationMs));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // Issuing
    // -----------------------------------------------------------------------

    public String generateAccessToken(String username, Map<String, Object> extraClaims) {
        return buildToken(extraClaims, username, TokenType.ACCESS, accessTokenExpirationMs);
    }

    public String generateAccessToken(String username) {
        return generateAccessToken(username, new HashMap<>());
    }

    public String generateRefreshToken(String username) {
        return buildToken(new HashMap<>(), username, TokenType.REFRESH, refreshTokenExpirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, TokenType type, long expirationMs) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(TokenType.CLAIM, type.claimValue());

        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(getSigningKey())
                .compact();
    }

    // -----------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------

    /**
     * Parses and cryptographically verifies a token.
     *
     * @throws JwtException if the signature is wrong, the token is malformed,
     *                      or it has expired. Callers must handle this rather
     *                      than let it escape as a 500.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public TokenType extractTokenType(String token) {
        return TokenType.fromClaim(extractClaim(token, claims -> claims.get(TokenType.CLAIM, String.class)));
    }

    /**
     * How long the given token remains valid, or {@link Duration#ZERO} if it
     * has already expired. Used to size a revocation entry's TTL so revoked
     * tokens are not remembered for longer than they could have been used.
     */
    public Duration remainingValidity(String token) {
        long millis = extractExpiration(token).getTime() - System.currentTimeMillis();
        return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
    }

    // -----------------------------------------------------------------------
    // Verifying
    // -----------------------------------------------------------------------

    /**
     * Validates a token for use as an API credential: correct signature, not
     * expired, belongs to this user, and is an access token rather than a
     * refresh token.
     */
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        return isAccessTokenValid(token, userDetails.getUsername());
    }

    public boolean isAccessTokenValid(String token, String expectedUsername) {
        return isTokenValid(token, expectedUsername, TokenType.ACCESS);
    }

    /**
     * Validates a token for use at the refresh endpoint. Deliberately separate
     * from {@link #isAccessTokenValid}: an access token must never be accepted
     * here, and a refresh token must never authenticate an ordinary request.
     */
    public boolean isRefreshTokenValid(String token, String expectedUsername) {
        return isTokenValid(token, expectedUsername, TokenType.REFRESH);
    }

    private boolean isTokenValid(String token, String expectedUsername, TokenType requiredType) {
        try {
            Claims claims = parseClaims(token);
            TokenType actualType = TokenType.fromClaim(claims.get(TokenType.CLAIM, String.class));
            if (actualType != requiredType) {
                log.debug("Token rejected for '{}': expected {} but got {}",
                        expectedUsername, requiredType, actualType);
                return false;
            }
            return expectedUsername != null
                    && expectedUsername.equals(claims.getSubject())
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rejected: {}", e.getMessage());
            return false;
        }
    }
}
