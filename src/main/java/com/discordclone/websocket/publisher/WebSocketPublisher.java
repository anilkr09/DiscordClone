package com.discordclone.websocket.publisher;

import com.discordclone.service.ChatBroadcast;
import com.discordclone.websocket.event.WsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Queues events; they reach subscribers after the current transaction commits. */
@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    private final ApplicationEventPublisher events;

    public void sendToUser(String username, String destination, WsEvent event) {
        events.publishEvent(ChatBroadcast.toUser(username, destination, event));
    }

    public void sendToTopic(String destination, WsEvent event) {
        events.publishEvent(ChatBroadcast.toTopic(destination, event));
    }
}
