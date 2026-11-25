package com.discordclone.exception;

import org.springframework.http.HttpStatus;

/** The caller may not do this. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
