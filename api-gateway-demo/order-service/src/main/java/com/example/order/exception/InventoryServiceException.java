package com.example.order.exception;

/*
 * Thrown when inventory-service returns 500 Internal Server Error.
 *
 * Maps to HTTP 502 Bad Gateway — the problem is in the upstream service,
 * not in order-service itself.
 */
public class InventoryServiceException extends RuntimeException {

    public InventoryServiceException(String message) {
        super(message);
    }
}
