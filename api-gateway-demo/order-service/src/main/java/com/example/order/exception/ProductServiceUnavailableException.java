package com.example.order.exception;

/*
 * Thrown when product-service is unreachable or returns 503.
 *
 * Two scenarios trigger this:
 *
 * 1. Connection timeout — the TCP connection to product-service could not
 *    be established within the configured timeout. The service is down,
 *    the port is wrong, or the network is unavailable.
 *
 * 2. Read timeout — the connection was established but product-service
 *    did not send a complete response within the configured timeout.
 *    The service is alive but too slow or hung.
 *
 * 3. product-service explicitly returns 503 Service Unavailable —
 *    it is up but signalling that it cannot handle requests right now
 *    (e.g. it is itself waiting for a dependency).
 *
 * Maps to HTTP 503 Service Unavailable in order-service's GlobalExceptionHandler.
 * This tells the caller: "try again later, a dependency is currently unavailable."
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
