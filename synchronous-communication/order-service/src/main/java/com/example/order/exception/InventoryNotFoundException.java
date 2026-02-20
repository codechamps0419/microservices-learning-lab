package com.example.order.exception;

import java.util.UUID;

/*
 * Thrown when inventory-service returns 404 for a given productId.
 *
 * This means no inventory record exists for the product — stock has never
 * been added for this productId in inventory-service. The product may exist
 * in product-service, but it has no inventory record yet.
 *
 * This is a business rule violation from order-service's perspective:
 * you cannot order a product that has no inventory record.
 * Maps to HTTP 422 Unprocessable Entity — the request is valid but the
 * current state of the system cannot fulfil it.
 */
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(UUID productId) {
        super("No inventory record found for product: " + productId);
    }
}
