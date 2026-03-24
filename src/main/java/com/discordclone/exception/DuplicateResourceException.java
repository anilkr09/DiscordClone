package com.discordclone.exception;

import lombok.Getter;

@Getter
public class DuplicateResourceException extends RuntimeException {

    // Optional getters (useful for logging / error response)
    private String resourceName;
    private String fieldName;
    private Object fieldValue;


    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s : '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }

}