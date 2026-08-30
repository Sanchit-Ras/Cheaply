package com.cheaply.security.jwt;

import com.cheaply.security.token.TokenDenylistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a valid {@code Authorization: Bearer ...} header into an authenticated
 * SecurityContext.
 *
 * <p>The filter never rejects a request itself; it either authenticates or
 * leaves the context empty and lets the authorization rules decide. What it
 * does guarantee is that a request is never left half-authenticated: any
 * failure clears the context before continuing, so a rejected token can't
 * inherit authentication from anywhere else on the thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenDenylistService tokenDenylistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = parseJwt(request);

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticate(jwt, request);
            } catch (JwtException | IllegalArgumentException e) {
                // Malformed, expired or wrongly signed token. Not an error
                // condition for the server - the request simply stays
                // anonymous and the entry point turns that into a 401.
                SecurityContextHolder.clearContext();
                log.debug("Rejected bearer token on {} {}: {}",
                        request.getMethod(), request.getRequestURI(), e.getMessage());
            } catch (UsernameNotFoundException e) {
                // Correctly signed token for a user that no longer exists.
                SecurityContextHolder.clearContext();
                log.debug("Bearer token references unknown user: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        if (jwtService.extractTokenType(jwt) != TokenType.ACCESS) {
            log.debug("Refusing to authenticate a non-access token");
            return;
        }

        if (tokenDenylistService.isDenylisted(jwtService.extractTokenId(jwt))) {
            log.debug("Refusing a revoked token");
            return;
        }

        String username = jwtService.extractUsername(jwt);
        if (username == null) {
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isAccessTokenValid(jwt, userDetails)) {
            return;
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith(BEARER_PREFIX)) {
            String token = headerAuth.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
