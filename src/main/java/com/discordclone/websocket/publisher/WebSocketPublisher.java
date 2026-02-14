package com.discordclone.websocket.publisher;

import com.discordclone.websocket.event.WsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketPublisher {

    private final SimpMessagingTemplate template;

    public void sendToUser(String username, String destination, WsEvent event) {

        template.convertAndSendToUser(
                username,
                destination,
                event
        );
    }

    public void sendToTopic(String destination, WsEvent event) {

        template.convertAndSend(
                destination,
                event
        );
    }
}
