package com.cheaply.security.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-only-signing-key-not-used-anywhere-real-0123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = newService(SECRET, 3_600_000L, 604_800_000L);
    }

    private JwtService newService(String secret, long accessTtl, long refreshTtl) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "accessTokenExpirationMs", accessTtl);
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", refreshTtl);
        return service;
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("refuses to start when no secret is configured")
        void rejectsMissingSecret() {
            JwtService service = newService("", 1000L, 1000L);
            IllegalStateException error = assertThrows(IllegalStateException.class, service::validateConfiguration);
            assertTrue(error.getMessage().contains("JWT_SECRET"));
        }

        @Test
        @DisplayName("refuses to start when the secret is too short for HS256")
        void rejectsShortSecret() {
            JwtService service = newService("too-short", 1000L, 1000L);
            IllegalStateException error = assertThrows(IllegalStateException.class, service::validateConfiguration);
            assertTrue(error.getMessage().contains("at least 32 bytes"));
        }

        @Test
        @DisplayName("accepts a 32 byte secret")
        void acceptsAdequateSecret() {
            jwtService.validateConfiguration();
        }
    }

    @Nested
    @DisplayName("Issuing")
    class Issuing {

        @Test
        @DisplayName("an access token carries the subject and any extra claims")
        void accessTokenCarriesClaims() {
            String token = jwtService.generateAccessToken("testuser", Map.of("role", "ROLE_USER"));

            assertNotNull(token);
            assertEquals("testuser", jwtService.extractUsername(token));
            assertEquals(TokenType.ACCESS, jwtService.extractTokenType(token));
            assertEquals("ROLE_USER", jwtService.parseClaims(token).get("role", String.class));
        }

        @Test
        @DisplayName("every token gets a unique id so it can be revoked individually")
        void tokensHaveUniqueIds() {
            String first = jwtService.generateRefreshToken("testuser");
            String second = jwtService.generateRefreshToken("testuser");

            assertNotNull(jwtService.extractTokenId(first));
            assertNotEquals(jwtService.extractTokenId(first), jwtService.extractTokenId(second));
        }

        @Test
        @DisplayName("remaining validity reflects the configured TTL")
        void remainingValidityTracksTtl() {
            Duration remaining = jwtService.remainingValidity(jwtService.generateAccessToken("testuser"));

            assertTrue(remaining.toMinutes() > 55, "expected close to an hour, got " + remaining);
            assertTrue(remaining.toMinutes() <= 60);
        }
    }

    @Nested
    @DisplayName("Verifying")
    class Verifying {

        @Test
        @DisplayName("a valid access token is accepted for the matching user")
        void acceptsValidAccessToken() {
            String token = jwtService.generateAccessToken("validuser");
            assertTrue(jwtService.isAccessTokenValid(token, "validuser"));
        }

        @Test
        @DisplayName("a token is rejected for a different user")
        void rejectsWrongUser() {
            String token = jwtService.generateAccessToken("validuser");
            assertFalse(jwtService.isAccessTokenValid(token, "someoneelse"));
        }

        @Test
        @DisplayName("an access token is NOT accepted as a refresh token")
        void accessTokenIsNotARefreshToken() {
            String accessToken = jwtService.generateAccessToken("testuser");

            assertTrue(jwtService.isAccessTokenValid(accessToken, "testuser"));
            assertFalse(jwtService.isRefreshTokenValid(accessToken, "testuser"),
                    "an access token replayed at the refresh endpoint would grant an endless session");
        }

        @Test
        @DisplayName("a refresh token is NOT accepted as an API credential")
        void refreshTokenIsNotAnAccessToken() {
            String refreshToken = jwtService.generateRefreshToken("testuser");

            assertTrue(jwtService.isRefreshTokenValid(refreshToken, "testuser"));
            assertFalse(jwtService.isAccessTokenValid(refreshToken, "testuser"));
        }

        @Test
        @DisplayName("an expired token is rejected rather than throwing")
        void rejectsExpiredToken() {
            JwtService shortLived = newService(SECRET, -1000L, -1000L);
            String expired = shortLived.generateAccessToken("testuser");

            assertFalse(jwtService.isAccessTokenValid(expired, "testuser"));
        }

        @Test
        @DisplayName("a token signed with a different key is rejected")
        void rejectsForeignSignature() {
            JwtService otherIssuer = newService(
                    "0000000000000000000000000000000000000000000000000000000000000000", 3_600_000L, 3_600_000L);
            String foreign = otherIssuer.generateAccessToken("testuser");

            assertFalse(jwtService.isAccessTokenValid(foreign, "testuser"));
        }

        @Test
        @DisplayName("a malformed token is rejected rather than throwing")
        void rejectsMalformedToken() {
            assertFalse(jwtService.isAccessTokenValid("not-a-jwt", "testuser"));
        }

        @Test
        @DisplayName("parseClaims still throws so callers can distinguish causes")
        void parseClaimsThrowsOnGarbage() {
            assertThrows(JwtException.class, () -> jwtService.parseClaims("not-a-jwt"));
        }
    }
}
