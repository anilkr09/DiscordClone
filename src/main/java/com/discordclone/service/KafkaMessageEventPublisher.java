package com.discordclone.service;

import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageEventPublisher implements MessageEventPublisher {

    private final KafkaTemplate<String, MessageResponse> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(Message message, MessageResponse response, MessageRequest request, User user) {

        kafkaTemplate.send("message-events", response.getChannelId().toString(), response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka failed", ex);

                        messagingTemplate.convertAndSendToUser(
                                user.getUsername(),
                                "/queue/errors",
                                "Message failed"
                        );
                    } else {
                        sendToWebSocket(request, user, response);
                    }
                });
    }
    private void sendToWebSocket(MessageRequest request, User user, MessageResponse messageResponse) {

        if (request.isDm()) {
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(),
                    "/queue/messages",
                    messageResponse
            );

            messagingTemplate.convertAndSendToUser(
                    request.getReceiver(),
                    "/queue/messages",
                    messageResponse
            );
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + messageResponse.getChannelId() + "/messages",
                    messageResponse
            );
        }
    }

}