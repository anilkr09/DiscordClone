package com.discordclone.exception;

import org.springframework.http.HttpStatus;

/** The thing asked for does not exist. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
