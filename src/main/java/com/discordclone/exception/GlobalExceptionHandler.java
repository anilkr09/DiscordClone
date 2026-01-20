
    package com.discordclone.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

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
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
            Map<String, String> errors = new HashMap<>();

            ex.getBindingResult().getFieldErrors().forEach(error ->
                    errors.put(error.getField(), error.getDefaultMessage())
            );

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("timestamp", LocalDateTime.now());
            responseBody.put("status", 400);
            responseBody.put("errors", errors);
            log.info("❌ Validation failed: {}", errors);
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }





        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("message", String.format("Request method '%s' is not supported for this endpoint.", ex.getMethod()));
            response.put("status", HttpStatus.METHOD_NOT_ALLOWED.value());

            return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
        }
//
//        @ExceptionHandler(HttpMessageNotReadableException.class)
//        public ResponseEntity<Map<String, Object>> handleMissingRequestBody(HttpMessageNotReadableException ex) {
//            Map<String, Object> response = new HashMap<>();
//            response.put("timestamp", LocalDateTime.now());
//            response.put("message", "Required request body is missing or malformed.");
//            response.put("status", HttpStatus.BAD_REQUEST.value());
//
//            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
//        }
        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<Map<String, Object>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("message", String.format("No endpoint %s %s found.", ex.getHttpMethod(), ex.getRequestURL()));
            response.put("status", HttpStatus.NOT_FOUND.value());

            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        @ExceptionHandler(JwtAuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleJwtAuthenticationException(JwtAuthenticationException ex) {
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
        public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
            log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);

            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("error", "Internal Server Error");
            response.put("message", ex.getMessage());
            response.put("path", request.getRequestURI());

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }


