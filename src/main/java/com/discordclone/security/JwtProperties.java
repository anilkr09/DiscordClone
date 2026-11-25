package com.discordclone.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret            HS512 signing key, at least 64 characters
 * @param expiration        lifetime of an access token
 * @param refreshExpiration lifetime of a refresh token
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotNull @Size(min = 64, message = "JWT_SECRET must be at least 64 characters") String secret,
        @NotNull Duration expiration,
        @NotNull Duration refreshExpiration) {
}
