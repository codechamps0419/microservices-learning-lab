package com.example.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * DTO for POST /api/inventories/{productId}/reserve  — reserve stock
 *     and POST /api/inventories/{productId}/release  — release reservation
 *     and POST /api/inventories/{productId}/confirm  — confirm deduction
 *
 * All three operations work on a quantity, so they share this DTO.
 * The productId comes from the path variable, not the request body.
 *
 * @Min(1) — quantity must be at least 1.
 * Reserving, releasing, or confirming 0 units is a no-op and likely
 * a caller bug. We reject it explicitly rather than silently ignoring it.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReserveStockRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
