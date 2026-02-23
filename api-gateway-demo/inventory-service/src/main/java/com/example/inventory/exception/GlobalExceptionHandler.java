package com.example.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 * Identical structure to product-service GlobalExceptionHandler.
 * Each service owns its own handler — no shared library between services.
 *
 * KEY ADDITION vs product-service:
 * ObjectOptimisticLockingFailureException handler → 409 Conflict.
 * This is the exception Hibernate throws when the @Version check fails.
 *
 * HOW IT GETS HERE:
 * 1. Two threads both call reserveStock() concurrently
 * 2. Both read the same Inventory row with version=N
 * 3. Thread A commits first → version becomes N+1
 * 4. Thread B tries to commit → Hibernate executes:
 *    UPDATE inventories SET ... WHERE id=? AND version=N
 *    → 0 rows updated (version is now N+1, not N)
 * 5. Hibernate throws ObjectOptimisticLockingFailureException
 * 6. @Transactional rolls back Thread B's transaction
 * 7. This handler catches it and returns 409 Conflict
 *
 * WHAT SHOULD THE CALLER DO ON 409?
 * Retry the operation. The conflict is transient — it means another
 * request succeeded. A fresh read will show the updated stock level.
 * In production, a retry with exponential backoff is the standard pattern.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(InventoryNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientStockException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /*
     * Catches Hibernate's optimistic lock failure.
     * Spring wraps the JPA OptimisticLockException in
     * ObjectOptimisticLockingFailureException — we catch the Spring wrapper
     * so we are not tied to the JPA provider (Hibernate) directly.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT,
                "Stock was updated by another request. Please retry the operation.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = buildResponseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Malformed request body. Check field types and accepted values.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        ex.printStackTrace();
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(buildResponseBody(status, message));
    }

    private Map<String, Object> buildResponseBody(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
