package com.discordclone.exception;

import org.springframework.http.HttpStatus;

/** A failure the caller can act on. Its message is sent to the client, so it must not leak internals. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
