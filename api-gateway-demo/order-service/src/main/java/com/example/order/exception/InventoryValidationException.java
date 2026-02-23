package com.example.order.exception;

/*
 * Thrown when inventory-service returns 422 for a reason other than
 * insufficient stock — for example, a validation error on the request body
 * that order-service sent (e.g. quantity < 1 somehow bypassed local validation).
 *
 * In practice this should never happen if order-service's own validation
 * is correct. But defensive handling is required — if inventory-service
 * rejects the request for any 422 reason, order-service must not silently
 * swallow it.
 *
 * Maps to HTTP 422 Unprocessable Entity in order-service's GlobalExceptionHandler.
 *
 * NOTE ON 422 DISAMBIGUATION:
 * inventory-service returns 422 for both "insufficient stock" and general
 * validation failures. The client layer reads the response message to
 * distinguish them and throws the appropriate exception:
 * - "Insufficient stock" message -> InsufficientStockException
 * - Any other 422 message        -> InventoryValidationException
 */
public class InventoryValidationException extends RuntimeException {

    public InventoryValidationException(String message) {
        super(message);
    }
}
