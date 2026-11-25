package com.discordclone.security;

import com.discordclone.exception.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and verifies tokens. Every token says what it is for, so one kind cannot pass as another. */
@Component
public class JwtService {

    private static final String TYPE = "typ";
    private static final String SESSION = "sid";

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtProperties properties;
    private final Key key;

    public JwtService(CustomUserDetailsService customUserDetailsService, JwtProperties properties) {
        this.customUserDetailsService = customUserDetailsService;
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(Long userId, String sessionId, TokenType type) {
        Instant now = Instant.now();
        Duration lifetime = type == TokenType.ACCESS ? properties.expiration() : properties.refreshExpiration();
        return Jwts.builder()
                .setSubject(Long.toString(userId))
                // Unique per token, so that two issued in the same second still differ.
                .setId(UUID.randomUUID().toString())
                .claim(TYPE, type.name())
                .claim(SESSION, sessionId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws JwtAuthenticationException unless the token is genuine, unexpired and of the expected type
     */
    public TokenClaims verify(String token, TokenType expected) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            String sessionId = claims.get(SESSION, String.class);
            if (!expected.name().equals(claims.get(TYPE, String.class)) || sessionId == null) {
                throw new JwtAuthenticationException("Wrong kind of token");
            }
            return new TokenClaims(Long.parseLong(claims.getSubject()), sessionId,
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtAuthenticationException("Invalid or expired token", ex);
        }
    }

    public Authentication authentication(TokenClaims claims) {
        UserDetails user = customUserDetailsService.loadUserById(claims.userId());
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
