package com.discordclone.exception;

import org.springframework.http.HttpStatus;

/** The input is invalid. */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
