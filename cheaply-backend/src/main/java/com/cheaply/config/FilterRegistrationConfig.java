package com.cheaply.config;

import com.cheaply.ratelimit.RateLimitFilter;
import com.cheaply.security.jwt.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stops Spring Boot from auto-registering our security filters against the
 * whole servlet container.
 *
 * <p>Any {@code Filter} bean is picked up by Boot and applied to every request,
 * including ones the Spring Security chain never sees. Both of these filters
 * are added explicitly to the security chain in
 * {@link com.cheaply.security.config.SecurityConfig}, so the automatic
 * registration would be a second, unordered copy.
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> disableRateLimitFilterAutoRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
