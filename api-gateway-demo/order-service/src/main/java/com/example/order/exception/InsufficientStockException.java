package com.example.order.exception;

/*
 * Thrown when inventory-service returns 422 Unprocessable Entity.
 *
 * This means the reservation was rejected because available stock is less
 * than the requested quantity. The request itself is valid — the quantity
 * is a positive integer — but the current stock level cannot satisfy it.
 *
 * Maps to HTTP 422 Unprocessable Entity in order-service's GlobalExceptionHandler.
 *
 * WHY 422 AND NOT 400?
 * 400 means the request is malformed. The request here is perfectly valid.
 * 422 means the request is understood but cannot be processed due to the
 * current state of the resource (not enough stock).
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
