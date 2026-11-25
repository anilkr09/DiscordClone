package com.discordclone.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Attributes of the refresh cookie. {@code sameSite} must be {@code None} when the web client is
 * served from a different site than the API, which also requires {@code secure}.
 */
@ConfigurationProperties("app.auth.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure, @DefaultValue("Strict") String sameSite) {
}
