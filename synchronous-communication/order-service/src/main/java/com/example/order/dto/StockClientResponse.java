package com.example.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/*
 * Represents the JSON response order-service receives from inventory-service
 * after a reserve or release call.
 *
 * Same reasoning as ProductClientResponse — this DTO depends only on the
 * HTTP contract, not on inventory-service's compiled StockResponse class.
 *
 * FIELDS INCLUDED:
 * - productId         — to confirm the reservation was for the right product
 * - availableQuantity — order-service may log or use this for diagnostics
 * - reservedQuantity  — confirms the reservation was recorded
 *
 * Fields NOT included (id, updatedAt) — order-service has no use for them.
 */
@Getter
@Setter
@NoArgsConstructor
public class StockClientResponse {

    private UUID productId;
    private int availableQuantity;
    private int reservedQuantity;
}
