package com.cheaply.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tuning for {@link RateLimitFilter}. Two separate buckets: one for the
 * expensive search endpoint, one for the credential endpoints.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cheaply.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Requests allowed per window against POST /api/search. */
    private int searchCapacity = 10;
    private int searchWindowSeconds = 60;

    /** Requests allowed per window against the login / signup / refresh endpoints. */
    private int authCapacity = 5;
    private int authWindowSeconds = 60;

    /**
     * Whether to believe the X-Forwarded-For header when identifying a client.
     * Only enable this when the service genuinely sits behind a trusted proxy,
     * otherwise any caller can spoof the header and defeat the limit entirely.
     */
    private boolean trustForwardedFor = false;
}
