package com.example.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/*
 * DTO for POST /api/inventories — creates an inventory record for a product.
 *
 * The caller provides the productId and the initial stock quantity.
 * inventory-service does not validate whether the productId exists in
 * product-service — that would require a cross-service call and create
 * coupling. The caller is responsible for providing a valid productId.
 *
 * This is a deliberate boundary decision:
 * inventory-service trusts the productId it receives.
 * It does not reach into product_db to verify it.
 */
@Getter
@Setter
@NoArgsConstructor
public class AddStockRequest {

    /*
     * @NotNull — productId must be present.
     * Spring automatically converts a valid UUID string from JSON to UUID type.
     * An invalid UUID string causes HttpMessageNotReadableException → 400.
     */
    @NotNull(message = "Product ID is required")
    private UUID productId;

    /*
     * @Min(0) — initial quantity can be zero (creating a record with no stock yet)
     * but cannot be negative. A negative initial stock makes no business sense.
     *
     * We use int primitive in the entity but Integer wrapper in the DTO
     * so that @NotNull-equivalent behaviour works correctly.
     * A missing int field in JSON defaults to 0 silently — using Integer
     * with @Min makes the constraint explicit.
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Initial quantity cannot be negative")
    private Integer quantity;
}
