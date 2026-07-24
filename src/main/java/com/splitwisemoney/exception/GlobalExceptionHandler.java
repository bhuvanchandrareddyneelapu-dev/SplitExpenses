package com.splitwisemoney.exception;

import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ──────────────────────────────────────────
    // 400 Bad Request
    // ──────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Bad request [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(
            org.springframework.web.bind.MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> fieldErrors.put(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", "Validation failed for one or more fields");
        body.put("errors", fieldErrors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // ──────────────────────────────────────────
    // 401 Unauthorized
    // ──────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required. Please log in again.");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Object> handleUsernameNotFoundException(UsernameNotFoundException ex, WebRequest request) {
        log.warn("User not found [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required. Please log in again.");
    }

    // ──────────────────────────────────────────
    // 409 Conflict
    // ──────────────────────────────────────────

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Object> handleResourceConflictException(ResourceConflictException ex, WebRequest request) {
        log.warn("Conflict [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // ──────────────────────────────────────────
    // 410 Gone
    // ──────────────────────────────────────────

    @ExceptionHandler({InvitationExpiredException.class, InvalidTokenException.class})
    public ResponseEntity<Object> handleInvitationExpiredOrInvalid(RuntimeException ex, WebRequest request) {
        log.warn("Invitation expired or invalid [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.GONE, "Gone", ex.getMessage());
    }

    // ──────────────────────────────────────────
    // 403 Forbidden
    // ──────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied [{}]: {}", request.getDescription(false), ex.getMessage());
        return errorBody(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action.");
    }

    // ──────────────────────────────────────────
    // 500 Internal Server Error
    // ──────────────────────────────────────────

    @ExceptionHandler({SmtpDeliveryException.class, org.springframework.mail.MailException.class, jakarta.mail.MessagingException.class})
    public ResponseEntity<Object> handleSmtpDeliveryException(Exception ex, WebRequest request) {
        log.error("SMTP / Email Delivery Error [{}]: {}", request.getDescription(false), ex.getMessage(), ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Email Delivery Error", "Failed to send invitation email: " + ex.getMessage());
    }

    /**
     * LazyInitializationException means a JPA entity's lazy-loaded association was
     * accessed outside a transaction. This is a programming error; log it in detail
     * so it can be fixed, but return a safe 500 to the client.
     */
    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Object> handleLazyInitializationException(LazyInitializationException ex, WebRequest request) {
        log.error("LazyInitializationException on [{}] — a lazy-loaded association was accessed outside its " +
                  "transaction. Ensure the service method loads all required associations before the transaction closes. " +
                  "Detail: {}", request.getDescription(false), ex.getMessage(), ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled exception on request [{}]: {}", request.getDescription(false), ex.getMessage(), ex);
        return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred. Please try again later.");
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private ResponseEntity<Object> errorBody(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
