package com.discordclone.config;

import com.discordclone.security.UserPrincipal;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.core.Authentication;

/** Puts who is acting into every log line written while handling a STOMP frame. */
public class LogContext implements ExecutorChannelInterceptor {

    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (SimpMessageHeaderAccessor.getUser(message.getHeaders()) instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal user) {
            MDC.put(USER_ID, String.valueOf(user.getId()));
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                    Exception ex) {
        MDC.remove(USER_ID);
        MDC.remove(SESSION_ID);
    }
}
