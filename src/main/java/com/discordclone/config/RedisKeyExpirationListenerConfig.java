package com.discordclone.config;

import com.discordclone.service.PresenceExpirationListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@RequiredArgsConstructor
public class RedisKeyExpirationListenerConfig {

    private final PresenceExpirationListener presenceExpirationListener;

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter
    ) {

        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(
                listenerAdapter,
                new PatternTopic("__keyevent@*__:expired")
        );

        return container;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter() {

        return new MessageListenerAdapter(
                presenceExpirationListener,
                "handleMessage"
        );
    }
}