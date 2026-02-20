package com.example.inventory.dto;

import com.example.inventory.domain.Inventory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * DTO returned from all inventory endpoints.
 *
 * Notice what is NOT here:
 * - No product name, price, category or any product field
 * - No version field (internal optimistic locking concern, never exposed)
 *
 * The version field is intentionally excluded from the response.
 * Exposing it would tempt callers to use it for their own logic,
 * creating hidden coupling to our internal concurrency mechanism.
 */
@Getter
@Setter
@NoArgsConstructor
public class StockResponse {

    private UUID id;
    private UUID productId;
    private int availableQuantity;
    private int reservedQuantity;
    private LocalDateTime updatedAt;

    public static StockResponse from(Inventory inventory) {
        StockResponse response = new StockResponse();
        response.setId(inventory.getId());
        response.setProductId(inventory.getProductId());
        response.setAvailableQuantity(inventory.getAvailableQuantity());
        response.setReservedQuantity(inventory.getReservedQuantity());
        response.setUpdatedAt(inventory.getUpdatedAt());
        return response;
    }
}
