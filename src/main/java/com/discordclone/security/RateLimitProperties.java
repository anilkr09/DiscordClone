package com.discordclone.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Token-bucket limits by action. {@code capacity} requests may be made at once, and the bucket
 * refills completely over {@code period}.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, Map<RateLimiter.Action, Limit> limits) {

    public record Limit(int capacity, Duration period) {
    }
}
