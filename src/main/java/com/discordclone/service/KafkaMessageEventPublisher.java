package com.discordclone.service;

import com.discordclone.payload.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaMessageEventPublisher implements MessageEventPublisher {

    private final KafkaTemplate<String, MessageResponse> kafkaTemplate;

    @Override
    public void publish(MessageResponse message) {
        kafkaTemplate.send(
                "message-events",
                message.getChannelId().toString(), // 🔥 partition key
                message
        );
    }
}