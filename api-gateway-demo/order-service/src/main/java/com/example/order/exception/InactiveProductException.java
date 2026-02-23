package com.example.order.exception;

import java.util.UUID;

/*
 * Thrown when product-service returns the product successfully (200 OK)
 * but the product's status is not "ACTIVE".
 *
 * This is a business rule enforced by order-service:
 * you cannot place an order for a product that is not currently active.
 *
 * WHY THIS IS DIFFERENT FROM ProductNotFoundException:
 * - ProductNotFoundException: the product does not exist at all (404 from product-service)
 * - InactiveProductException: the product exists but is INACTIVE, DISCONTINUED, etc.
 *
 * In both cases, order-service must NOT call inventory-service.
 * There is no point reserving stock for a product that cannot be ordered.
 *
 * Maps to HTTP 422 Unprocessable Entity in GlobalExceptionHandler.
 * The request is syntactically valid but cannot be processed given the
 * current state of the product.
 */
public class InactiveProductException extends RuntimeException {

    public InactiveProductException(UUID productId, String status) {
        super("Product " + productId + " cannot be ordered because its status is: " + status);
    }
}
