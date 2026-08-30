package com.cheaply.auth.service;

import com.cheaply.auth.dto.AuthResponse;
import com.cheaply.auth.dto.LoginRequest;
import com.cheaply.auth.dto.LogoutRequest;
import com.cheaply.auth.dto.RefreshTokenRequest;
import com.cheaply.auth.dto.SignupRequest;
import com.cheaply.auth.dto.UserResponse;
import com.cheaply.exception.InvalidCredentialsException;
import com.cheaply.exception.UserAlreadyExistsException;
import com.cheaply.security.jwt.JwtService;
import com.cheaply.security.token.TokenDenylistService;
import com.cheaply.user.model.Role;
import com.cheaply.user.model.User;
import com.cheaply.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenDenylistService tokenDenylistService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        log.info("Processing signup for username '{}'", username);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UserAlreadyExistsException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Email '" + email + "' is already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent signups can both pass the existsBy checks above.
            // The unique constraints in the database are the real guard; this
            // turns the resulting error into the same 409 the caller expects.
            log.warn("Signup lost a race on a unique constraint for '{}'", username);
            throw new UserAlreadyExistsException("That username or email is already registered");
        }

        log.info("Registered user id={}", savedUser.getId());
        return issueTokens(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        log.info("Processing login for username '{}'", username);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (AuthenticationException e) {
            // Deliberately the same message whether the username is unknown or
            // the password is wrong, so the endpoint cannot be used to
            // enumerate accounts.
            log.warn("Failed login attempt for username '{}'", username);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        log.info("User '{}' logged in", user.getUsername());
        return issueTokens(user);
    }

    /**
     * Exchanges a refresh token for a fresh pair of tokens.
     *
     * <p>Three things are checked here that previously were not: that the token
     * parses at all (a malformed token used to escape as a 500), that it is a
     * refresh token rather than an access token, and that it has not been
     * revoked by a logout. The presented token is revoked on use, so a refresh
     * token is single-use and a stolen copy stops working as soon as the
     * legitimate client rotates it.
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        String username;
        String tokenId;
        try {
            username = jwtService.extractUsername(refreshToken);
            tokenId = jwtService.extractTokenId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Refresh rejected - token could not be parsed: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid or expired refresh token. Please log in again.");
        }

        if (tokenDenylistService.isDenylisted(tokenId)) {
            log.warn("Refresh rejected - token was revoked (user '{}')", username);
            throw new InvalidCredentialsException("This session has been logged out. Please log in again.");
        }

        if (!jwtService.isRefreshTokenValid(refreshToken, username)) {
            log.warn("Refresh rejected - not a valid refresh token (user '{}')", username);
            throw new InvalidCredentialsException("Invalid or expired refresh token. Please log in again.");
        }

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid or expired refresh token. Please log in again."));

        // Rotate: the presented token cannot be replayed after this point.
        tokenDenylistService.revoke(tokenId, jwtService.remainingValidity(refreshToken));

        log.info("Issued a rotated token pair for user '{}'", user.getUsername());
        return issueTokens(user);
    }

    /**
     * Revokes the supplied refresh token.
     *
     * <p>Access tokens stay valid until they expire, which is why their TTL was
     * cut from 24 hours to 15 minutes. Revoking the refresh token means the
     * session cannot be extended beyond that window.
     */
    public void logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();
        try {
            String tokenId = jwtService.extractTokenId(refreshToken);
            tokenDenylistService.revoke(tokenId, jwtService.remainingValidity(refreshToken));
            log.info("Logged out user '{}'", jwtService.extractUsername(refreshToken));
        } catch (JwtException | IllegalArgumentException e) {
            // Logging out with an already-invalid token is not worth surfacing
            // as an error: the desired end state is already true.
            log.debug("Logout called with an unparseable token: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AuthResponse issueTokens(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());

        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(user.getUsername(), claims))
                .refreshToken(jwtService.generateRefreshToken(user.getUsername()))
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
