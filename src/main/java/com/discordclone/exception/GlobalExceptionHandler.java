
    package com.discordclone.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
    @Slf4j
    @ControllerAdvice
    public class GlobalExceptionHandler {
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException ex) {
            log.warn("Access denied: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: You do not have permission to perform this action.");
        }

        @ExceptionHandler(JwtAuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleRuntimeException(JwtAuthenticationException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("message", ex.getMessage());
            response.put("status", HttpStatus.BAD_REQUEST.value());

            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("message", ex.getMessage());
            response.put("status", HttpStatus.BAD_REQUEST.value());

            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        @ExceptionHandler(MessageDeliveryException.class)
        public ResponseEntity<String> handleMessageDeliveryException(MessageDeliveryException ex) {
            log.error("Message delivery failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Message delivery failed: " + ex.getMessage());
        }
        @ExceptionHandler({
                ExpiredJwtException.class,
                MalformedJwtException.class,
                SignatureException.class,
                UnsupportedJwtException.class,

        })
        public ResponseEntity<Map<String, Object>> handleJwtExceptions(Exception ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("status", HttpStatus.UNAUTHORIZED.value());
            response.put("error", "Unauthorized");
            response.put("message", getErrorMessage(ex));
            response.put("path", "/api/protected"); // Modify based on your use case

            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        private String getErrorMessage(Exception ex) {
            if (ex instanceof ExpiredJwtException) {
                return "JWT token has expired. Please log in again.";
            } else if (ex instanceof MalformedJwtException) {
                return "Invalid JWT token format.";
            } else if (ex instanceof SignatureException) {
                return "Invalid JWT signature.";
            } else if (ex instanceof UnsupportedJwtException) {
                return "JWT token is not supported.";
            } else if (ex instanceof JwtAuthenticationException) {
                return "Invalid JWT token.";
            } else {
                return "Authentication error.";
            }
        }
        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        public void handleWebSocketExceptions(Exception e) {
            System.err.println("WebSocket error: " + e.getMessage());
        }
    }


