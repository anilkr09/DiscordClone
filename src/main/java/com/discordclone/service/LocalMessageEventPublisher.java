package com.discordclone.service;

import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
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
        WsEvent event = new WsEvent(WsEventType.MESSAGE_CREATED, response);
        if (request.isDm()) {
            events.publishEvent(ChatBroadcast.toUser(user.getUsername(), WsDestinations.MESSAGES, event));
            if (!user.getUsername().equals(request.getReceiver())) {
                events.publishEvent(ChatBroadcast.toUser(request.getReceiver(), WsDestinations.MESSAGES, event));
            }
        } else {
            events.publishEvent(ChatBroadcast.toTopic(
                    WsDestinations.channelMessages(response.getChannelId()), event));
        }
    }
}
