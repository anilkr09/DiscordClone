package com.discordclone.exception;
import java.time.Instant;
import java.util.Map;

public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Instant timestamp;
    private final Map<String, String> fieldErrors;

    public ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
        this.timestamp = Instant.now();
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
