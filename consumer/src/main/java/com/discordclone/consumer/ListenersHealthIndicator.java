package com.discordclone.consumer;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/** Down when a Kafka listener has stopped, which would otherwise show only as growing lag. */
@Component("listeners")
public class ListenersHealthIndicator implements HealthIndicator {

    private final KafkaListenerEndpointRegistry registry;

    public ListenersHealthIndicator(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        var containers = registry.getListenerContainers();
        boolean running = !containers.isEmpty()
                && containers.stream().allMatch(MessageListenerContainer::isRunning);
        return (running ? Health.up() : Health.down()).withDetail("listeners", containers.size()).build();
    }
}
