# Spring Security 6 WebSocket Authorization Migration

## Current State (Hand-Rolled)

The project currently uses a custom `WebSocketAuthInterceptor` (`ChannelInterceptor`) that manually handles:

| STOMP Command | Current Handling |
|---------------|------------------|
| `CONNECT` | Validates JWT from `Authorization` header, sets `accessor.setUser(auth)` |
| `SUBSCRIBE` | Checks destination format (`/topic/`, `/app/`, `/user/queue/`) |
| `SEND` | Verifies authenticated principal exists |

**Problems:**
- Imperative, hard-to-test logic in `preSend()`
- Authorization scattered between interceptor + `@MessageMapping` controllers
- Custom exception handling via `WebSocketExceptionHandler`
- No declarative destination-based rules

---

## Target State (Spring Security 6)

### Key Annotations & Classes

| Component | Purpose |
|-----------|---------|
| `@EnableWebSocketSecurity` | Enables WebSocket security configuration (Spring Security 6+) |
| `AuthorizationManager<Message<?>>` | Functional interface for declarative authorization decisions |
| `MessageSecurityMetadataSourceRegistry` | Registry for destination-pattern → authorization rules |
| `AuthorizationManagers` | Factory for common authorization managers |

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    WebSocketSecurityConfig                   │
│  @EnableWebSocketSecurity                                    │
│  @EnableWebSocketMessageBroker                               │
│  implements WebSocketMessageBrokerConfigurer                 │
├─────────────────────────────────────────────────────────────┤
│  configureMessageBroker()     → /topic, /queue, /app, /user │
│  registerStompEndpoints()     → /ws                          │
│  messageAuthorizationManager() → AuthorizationManager<Msg>   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│           AuthorizationManager<Message<?>>                   │
│  (Bean: declarative rules for destinations)                  │
├─────────────────────────────────────────────────────────────┤
│  AuthorizationManagers.anyOf(                               │
│      denyAll()          // CONNECT, etc. handled separately  │
│      patternMatcher("/user/**", authenticated()),           │
│      patternMatcher("/topic/channels/**",                   │
│          customChannelMembershipManager()),                 │
│      patternMatcher("/app/**", authenticated())             │
│  )                                                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│         Custom AuthorizationManager (lambda)                 │
│  1. Extract channelId from destination                       │
│  2. Get UserPrincipal from authentication                    │
│  3. ChannelAuthorizationService.hasAccess(channel, userId)   │
│  4. Return AuthorizationDecision.allow() / deny()            │
└─────────────────────────────────────────────────────────────┘
```

---

## CSRF on CONNECT — Critical Detail

### Default Behavior
`@EnableWebSocketSecurity` **enforces CSRF token on `CONNECT`** by default.

```
Client                          Server
  │                                │
  ├─ HTTP GET /ws?token=jwt ──────►│ (Handshake)
  │                                ├─ Requires CSRF token in header
  │                                │  (X-CSRF-TOKEN or X-XSRF-TOKEN)
  │◄─ 403 Forbidden ──────────────┤
  │                                │
```

### Why This Breaks JWT-Based Auth
- Your clients authenticate via **JWT in `Authorization: Bearer <token>` header**
- No cookie-based session → no CSRF token available
- Browser doesn't send cookies on WebSocket upgrade by default
- **Result: All connections rejected with 403**

### Solution: Disable CSRF for WebSocket Endpoint

```java
// SecurityConfig.java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ... existing config ...
        .csrf(csrf -> csrf
            .ignoringRequestMatchers("/ws/**")  // ← Critical for JWT auth
        );
    return http.build();
}
```

**Alternative (if you need CSRF for other endpoints):**
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            .ignoringRequestMatchers("/ws/**")
        );
    return http.build();
}
```

---

## Complete Implementation

### 1. WebSocketSecurityConfig.java

