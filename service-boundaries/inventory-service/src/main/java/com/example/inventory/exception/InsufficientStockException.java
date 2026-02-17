package com.example.inventory.exception;

import java.util.UUID;

/*
 * Thrown when a reservation or confirmation is attempted but
 * available stock is less than the requested quantity.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * WHY 422 AND NOT 400?
 * 400 Bad Request means the request itself is malformed.
 * The request here is perfectly valid — the quantity is a positive integer.
 * The problem is that the current state of the resource cannot satisfy it.
 * 422 communicates "the request is understood but cannot be processed
 * due to business rule constraints."
 *
 * WHY NOT 409 CONFLICT?
 * 409 is reserved for optimistic lock conflicts (concurrent update collision).
 * Insufficient stock is a business rule violation, not a concurrency conflict.
 * Using different status codes for different failure reasons lets the caller
 * handle each case appropriately.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int requested, int available) {
        super("Insufficient stock for product id: " + productId
                + ". Requested: " + requested + ", Available: " + available);
    }
}
