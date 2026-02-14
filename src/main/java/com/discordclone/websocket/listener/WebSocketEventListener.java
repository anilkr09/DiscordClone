package com.discordclone.websocket.listener;

import com.discordclone.model.UserStatus;
import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import com.discordclone.websocket.destination.WsDestinations;
import com.discordclone.websocket.event.WsEvent;
import com.discordclone.websocket.event.WsEventType;
import com.discordclone.websocket.publisher.WebSocketPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserStatusService userStatusService;
    private final WebSocketPublisher publisher;

    /**
     * Fired when WebSocket connection is established
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        Authentication authentication = (Authentication) accessor.getUser();

        if (authentication == null) {
            return;
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Long userId = userPrincipal.getId();
        String username = userPrincipal.getUsername();

        log.info("User connected: {}", username);

        // 1. Update database status
        userStatusService.updateUserStatus(userId, UserStatus.ONLINE);

        // 2. Broadcast presence event
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

        Authentication authentication = (Authentication) accessor.getUser();

        if (authentication == null) {
            return;
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Long userId = userPrincipal.getId();
        String username = userPrincipal.getUsername();

        log.info("User disconnected: {}", username);

        // 1. Update database status
        userStatusService.updateUserStatus(userId, UserStatus.OFFLINE);

        // 2. Broadcast presence event
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
     * Optional: Track subscriptions
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
