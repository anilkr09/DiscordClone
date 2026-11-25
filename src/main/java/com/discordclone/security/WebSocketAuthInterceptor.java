package com.discordclone.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Authenticates the STOMP session from the access token on CONNECT and refuses frames once that
 * token has expired. Destination rules live in {@link StompSecurityConfig}.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String EXPIRES_AT = "tokenExpiresAt";

    private final JwtService jwtService;
    private final LoginSessions loginSessions;
    private final StompSessionRegistry sockets;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            return message;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith(BEARER)) {
                throw new BadCredentialsException("Missing bearer token on CONNECT");
            }
            TokenClaims claims = jwtService.verify(header.substring(BEARER.length()), TokenType.ACCESS);
            if (!loginSessions.isActive(claims.sessionId())) {
                throw new CredentialsExpiredException("This session has ended");
            }
            accessor.setUser(jwtService.authentication(claims));
            if (attributes != null) {
                attributes.put(EXPIRES_AT, claims.expiresAt());
            }
            sockets.bind(accessor.getSessionId(), claims.sessionId(), claims.expiresAt());
        } else if (attributes != null && attributes.get(EXPIRES_AT) instanceof Instant expiresAt
                && Instant.now().isAfter(expiresAt)) {
            throw new CredentialsExpiredException("The access token has expired");
        }
        return message;
    }
}
