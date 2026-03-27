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
    import org.springframework.stereotype.Component;
    import org.springframework.util.StringUtils;

    import java.security.Principal;
    import java.util.List;

    @Component
    public class WebSocketAuthInterceptor implements ChannelInterceptor {
        private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

        private final JwtService jwtService;

        public WebSocketAuthInterceptor(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            logger.debug("inside presend -- presend{}", accessor);
    //    try {
            if (accessor != null) {
//                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
//                    logger.debug("Processing WebSocket CONNECT command");
//                    logger.debug("WS CONNECT principal.getName() = {}", accessor.getUser().getName());
//
//                    // Extract JWT token from headers
//                    List<String> authHeaders = accessor.getNativeHeader("Authorization");
//                    if (authHeaders != null && !authHeaders.isEmpty()) {
//                        String token = authHeaders.get(0);
//
//                        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
//                            token = token.substring(7); // Remove "Bearer " prefix
//                            logger.debug("token jwt value : {}", token);
//
//                            if (jwtTokenProvider.validateToken(token)) {
//                                Authentication auth = jwtTokenProvider.getAuthentication(token);
//                                accessor.setUser(auth);
//                                logger.debug("WebSocket Authentication successful for user: {}", auth.getName());
//
//
//                            } else {
//                                logger.warn("Invalid JWT token in WebSocket CONNECT request");
//                                return null; // Reject connection
//                            }
//                        }
//                    } else {
//                        logger.warn("Missing Authorization header in WebSocket CONNECT request");
//                        return null; // Reject connection
//                    }
//                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {

                    logger.debug("Processing WebSocket CONNECT command");

                    List<String> authHeaders = accessor.getNativeHeader("Authorization");
                    if (authHeaders == null || authHeaders.isEmpty()) {
                        logger.warn("Missing Authorization header in WebSocket CONNECT request");
                        return null;
                    }

                    String token = authHeaders.get(0);
                    if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
                        logger.warn("Invalid Authorization header format");
                        return null;
                    }

                    token = token.substring(7); // remove "Bearer "

                    if (!jwtService.validateToken(token)) {
                        logger.warn("Invalid JWT token in WebSocket CONNECT request");
                        return null;
                    }

                    Authentication auth = jwtService.getAuthentication(token);

                    // 🔥 SET USER FIRST
                    accessor.setUser(auth);

                    // 🔥 THEN LOG
                    logger.info(
                            "WS CONNECT principal.getName() = {}",
                            auth.getName()
                    );

                    logger.debug("WebSocket Authentication successful for user: {}", auth.getName());
                }




                else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    logger.debug("Processing WebSocket SUBSCRIBE command to destination: {}", accessor.getDestination());

                    // Validate subscription destination
                    String destination = accessor.getDestination();
                    Authentication authentication = (Authentication) accessor.getUser();
//                    if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
                        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
//                    }

                    if (destination != null && !isValidDestination(destination)) {
                        logger.warn("Invalid subscription destination: {}", destination);
                        return null; // Reject the message
                    }

//                     Extract channelId from destination (e.g., "/topic/channels/1/messages")
//                    if (destination.startsWith("/topic/channels/")) {
//                        Long channelId = Long.valueOf(destination.substring("/topic/channels/".length(), destination.lastIndexOf("/")));
//
//                        // Now you can authorize the user based on the channelId
//                        if (!channelService.checkUserIsMember(channelId,currentUser.getId() )) {
//                            logger.warn("User {} not authorized to subscribe to channel {}",currentUser.getId(), channelId);
//                            return null; // Reject if user doesn't have access to the channel
//                        }
//                    }


                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    logger.debug("Processing WebSocket SEND command to destination: {}", accessor.getDestination());

                    // Ensure the sender is authenticated
                    Principal principal = accessor.getUser();
                    Authentication auth = (principal instanceof Authentication) ? (Authentication) principal : null;


                    logger.debug("auth value is{} ", auth.getName());
                    if (auth == null || !auth.isAuthenticated()) {
                        logger.warn("Unauthenticated message send attempt");
                        return null; // Reject the message
                    }
                }
            }


            return message;
    //    }

    //       catch (JwtAuthenticationException e) {
    //
    //
    //            // Convert to STOMP ERROR frame
    //
    //
    //            logger.warn("JWT Authentication failed: {} - Session ID: {}", e.getMessage(),
    //                    accessor.getSessionId(), e);
    //            StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
    //            errorAccessor.setMessage(e.getMessage());
    //            errorAccessor.setSessionId(accessor.getSessionId());
    //
    //            // Add a more specific error code
    //            errorAccessor.setHeader("message", e.getMessage());
    //            errorAccessor.setHeader("error-type", "AUTH_ERROR");
    //
    //            return MessageBuilder.createMessage(
    //                    e.getMessage().getBytes(StandardCharsets.UTF_8),
    //                    errorAccessor.getMessageHeaders());
    //        }
        }

        private boolean isValidDestination(String destination) {
            // Add your destination validation logic here
            return destination.startsWith("/topic/")
                    || destination.startsWith("/app/")
                    || destination.startsWith("/user/queue/");
        }

        @Override
        public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                logger.debug("Client disconnected");
            }
        }

        @Override
        public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
            logger.info("after send completion");
            if (ex != null) {
                logger.error("Error during WebSocket message processing", ex);
            }
        }
    }
