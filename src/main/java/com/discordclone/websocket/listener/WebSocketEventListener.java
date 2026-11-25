package com.discordclone.websocket.listener;

import com.discordclone.security.UserPrincipal;
import com.discordclone.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.function.Consumer;

/** Feeds socket opens and closes into presence. */
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserStatusService userStatusService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        withUser(event.getUser(), userId -> userStatusService.connected(userId, sessionId));
    }

    /** May fire more than once for a socket, which presence tolerates. */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        withUser(event.getUser(), userId -> userStatusService.disconnected(userId, event.getSessionId()));
    }

    private static void withUser(Principal principal, Consumer<Long> action) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            action.accept(user.getId());
        }
    }
}
