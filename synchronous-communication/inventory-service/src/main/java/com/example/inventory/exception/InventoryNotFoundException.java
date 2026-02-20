package com.example.inventory.exception;

import java.util.UUID;

/*
 * Thrown when no inventory record exists for the given productId.
 * Maps to HTTP 404 Not Found.
 *
 * This is different from "product does not exist in product-service".
 * It means stock has never been added for this productId in inventory-service.
 * The product may exist — it just has no inventory record yet.
 */
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(UUID productId) {
        super("Inventory not found for product id: " + productId);
    }
}
