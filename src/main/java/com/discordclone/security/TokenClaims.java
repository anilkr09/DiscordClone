package com.discordclone.security;

import java.time.Instant;

/**
 * A verified token.
 *
 * @param sessionId the login this token belongs to; shared by every token that login is issued
 */
public record TokenClaims(Long userId, String sessionId, Instant expiresAt) {
}
