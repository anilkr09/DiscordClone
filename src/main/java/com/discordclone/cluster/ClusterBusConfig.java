package com.discordclone.cluster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ClusterBusConfig {

    @Bean
    ThreadPoolTaskExecutor clusterEventExecutor() {
        // One thread, so that events are delivered in the order they were published.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("cluster-events-");
        return executor;
    }

    @Bean
    RedisMessageListenerContainer clusterListenerContainer(
            RedisConnectionFactory connections, ClusterBus bus, ThreadPoolTaskExecutor clusterEventExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.setTaskExecutor(clusterEventExecutor);
        container.addMessageListener(bus, new ChannelTopic(ClusterBus.CHANNEL));
        return container;
    }
}
