package com.discordclone.consumer;

/** The event can never be stored, however often it is retried. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
