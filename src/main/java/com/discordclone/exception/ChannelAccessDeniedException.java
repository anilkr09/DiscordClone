package com.discordclone.exception;

public class ChannelAccessDeniedException extends RuntimeException {

    public ChannelAccessDeniedException(String message) {
        super(message);
    }

    public ChannelAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}