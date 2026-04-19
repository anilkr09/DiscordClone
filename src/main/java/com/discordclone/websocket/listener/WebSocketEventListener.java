package com.discordclone.websocket.listener;

import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserStatusService userStatusService;

    // =========================
    // CONNECT
    // =========================
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Authentication authentication = (Authentication) accessor.getUser();

        if (authentication == null) return;

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        log.info("User connected: {}", user.getUsername());

        // 🔥 OPTIONAL: mark initial activity (fast ONLINE)
        userStatusService.handleActivity(user.getId());
    }

    // =========================
    // DISCONNECT
    // =========================
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Authentication authentication = (Authentication) accessor.getUser();

        if (authentication == null) return;

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        log.info("User disconnected: {}", user.getUsername());

        // 🔥 DO NOT force OFFLINE
        // Let Redis TTL handle it

        // OPTIONAL: persist last seen
        userStatusService.persistLastSeen(user.getId());
    }
}