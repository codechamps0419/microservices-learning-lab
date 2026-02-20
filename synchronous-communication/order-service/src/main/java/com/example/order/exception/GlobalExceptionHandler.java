package com.example.order.exception;

import com.example.order.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/*
 * Central exception handler for all controllers in order-service.
 *
 * @RestControllerAdvice intercepts exceptions thrown by any @RestController
 * and converts them into structured ErrorResponse JSON bodies with the
 * correct HTTP status code.
 *
 * WHY THIS EXISTS:
 * Without this, Spring Boot returns its default /error page or a generic
 * 500 response for every unhandled exception. API consumers cannot reliably
 * parse those responses. This handler ensures every error has the same
 * consistent ErrorResponse shape.
 *
 * EXCEPTION → HTTP STATUS MAPPING:
 *
 *   ProductNotFoundException              → 404  (product does not exist)
 *   InactiveProductException              → 422  (product exists but is not orderable)
 *   InsufficientStockException            → 422  (not enough stock)
 *   InventoryNotFoundException            → 422  (no inventory record for product)
 *   InventoryValidationException          → 422  (inventory rejected the request)
 *   InventoryConflictException            → 409  (optimistic lock conflict — retry)
 *   ProductServiceException               → 502  (product-service returned 500)
 *   InventoryServiceException             → 502  (inventory-service returned 500)
 *   ProductServiceUnavailableException    → 503  (product-service down/timeout)
 *   InventoryServiceUnavailableException  → 503  (inventory-service down/timeout)
 *   MethodArgumentNotValidException       → 400  (@Valid constraint violations)
 *   Exception (fallback)                  → 500  (unexpected errors)
 *
 * CORRELATION ID IN ERROR RESPONSES:
 * Every error response includes the correlationId from MDC (if present).
 * This allows the caller to reference the ID when reporting issues,
 * and allows support to find the full request trace in logs.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 400 Bad Request — validation failures from @Valid on request DTOs
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Collect all field-level validation messages into one readable string.
        // e.g. "productId: Product ID is required; quantity: Quantity must be at least 1"
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message);
    }

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 409 Conflict — optimistic lock collision on inventory, caller may retry
    // -------------------------------------------------------------------------

    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ErrorResponse> handleInventoryConflict(InventoryConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity — valid request, but business rules prevent it
    // -------------------------------------------------------------------------

    @ExceptionHandler(InactiveProductException.class)
    public ResponseEntity<ErrorResponse> handleInactiveProduct(InactiveProductException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInventoryNotFound(InventoryNotFoundException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InventoryValidationException.class)
    public ResponseEntity<ErrorResponse> handleInventoryValidation(InventoryValidationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway — a downstream service returned an error response
    // -------------------------------------------------------------------------

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<ErrorResponse> handleProductServiceError(ProductServiceException ex) {
        log.error("product-service error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(InventoryServiceException.class)
    public ResponseEntity<ErrorResponse> handleInventoryServiceError(InventoryServiceException ex) {
        log.error("inventory-service error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 503 Service Unavailable — a downstream service is unreachable or timed out
    // -------------------------------------------------------------------------

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProductUnavailable(ProductServiceUnavailableException ex) {
        log.error("product-service unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(InventoryServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleInventoryUnavailable(InventoryServiceUnavailableException ex) {
        log.error("inventory-service unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error — fallback for anything not explicitly handled
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Log with full stack trace — this is an unexpected error and needs investigation.
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                MDC.get("correlationId") // null-safe — @JsonInclude(NON_NULL) omits it if null
        );
        return ResponseEntity.status(status).body(body);
    }
}
