package com.discordclone.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import com.discordclone.security.CorsProperties;
import com.discordclone.security.StompSessionRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import com.discordclone.security.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
// Ordered ahead of Spring Security's configurer, so that CONNECT is authenticated before it is authorized.
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    private final StompSessionRegistry stompSessionRegistry;
    private final CorsProperties corsProperties;
    private final TaskScheduler brokerHeartbeatScheduler;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor,
                           StompSessionRegistry stompSessionRegistry, CorsProperties corsProperties,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerHeartbeatScheduler) {
        this.brokerHeartbeatScheduler = brokerHeartbeatScheduler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.stompSessionRegistry = stompSessionRegistry;
        this.corsProperties = corsProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Heart-beats in both directions notice half-open connections.
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(brokerHeartbeatScheduler)
                .setHeartbeatValue(new long[]{10_000, 10_000});
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(stompSessionRegistry::track);
        registration.setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(10_000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor, new LogContext());
        registration.taskExecutor().corePoolSize(8).maxPoolSize(32).queueCapacity(1000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(8).maxPoolSize(32).queueCapacity(10_000);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}
