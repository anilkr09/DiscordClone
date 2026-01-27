package com.discordclone.websocket.publisher;

import com.discordclone.websocket.event.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendEventPublisher {

    private final SimpMessagingTemplate template;

    public void send(String userName, WsEvent event) {
        template.convertAndSendToUser(
                userName,
                WsDestinations.FRIENDS,
                event
        );
    }
}