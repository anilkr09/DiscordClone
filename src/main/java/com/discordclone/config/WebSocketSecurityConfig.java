package com.discordclone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;

@Configuration
public class WebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        MessageMatcherDelegatingAuthorizationManager.Builder builder =
                new MessageMatcherDelegatingAuthorizationManager.Builder();

        builder.simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT).permitAll();
        builder.simpDestMatchers("/app/**").authenticated();
        builder.simpSubscribeDestMatchers("/topic/**").authenticated(); // Secure subscription destinations

        builder.simpSubscribeDestMatchers("/topic/admin").access(AuthorityAuthorizationManager.hasRole("ADMIN"));
//        builder.anyMessage().permitAll();
//        builder.anyMessage().denyAll();

        return builder.build();
    }
//    @Override
//    public boolean sameOriginDisabled() {
//        // Disable CSRF for WebSockets
//        return true;
//    }
    @Bean
    public AuthorizationChannelInterceptor authorizationChannelInterceptor(
            AuthorizationManager<Message<?>> messageAuthorizationManager) {
        return new AuthorizationChannelInterceptor(messageAuthorizationManager);
    }

    @Bean
    public SecurityContextChannelInterceptor securityContextChannelInterceptor() {
        return new SecurityContextChannelInterceptor();
    }
}
