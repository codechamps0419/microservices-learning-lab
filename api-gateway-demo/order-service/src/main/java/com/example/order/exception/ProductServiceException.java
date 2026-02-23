package com.example.order.exception;

/*
 * Thrown when product-service returns 500 Internal Server Error.
 *
 * This means product-service encountered an unexpected error on its side.
 * order-service cannot do anything about it — the problem is in the
 * downstream service. The caller of order-service should receive 502 Bad
 * Gateway, indicating that an upstream dependency failed.
 *
 * WHY 502 AND NOT 500?
 * 500 means THIS service failed unexpectedly.
 * 502 means THIS service received an invalid response from an upstream service.
 * Using 502 tells the caller exactly where the problem is — not here, upstream.
 */
public class ProductServiceException extends RuntimeException {

    public ProductServiceException(String message) {
        super(message);
    }
}
