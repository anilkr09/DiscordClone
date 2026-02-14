package com.discordclone.websocket.listener;

import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import com.discordclone.websocket.publisher.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketPublisher publisher;

    /**
     * Fired when WebSocket connection is established
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();

        if (user == null) {
            return;
        }

        String username = user.getName();

        log.info("User connected: {}", username);

        WsEvent wsEvent = WsEvent.builder()
                .type(WsEventType.USER_ONLINE)
                .payload(username)
                .build();

        publisher.sendToTopic(
                WsDestinations.PRESENCE,
                wsEvent
        );
    }

    /**
     * Fired when WebSocket connection is closed
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();

        if (user == null) {
            return;
        }

        String username = user.getName();

        log.info("User disconnected: {}", username);

        WsEvent wsEvent = WsEvent.builder()
                .type(WsEventType.USER_OFFLINE)
                .payload(username)
                .build();

        publisher.sendToTopic(
                WsDestinations.PRESENCE,
                wsEvent
        );
    }

    /**
     * Fired when user subscribes to a destination (optional)
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();

        if (user == null) {
            return;
        }

        String username = user.getName();

        String destination = accessor.getDestination();

        log.info("User subscribed: {} -> {}", username, destination);
    }
}
