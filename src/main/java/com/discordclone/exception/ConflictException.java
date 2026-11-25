package com.discordclone.exception;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state, such as a duplicate. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
