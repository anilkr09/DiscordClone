package com.discordclone.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Reports failures of {@code @MessageMapping} handlers to the sender, who would otherwise hear nothing. */
@Slf4j
@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ErrorMessage handleApi(ApiException ex) {
        return new ErrorMessage(ex.getStatus().value(), ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ErrorMessage handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult() == null ? "Invalid message"
                : ex.getBindingResult().getFieldErrors().stream()
                        .findFirst().map(error -> error.getDefaultMessage()).orElse("Invalid message");
        return new ErrorMessage(400, message);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ErrorMessage handleUnexpected(Exception ex) {
        log.error("Unhandled exception in a message handler", ex);
        return new ErrorMessage(500, "Something went wrong");
    }
}
