package com.example.order.exception;

/*
 * Thrown when inventory-service returns 409 Conflict.
 *
 * This means two concurrent reservation requests collided on the same
 * inventory record. inventory-service uses optimistic locking (@Version).
 * When two threads read the same version and both try to update, only one
 * wins. The other gets ObjectOptimisticLockingFailureException inside
 * inventory-service, which maps to 409 Conflict in its response.
 *
 * From order-service's perspective, this is a transient conflict.
 * The caller should receive 409 Conflict and may retry the order.
 *
 * Maps to HTTP 409 Conflict in order-service's GlobalExceptionHandler.
 */
public class InventoryConflictException extends RuntimeException {

    public InventoryConflictException(String message) {
        super(message);
    }
}
