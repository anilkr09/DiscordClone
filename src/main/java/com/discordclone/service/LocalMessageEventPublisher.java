package com.discordclone.service;

import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalMessageEventPublisher implements MessageEventPublisher {

    private final MessagePersistenceService persistenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(Message message, MessageResponse response, MessageRequest request, User user) {

        // ✅ Save to DB
        persistenceService.save(message);

        // ✅ Direct WebSocket
        sendToWebSocket(request, user, response);
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

