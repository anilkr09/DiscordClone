package com.discordclone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("kafka")
public class KafkaSendConfig {

    /**
     * Kafka's send blocks while it waits for broker metadata, so it must not run on a STOMP inbound
     * thread. One thread keeps messages in order; the bounded queue limits the backlog in an outage.
     */
    @Bean
    ThreadPoolTaskExecutor kafkaSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("kafka-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
