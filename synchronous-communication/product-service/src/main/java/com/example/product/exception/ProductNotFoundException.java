package com.example.product.exception;

import java.util.UUID;

/*
 * Thrown when a product with the given ID does not exist in the database.
 * Caught by GlobalExceptionHandler and mapped to HTTP 404 Not Found.
 *
 * WHY EXTEND RuntimeException AND NOT Exception?
 * Checked exceptions (extends Exception) force every caller to declare
 * throws or wrap in try-catch. In a Spring service layer this creates
 * noise without adding safety — Spring's @Transactional only rolls back
 * on RuntimeException by default anyway.
 * Unchecked exceptions keep the service method signatures clean.
 *
 * WHY NOT USE A GENERIC NotFoundException?
 * A dedicated exception per domain concept gives you:
 * 1. Clear stack traces — you immediately know what was not found
 * 2. The ability to handle ProductNotFoundException differently from
 *    InventoryNotFoundException in GlobalExceptionHandler if needed
 * 3. Readable service code: throw new ProductNotFoundException(id)
 *    is self-documenting
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }
}
