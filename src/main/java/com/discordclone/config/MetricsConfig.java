package com.discordclone.config;

import com.discordclone.security.StompSessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application metrics. Spring Boot adds the rest: the STOMP channel executors appear as
 * {@code executor.*}, and Kafka sends as {@code spring.kafka.template}.
 */
@Configuration
public class MetricsConfig {

    @Bean
    MeterBinder stompSessions(StompSessionRegistry sessions) {
        return registry -> Gauge.builder("stomp.sessions", sessions, StompSessionRegistry::size)
                .description("Open WebSocket connections on this instance")
                .register(registry);
    }
}
