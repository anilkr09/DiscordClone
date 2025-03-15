package com.discordclone.config;

import com.discordclone.security.JwtHandshakeInterceptor;
import com.discordclone.security.WebSocketAuthInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
//@EnableWebSocketSecurity
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final AuthorizationChannelInterceptor authorizationChannelInterceptor ;
    private final ChannelInterceptor securityContextChannelInterceptor;
    private final ApplicationContext applicationContext;

    private final AuthorizationManager<Message<?>> authorizationManager;


    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           WebSocketAuthInterceptor webSocketAuthInterceptor, AuthorizationChannelInterceptor authorizationChannelInterceptor, ChannelInterceptor securityContextChannelInterceptor, ApplicationContext applicationContext, AuthorizationManager<Message<?>> authorizationManager) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.authorizationChannelInterceptor = authorizationChannelInterceptor;
        this.securityContextChannelInterceptor = securityContextChannelInterceptor;
        this.applicationContext = applicationContext;
        this.authorizationManager = authorizationManager;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173")
                       .setAllowedOriginPatterns("*") ;// ✅ Allow all origins

//                .addInterceptors(jwtHandshakeInterceptor); // ✅ Interceptor for JWT authentication
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        System.out.println("✅ Registering WebSocketAuthInterceptor...");

        registration
        .interceptors(webSocketAuthInterceptor) // ✅ Ensures authentication
       .interceptors(securityContextChannelInterceptor) // ✅ Then applies Spring Security
                         .interceptors(authorizationChannelInterceptor); // Enforce message auth

    }


    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
    }

//    @Override
//    public void configureClientInboundChannel(ChannelRegistration registration) {
//        AuthorizationChannelInterceptor authz = new AuthorizationChannelInterceptor(authorizationManager);
//        AuthorizationEventPublisher publisher = new SpringAuthorizationEventPublisher(applicationContext);
//        authz.setAuthorizationEventPublisher(publisher);
//        registration.interceptors(new SecurityContextChannelInterceptor(), authz);
//    }

}
