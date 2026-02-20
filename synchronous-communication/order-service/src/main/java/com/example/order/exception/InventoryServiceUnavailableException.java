package com.example.order.exception;

/*
 * Thrown when inventory-service is unreachable, times out, or returns 503.
 *
 * Same two timeout scenarios as ProductServiceUnavailableException:
 * - Connection timeout — cannot reach inventory-service at all
 * - Read timeout      — connected but no response within configured time
 * - 503 response      — inventory-service is up but signalling unavailability
 *
 * Maps to HTTP 503 Service Unavailable in order-service's GlobalExceptionHandler.
 */
public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(String message) {
        super(message);
    }

    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
