package com.discordclone.service;

import com.discordclone.model.Message;
import com.discordclone.model.User;
import com.discordclone.payload.MessageRequest;
import com.discordclone.payload.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalMessageEventPublisher implements MessageEventPublisher {

    private final MessagePersistenceService persistenceService;
    private final ApplicationEventPublisher events;

    @Override
    public void publish(Message message, MessageResponse response, MessageRequest request, User user) {
        persistenceService.save(message);

        // Delivered by ChatBroadcaster after the surrounding transaction commits.
        if (request.isDm()) {
            events.publishEvent(ChatBroadcast.toUser(user.getUsername(), "/queue/messages", response));
            if (!user.getUsername().equals(request.getReceiver())) {
                events.publishEvent(ChatBroadcast.toUser(request.getReceiver(), "/queue/messages", response));
            }
        } else {
            events.publishEvent(ChatBroadcast.toTopic(
                    "/topic/channels/" + response.getChannelId() + "/messages", response));
        }
    }
}
