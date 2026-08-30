package com.cheaply.auth.service;

import com.cheaply.auth.dto.AuthResponse;
import com.cheaply.auth.dto.LoginRequest;
import com.cheaply.auth.dto.LogoutRequest;
import com.cheaply.auth.dto.RefreshTokenRequest;
import com.cheaply.auth.dto.SignupRequest;
import com.cheaply.exception.InvalidCredentialsException;
import com.cheaply.exception.UserAlreadyExistsException;
import com.cheaply.security.jwt.JwtService;
import com.cheaply.security.token.TokenDenylistService;
import com.cheaply.user.model.Role;
import com.cheaply.user.model.User;
import com.cheaply.user.repository.UserRepository;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenDenylistService tokenDenylistService;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("hashed")
                .role(Role.ROLE_USER)
                .build();
    }

    private void stubTokenIssuing() {
        when(jwtService.generateAccessToken(anyString(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
    }

    @Test
    @DisplayName("signup creates the user and returns a token pair")
    void signupSucceeds() {
        SignupRequest request = SignupRequest.builder()
                .username("newuser").email("New@Example.com").password("password1").build();

        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        stubTokenIssuing();

        AuthResponse response = authService.signup(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    @DisplayName("signup rejects a username that differs only by case")
    void signupRejectsCaseVariantUsername() {
        SignupRequest request = SignupRequest.builder()
                .username("TestUser").email("other@example.com").password("password1").build();

        when(userRepository.existsByUsernameIgnoreCase("TestUser")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.signup(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("signup turns a lost unique-constraint race into a 409, not a 500")
    void signupHandlesConcurrentInsert() {
        SignupRequest request = SignupRequest.builder()
                .username("newuser").email("new@example.com").password("password1").build();

        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(UserAlreadyExistsException.class, () -> authService.signup(request));
    }

    @Test
    @DisplayName("login returns a token pair for valid credentials")
    void loginSucceeds() {
        LoginRequest request = LoginRequest.builder().username("testuser").password("password1").build();

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(existingUser));
        stubTokenIssuing();

        AuthResponse response = authService.login(request);

        assertNotNull(response.getAccessToken());
        assertEquals("testuser", response.getUsername());
    }

    @Test
    @DisplayName("login gives the same message for a bad password and an unknown user")
    void loginDoesNotRevealWhetherTheUserExists() {
        LoginRequest request = LoginRequest.builder().username("testuser").password("wrong").build();

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        InvalidCredentialsException error =
                assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Invalid username or password", error.getMessage());
    }

    @Test
    @DisplayName("refresh rotates the token pair and revokes the token it was given")
    void refreshRotatesAndRevokes() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("old-refresh").build();

        when(jwtService.extractUsername("old-refresh")).thenReturn("testuser");
        when(jwtService.extractTokenId("old-refresh")).thenReturn("jti-1");
        when(tokenDenylistService.isDenylisted("jti-1")).thenReturn(false);
        when(jwtService.isRefreshTokenValid("old-refresh", "testuser")).thenReturn(true);
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(existingUser));
        when(jwtService.remainingValidity("old-refresh")).thenReturn(Duration.ofDays(3));
        stubTokenIssuing();

        AuthResponse response = authService.refreshToken(request);

        assertEquals("access-token", response.getAccessToken());
        verify(tokenDenylistService).revoke(eq("jti-1"), eq(Duration.ofDays(3)));
    }

    @Test
    @DisplayName("refresh rejects an access token presented as a refresh token")
    void refreshRejectsAccessToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("an-access-token").build();

        when(jwtService.extractUsername("an-access-token")).thenReturn("testuser");
        when(jwtService.extractTokenId("an-access-token")).thenReturn("jti-2");
        when(tokenDenylistService.isDenylisted("jti-2")).thenReturn(false);
        when(jwtService.isRefreshTokenValid("an-access-token", "testuser")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.refreshToken(request));
        verify(userRepository, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("refresh rejects a token that has already been used or logged out")
    void refreshRejectsRevokedToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("used-refresh").build();

        when(jwtService.extractUsername("used-refresh")).thenReturn("testuser");
        when(jwtService.extractTokenId("used-refresh")).thenReturn("jti-3");
        when(tokenDenylistService.isDenylisted("jti-3")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("refresh answers a malformed token with 401 rather than letting it become a 500")
    void refreshRejectsMalformedToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("garbage").build();

        when(jwtService.extractUsername("garbage")).thenThrow(new MalformedJwtException("bad token"));

        assertThrows(InvalidCredentialsException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logoutRevokesToken() {
        when(jwtService.extractTokenId("refresh")).thenReturn("jti-4");
        when(jwtService.remainingValidity("refresh")).thenReturn(Duration.ofDays(1));

        authService.logout(LogoutRequest.builder().refreshToken("refresh").build());

        verify(tokenDenylistService).revoke("jti-4", Duration.ofDays(1));
    }

    @Test
    @DisplayName("logout with an unusable token succeeds quietly")
    void logoutIsIdempotent() {
        when(jwtService.extractTokenId("garbage")).thenThrow(new MalformedJwtException("bad token"));

        authService.logout(LogoutRequest.builder().refreshToken("garbage").build());

        verify(tokenDenylistService, never()).revoke(anyString(), any());
    }
}
