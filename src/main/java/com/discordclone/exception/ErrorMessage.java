package com.discordclone.exception;

/** An error delivered over STOMP on {@code /user/queue/errors}. */
public record ErrorMessage(int status, String message) {
}
