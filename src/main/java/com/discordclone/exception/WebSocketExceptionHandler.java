package com.discordclone.exception;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(JwtAuthenticationException.class)
    @SendToUser("/queue/errors")
    public String handleJwtException(JwtAuthenticationException ex) {
        return "Authentication Error: " + ex.getMessage();
    }
}
