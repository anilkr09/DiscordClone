package com.discordclone.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Keeps hold of this instance's open sockets, so that none outlives its authorization: each is
 * closed when its access token expires and when its login ends.
 */
@Slf4j
@Component
public class StompSessionRegistry {

    private record Binding(String loginSessionId, ScheduledFuture<?> expiry) {
    }

    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    public StompSessionRegistry() {
        scheduler.setThreadNamePrefix("socket-expiry-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setDaemon(true);
        scheduler.initialize();
    }

    public WebSocketHandler track(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sockets.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                forget(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** Called when a socket authenticates. */
    public void bind(String socketId, String loginSessionId, Instant expiresAt) {
        ScheduledFuture<?> expiry = scheduler.schedule(() -> close(socketId, "token expired"), expiresAt);
        Binding previous = bindings.put(socketId, new Binding(loginSessionId, expiry));
        if (previous != null) {
            previous.expiry().cancel(false);
        }
    }

    public void closeLogin(String loginSessionId) {
        bindings.forEach((socketId, binding) -> {
            if (binding.loginSessionId().equals(loginSessionId)) {
                close(socketId, "signed out");
            }
        });
    }

    private void close(String socketId, String reason) {
        WebSocketSession session = sockets.get(socketId);
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (IOException ex) {
            log.debug("Could not close socket {}: {}", socketId, ex.getMessage());
        }
    }

    private void forget(String socketId) {
        sockets.remove(socketId);
        Binding binding = bindings.remove(socketId);
        if (binding != null) {
            binding.expiry().cancel(false);
        }
    }
}
