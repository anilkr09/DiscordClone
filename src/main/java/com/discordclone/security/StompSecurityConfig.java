package com.discordclone.security;

import java.util.function.BiPredicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import java.util.function.Supplier;

/**
 * Destination rules for inbound STOMP frames. Anything not listed here is rejected, so a new
 * destination starts out closed. In particular clients can send only to {@code /app/**}.
 */
@Slf4j
@Configuration
@EnableWebSocketSecurity
public class StompSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages,
            PermissionService permissions) {
        messages
                .simpTypeMatchers(SimpMessageType.CONNECT).authenticated()
                .simpTypeMatchers(SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT).permitAll()
                .simpMessageDestMatchers("/app/**").authenticated()
                .simpSubscribeDestMatchers("/topic/channels/{channelId}/messages")
                        .access(member("channelId", permissions::isChannelMember))
                .simpSubscribeDestMatchers("/topic/servers/{serverId}/channels")
                        .access(member("serverId", permissions::isServerMember))
                .simpSubscribeDestMatchers("/user/queue/messages", "/user/queue/friends",
                        "/user/queue/presence", "/user/queue/errors").authenticated()
                .anyMessage().denyAll();
        return messages.build();
    }

    private AuthorizationManager<MessageAuthorizationContext<?>> member(
            String variable, BiPredicate<Long, Long> isMember) {
        return (Supplier<Authentication> authentication, MessageAuthorizationContext<?> context) -> {
            if (!(authentication.get().getPrincipal() instanceof UserPrincipal user)) {
                return new AuthorizationDecision(false);
            }
            // The broker accepts patterns such as /topic/channels/*/messages; only a plain ID passes.
            String id = context.getVariables().get(variable);
            if (id == null || !id.matches("\\d{1,18}")) {
                return new AuthorizationDecision(false);
            }
            try {
                return new AuthorizationDecision(isMember.test(Long.valueOf(id), user.getId()));
            } catch (RuntimeException ex) {
                log.debug("Subscription refused for {} {}: {}", variable, id, ex.getMessage());
                return new AuthorizationDecision(false);
            }
        };
    }

    /**
     * Replaces Spring Security's CSRF check on CONNECT with a no-op, deliberately: the client
     * authenticates with a bearer token in a STOMP header, which a cross-site page cannot supply.
     */
    @Bean
    public ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {
        };
    }
}
