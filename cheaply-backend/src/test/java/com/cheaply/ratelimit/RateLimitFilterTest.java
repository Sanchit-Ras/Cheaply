package com.cheaply.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setSearchCapacity(3);
        properties.setSearchWindowSeconds(60);
        properties.setAuthCapacity(2);
        properties.setAuthWindowSeconds(60);
        filter = new RateLimitFilter(properties, new ObjectMapper());
    }

    private MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(method, uri, ip), response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("allows requests up to the configured capacity")
    void allowsUpToCapacity() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertEquals(200, call("POST", "/api/search", "10.0.0.1").getStatus(),
                    "request " + i + " should have been allowed");
        }
    }

    @Test
    @DisplayName("returns 429 with Retry-After once the bucket is empty")
    void rejectsBeyondCapacity() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/search", "10.0.0.2");
        }

        MockHttpServletResponse response = call("POST", "/api/search", "10.0.0.2");

        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too many requests"));
    }

    @Test
    @DisplayName("limits each client independently")
    void limitsPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/search", "10.0.0.3");
        }

        assertEquals(429, call("POST", "/api/search", "10.0.0.3").getStatus());
        assertEquals(200, call("POST", "/api/search", "10.0.0.4").getStatus());
    }

    @Test
    @DisplayName("search and auth budgets are separate")
    void keepsBucketsSeparate() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/search", "10.0.0.5");
        }

        assertEquals(429, call("POST", "/api/search", "10.0.0.5").getStatus());
        assertEquals(200, call("POST", "/api/auth/login", "10.0.0.5").getStatus());
    }

    @Test
    @DisplayName("leaves unrelated endpoints alone")
    void ignoresOtherPaths() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertEquals(200, call("GET", "/api/history", "10.0.0.6").getStatus());
        }
    }

    @Test
    @DisplayName("passes everything through when disabled")
    void respectsDisabledFlag() throws Exception {
        properties.setEnabled(false);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request("POST", "/api/search", "10.0.0.7"), new MockHttpServletResponse(), chain);
        }

        verify(chain, times(10)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("ignores X-Forwarded-For unless the deployment says it is behind a trusted proxy")
    void ignoresSpoofableForwardedHeaderByDefault() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = request("POST", "/api/search", "10.0.0.8");
            request.addHeader("X-Forwarded-For", "1.2.3." + i);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest request = request("POST", "/api/search", "10.0.0.8");
        request.addHeader("X-Forwarded-For", "1.2.3.99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus(),
                "rotating a spoofable header must not reset the limit");
    }

    @Test
    @DisplayName("honours X-Forwarded-For when explicitly trusted")
    void usesForwardedHeaderWhenTrusted() throws Exception {
        properties.setTrustForwardedFor(true);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = request("POST", "/api/search", "10.0.0.9");
            request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.9");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest other = request("POST", "/api/search", "10.0.0.9");
        other.addHeader("X-Forwarded-For", "203.0.113.6, 10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(other, response, new MockFilterChain());

        assertEquals(200, response.getStatus(), "a different real client should have its own budget");
    }
}
