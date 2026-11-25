package com.discordclone.kafka;

import com.discordclone.contract.MessageCreatedV1;
import com.discordclone.payload.MessageResponse;
import com.discordclone.payload.UserDTO;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Delivers messages to the sockets connected to this instance. Each instance is its own consumer
 * group, so every instance sees every message, wherever it was sent.
 */
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class MessageFanOut {

    private final SimpMessagingTemplate messaging;

    @KafkaListener(topics = MessageCreatedV1.TOPIC, groupId = "fanout-${app.instance-id}",
            // A new instance has no sockets that missed anything earlier.
            properties = "auto.offset.reset=latest")
    public void deliver(MessageCreatedV1 message) {
        MessageResponse response = MessageResponse.builder()
                .id(message.id())
                .channelId(message.channelId())
                .content(message.content())
                .timestamp(message.timestamp())
                .nonce(message.nonce())
                .author(UserDTO.builder()
                        .id(message.author().id())
                        .username(message.author().username())
                        .avatarUrl("")
                        .build())
                .build();
        WsEvent event = new WsEvent(WsEventType.MESSAGE_CREATED, response);
        if (message.recipients() == null) {
            messaging.convertAndSend(WsDestinations.channelMessages(message.channelId()), event);
        } else {
            message.recipients().forEach(username ->
                    messaging.convertAndSendToUser(username, WsDestinations.MESSAGES, event));
        }
    }
}
