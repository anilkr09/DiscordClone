package com.discordclone.service;

import com.discordclone.contract.MessageCreatedV1;
import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("kafka")
@Slf4j
public class KafkaMessageEventPublisher implements MessageEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskExecutor sendExecutor;

    public KafkaMessageEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                      SimpMessagingTemplate messagingTemplate,
                                      @Qualifier("kafkaSendExecutor") TaskExecutor sendExecutor) {
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.sendExecutor = sendExecutor;
    }

    @Override
    public void publish(Message message, MessageResponse response, MessageRequest request, User user) {
        String sender = user.getUsername();
        MessageCreatedV1 event = new MessageCreatedV1(response.getId(), response.getChannelId(),
                response.getContent(), response.getTimestamp(),
                new MessageCreatedV1.Author(response.getAuthor().getId(), sender));
        try {
            sendExecutor.execute(() -> send(event, response, request, sender));
        } catch (TaskRejectedException ex) {
            reportFailure(sender, ex);
        }
    }

    private void send(MessageCreatedV1 event, MessageResponse response, MessageRequest request, String sender) {
        try {
            // Keyed by channel, so that one channel's messages stay in order.
            kafkaTemplate.send(MessageCreatedV1.TOPIC, event.channelId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            reportFailure(sender, ex);
                        } else {
                            sendToWebSocket(request, sender, response);
                        }
                    });
        } catch (RuntimeException ex) {
            // send() itself throws when no broker metadata arrives within max.block.ms.
            reportFailure(sender, ex);
        }
    }

    private void reportFailure(String sender, Throwable cause) {
        log.error("Could not publish a message to Kafka", cause);
        messagingTemplate.convertAndSendToUser(sender, "/queue/errors", "Message failed");
    }

    private void sendToWebSocket(MessageRequest request, String sender, MessageResponse response) {
        if (request.isDm()) {
            messagingTemplate.convertAndSendToUser(sender, "/queue/messages", response);
            messagingTemplate.convertAndSendToUser(request.getReceiver(), "/queue/messages", response);
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + response.getChannelId() + "/messages", response);
        }
    }
}
