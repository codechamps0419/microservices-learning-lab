package com.example.order.exception;

import java.util.UUID;

/*
 * Thrown when product-service returns 404 for a given productId.
 *
 * This means the product does not exist in product-service at all.
 * It is different from a product being INACTIVE — an inactive product
 * exists but cannot be ordered. A 404 means it was never created or
 * has been permanently deleted.
 *
 * Maps to HTTP 404 Not Found in order-service's GlobalExceptionHandler.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
    }
}
