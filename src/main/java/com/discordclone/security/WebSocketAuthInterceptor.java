package com.discordclone.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        logger.debug("inside presend -- presend{}",accessor);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                logger.debug("Processing WebSocket CONNECT command");

                // Extract JWT token from headers
                List<String> authHeaders = accessor.getNativeHeader("Authorization");
                if (authHeaders != null && !authHeaders.isEmpty()) {
                    String token = authHeaders.get(0);

                    if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                        token = token.substring(7); // Remove "Bearer " prefix

                        if (jwtTokenProvider.validateToken(token)) {
                            Authentication auth = jwtTokenProvider.getAuthentication(token);
                            accessor.setUser(auth);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            logger.debug("WebSocket Authentication successful for user: {}", auth.getName());

                            // Store authentication in session attributes
                            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                            if (sessionAttributes != null) {
                                sessionAttributes.put("USER_AUTH", auth);
                            }
                        } else {
                            logger.warn("Invalid JWT token in WebSocket CONNECT request");
                            return null; // Reject connection
                        }
                    }
                } else {
                    logger.warn("Missing Authorization header in WebSocket CONNECT request");
                    return null; // Reject connection
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                logger.debug("Processing WebSocket SUBSCRIBE command to destination: {}", accessor.getDestination());

                // Validate subscription destination
                String destination = accessor.getDestination();
                if (destination != null && !isValidDestination(destination)) {
                    logger.warn("Invalid subscription destination: {}", destination);
                    return null; // Reject the message
                }
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                logger.debug("Processing WebSocket SEND command to destination: {}", accessor.getDestination());

                // Ensure the sender is authenticated
                Principal principal = accessor.getUser();
                Authentication auth = (principal instanceof Authentication) ? (Authentication) principal : null;



                logger.debug("auth value is{} ",auth);
                if (auth == null || !auth.isAuthenticated()) {
                    logger.warn("Unauthenticated message send attempt");
                    return null; // Reject the message
                }
            }
        }

        return message;
    }

    private boolean isValidDestination(String destination) {
        // Add your destination validation logic here
        return destination.startsWith("/topic/") || destination.startsWith("/app/");
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            logger.debug("Client disconnected");
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex != null) {
            logger.error("Error during WebSocket message processing", ex);
        }
    }
}
