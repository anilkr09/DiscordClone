package com.discordclone.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /* =========================
       409 – Conflict
    ========================= */

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request
    ) {
        log.error(ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "CONFLICT",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                ));
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED",
                        "Invalid username or password",
                        request.getRequestURI(),
                        null
                ));
    }
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request
    ) {
        log.error(ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "CONFLICT",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                ));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.error(ex.getMessage(), ex);
        String message = "Database constraint violation";
        Map<String, String> fieldErrors = null;

        Throwable root = ex.getRootCause();

        if (root instanceof ConstraintViolationException constraintEx) {
            String constraint = constraintEx.getConstraintName();

            if (constraint != null) {
                switch (constraint) {

                    case "uk_username" -> {
                        message = "Username already taken";
                        fieldErrors = Map.of("username", "Username already taken");
                    }

                    case "uk_email" -> {
                        message = "Email already registered";
                        fieldErrors = Map.of("email", "Email already registered");
                    }

                    case "servers_name_key", "uk_server_name" -> {
                        message = "Server name already exists";
                        fieldErrors = Map.of("name", "Server name already exists");
                    }

                    default -> {
                        message = "Duplicate or invalid data";
                    }
                }
            }
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "CONFLICT",
                        message,
                        request.getRequestURI(),
                        fieldErrors
                ));
    }

    /* =========================
       400 – Validation Errors
    ========================= */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.info("❌ Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "RESOURCE_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                ));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

        log.info("❌ Validation failed: {}", errors);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_FAILED",
                        "Validation failed for request",
                        request.getRequestURI(),
                        errors
                ));
    }

    /* =========================
       405 – Method Not Allowed
    ========================= */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "METHOD_NOT_ALLOWED",
                        String.format(
                                "Request method '%s' is not supported for this endpoint.",
                                ex.getMethod()
                        ),
                        request.getRequestURI(),
                        null
                ));
    }

    /* =========================
       404 – No Handler Found
    ========================= */

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        String.format(
                                "No endpoint %s %s found.",
                                ex.getHttpMethod(),
                                ex.getRequestURL()
                        ),
                        request.getRequestURI(),
                        null
                ));
    }

    /* =========================
       401 – JWT Errors
    ========================= */

    @ExceptionHandler({
            ExpiredJwtException.class,
            MalformedJwtException.class,
            SignatureException.class,
            UnsupportedJwtException.class,
            JwtAuthenticationException.class
    })
    public ResponseEntity<ErrorResponse> handleJwtExceptions(
            Exception ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHORIZED",
                        getJwtErrorMessage(ex),
                        request.getRequestURI(),
                        null
                ));
    }

    /* =========================
       503 – WebSocket / Messaging
    ========================= */

    @ExceptionHandler(MessageDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleMessageDeliveryException(
            MessageDeliveryException ex,
            HttpServletRequest request
    ) {
        log.error("Message delivery failed", ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "SERVICE_UNAVAILABLE",
                        "Message delivery failed",
                        request.getRequestURI(),
                        null
                ));
    }

    /* =========================
       500 – Fallback
    ========================= */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_SERVER_ERROR",
                        "Something went wrong",
                        request.getRequestURI(),
                        null
                ));
    }

    private String getJwtErrorMessage(Exception ex) {
        if (ex instanceof ExpiredJwtException) {
            return "JWT token has expired. Please log in again.";
        } else if (ex instanceof MalformedJwtException) {
            return "Invalid JWT token format.";
        } else if (ex instanceof SignatureException) {
            return "Invalid JWT signature.";
        } else if (ex instanceof UnsupportedJwtException) {
            return "JWT token is not supported.";
        } else {
            return "Invalid JWT token.";
        }
    }
}