```java
package com.discordclone.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.messaging.EnableWebSocketSecurity;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.web.messaging.MessageAuthorizationInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.discordclone.model.Channel;
import com.discordclone.repository.ChannelRepository;

@Configuration
@EnableWebSocketSecurity
@EnableWebSocketMessageBroker
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private final ChannelAuthorizationService channelAuthService;
    private final ChannelRepository channelRepository;

    public WebSocketSecurityConfig(
            ChannelAuthorizationService channelAuthService,
            ChannelRepository channelRepository) {
        this.channelAuthService = channelAuthService;
        this.channelRepository = channelRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://discord-clone-eight-mocha.vercel.app");
    }

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        return AuthorizationManagers.anyOf(
            // Authenticated users can access user-specific destinations
            AuthorizationManagers.patternMatcher("/user/**", AuthorizationManagers.authenticated()),

            // Channel topics - custom channel membership check
            AuthorizationManagers.patternMatcher("/topic/channels/**", channelMembershipAuthorization()),

            // App destinations (send messages) - authenticated users only
            AuthorizationManagers.patternMatcher("/app/**", AuthorizationManagers.authenticated())
        );
    }

    private AuthorizationManager<Message<?>> channelMembershipAuthorization() {
        return (authenticationSupplier, messageSupplier) -> {
            try {
                Message<?> message = messageSupplier.get();
                SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
                String destination = accessor.getDestination();

                if (destination == null) {
                    return AuthorizationDecision.deny();
                }

                Long channelId = extractChannelId(destination);
                if (channelId == null) {
                    return AuthorizationDecision.deny();
                }

                var authentication = authenticationSupplier.get();
                if (authentication == null || !authentication.isAuthenticated()) {
                    return AuthorizationDecision.deny();
                }

                Object principal = authentication.getPrincipal();
                if (!(principal instanceof UserPrincipal userPrincipal)) {
                    return AuthorizationDecision.deny();
                }

                Channel channel = channelRepository.findById(channelId).orElse(null);
                if (channel == null) {
                    return AuthorizationDecision.deny();
                }

                boolean hasAccess = channelAuthService.hasAccess(channel, userPrincipal.getId());
                return hasAccess ? AuthorizationDecision.allow() : AuthorizationDecision.deny();

            } catch (Exception e) {
                return AuthorizationDecision.deny();
            }
        };
    }

    private Long extractChannelId(String destination) {
        String prefix = "/topic/channels/";
        if (!destination.startsWith(prefix)) return null;
        String afterPrefix = destination.substring(prefix.length());
        int nextSlash = afterPrefix.indexOf('/');
        String channelIdStr = nextSlash > 0 ? afterPrefix.substring(0, nextSlash) : afterPrefix;
        try {
            return Long.parseLong(channelIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

### 2. SecurityConfig.java (Add CSRF Disable)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf
            .ignoringRequestMatchers("/ws/**")  // ← Add this line
        )
        .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        // ... rest unchanged
}
```

### 3. Simplify WebSocketAuthInterceptor (Optional: Keep for Logging Only)

```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            // Only logging - authorization handled by Spring Security
            logger.debug("STOMP Command: {}", accessor.getCommand());
            logger.debug("Destination: {}", accessor.getDestination());
            if (accessor.getUser() != null) {
                logger.debug("Authenticated user: {}", accessor.getUser().getName());
            }
        }
        return message;
    }
}
```

### 4. Update WebSocketExceptionHandler

```java
@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public String handleAccessDenied(AccessDeniedException ex) {
        return "Access Denied: " + ex.getMessage();
    }

    @MessageExceptionHandler(JwtAuthenticationException.class)
    @SendToUser("/queue/errors")
    public String handleJwtException(JwtAuthenticationException ex) {
        return "Authentication Error: " + ex.getMessage();
    }
}
```

---

## Migration Checklist

- [ ] Create `WebSocketSecurityConfig` with `@EnableWebSocketSecurity`
- [ ] Implement `messageAuthorizationManager()` bean with pattern matchers
- [ ] Move channel membership logic to custom `AuthorizationManager`
- [ ] Add `.csrf().ignoringRequestMatchers("/ws/**")` to `SecurityConfig`
- [ ] Remove authorization logic from `WebSocketAuthInterceptor`
- [ ] Update `WebSocketExceptionHandler` to handle `AccessDeniedException`
- [ ] Test: Connect with JWT → Subscribe to channel → Send message
- [ ] Test: Unauthorized user tries to subscribe → 403 via `/user/queue/errors`

---

## Testing the Migration

```bash
# 1. Connect with JWT (no CSRF token needed)
wscat -c "ws://localhost:8080/ws" -H "Authorization: Bearer <jwt-token>"

# 2. Subscribe to authorized channel
SUBSCRIBE
id:sub-1
destination:/topic/channels/1/messages

# 3. Subscribe to unauthorized channel (should fail)
SUBSCRIBE
id:sub-2
destination:/topic/channels/999/messages

# 4. Send message
SEND
destination:/app/chat.send
content-length:45

{"channelId":1,"content":"Hello"}
```

---

## Benefits Summary

| Concern | Before | After |
|---------|--------|-------|
| **Authorization Rules** | Imperative `if/else` in interceptor | Declarative pattern matchers |
| **Channel Membership** | Hardcoded in interceptor | Reusable `ChannelAuthorizationService` |
| **CSRF Handling** | Manual/None | Built-in (configurable) |
| **Error Handling** | Custom `@ControllerAdvice` | Standard `AccessDeniedException` |
| **Testability** | Hard (requires full STOMP setup) | Easy (mock `AuthorizationManager`) |
| **Maintainability** | Scattered logic | Centralized in config bean |

---

## References

- [Spring Security WebSocket Reference](https://docs.spring.io/spring-security/reference/servlet/integrations/messaging.html)
- [AuthorizationManager API](https://docs.spring.io/spring-security/reference/authorization/authorization-manager.html)
- [Spring Security 6.2 WebSocket Security](https://spring.io/blog/2023/11/16/spring-security-6-2-websocket-security)