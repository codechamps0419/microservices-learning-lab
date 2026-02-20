package com.example.product.exception;

/*
 * Thrown when a product with the same name already exists.
 * Caught by GlobalExceptionHandler and mapped to HTTP 409 Conflict.
 *
 * You asked for "duplicate SKU handling". In our current schema we do
 * not have a SKU column — the closest natural uniqueness constraint is
 * the product name. This exception covers that rule.
 *
 * When you add a SKU column in a future migration, rename this to
 * DuplicateSkuException and update the message — no other code changes needed.
 *
 * WHY 409 CONFLICT AND NOT 400 BAD REQUEST?
 * 400 means the request itself is malformed or invalid in isolation.
 * 409 means the request is valid but conflicts with the current state
 * of the resource on the server. A duplicate name is a state conflict,
 * not a malformed request.
 */
public class DuplicateProductNameException extends RuntimeException {

    public DuplicateProductNameException(String name) {
        super("A product with the name '" + name + "' already exists");
    }
}
