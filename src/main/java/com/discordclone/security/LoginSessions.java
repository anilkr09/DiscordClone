package com.discordclone.security;

import com.discordclone.cluster.ClusterBus;
import org.springframework.context.annotation.Lazy;
import com.discordclone.exception.JwtAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The lifecycle of a login. Redis holds the hash of the one refresh token each login may use next.
 * Using a token replaces it; presenting a replaced token means it was copied, so the whole login is
 * revoked.
 */
@Slf4j
@Service
public class LoginSessions {

    public record Tokens(Long userId, String accessToken, String refreshToken) {
    }

    private static final String PREFIX = "auth:session:";

    // 1: rotated. -1: a replaced token was presented, and the session is now gone. 0: no session.
    private static final RedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            if current ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final JwtService jwtService;
    private final JwtProperties properties;
    private final StringRedisTemplate redis;
    private final ClusterBus bus;

    public LoginSessions(JwtService jwtService, JwtProperties properties, StringRedisTemplate redis,
                         @Lazy ClusterBus bus) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.redis = redis;
        this.bus = bus;
    }

    public Tokens open(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        Tokens tokens = issue(userId, sessionId);
        redis.opsForValue().set(PREFIX + sessionId, hash(tokens.refreshToken()), properties.refreshExpiration());
        return tokens;
    }

    /** Exchanges a refresh token for new tokens; each refresh token works once. */
    public Tokens refresh(String refreshToken) {
        TokenClaims claims = jwtService.verify(refreshToken, TokenType.REFRESH);
        Tokens next = issue(claims.userId(), claims.sessionId());
        Long result = redis.execute(ROTATE, List.of(PREFIX + claims.sessionId()), hash(refreshToken),
                hash(next.refreshToken()), String.valueOf(properties.refreshExpiration().toMillis()));
        if (result == null || result != 1) {
            if (result != null && result == -1) {
                log.warn("A replaced refresh token was used for user {}; the login is revoked", claims.userId());
                bus.closeLogin(claims.sessionId());
            }
            throw new JwtAuthenticationException("This session has ended");
        }
        return next;
    }

    /** Ends the login the refresh token belongs to, including its open sockets. */
    public void close(String refreshToken) {
        TokenClaims claims = jwtService.verify(refreshToken, TokenType.REFRESH);
        redis.delete(PREFIX + claims.sessionId());
        bus.closeLogin(claims.sessionId());
    }

    public boolean isActive(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + sessionId));
    }

    private Tokens issue(Long userId, String sessionId) {
        return new Tokens(userId,
                jwtService.issue(userId, sessionId, TokenType.ACCESS),
                jwtService.issue(userId, sessionId, TokenType.REFRESH));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
