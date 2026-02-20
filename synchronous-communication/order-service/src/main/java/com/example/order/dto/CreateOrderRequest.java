package com.example.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/*
 * DTO for POST /api/orders — places a new order.
 *
 * WHY A DTO AND NOT THE Order ENTITY DIRECTLY?
 * The Order entity has fields the client must NOT control:
 * id, productName, unitPrice, totalPrice, status, createdAt, updatedAt.
 * These are set by the service layer after calling product-service and
 * inventory-service. If we accepted the entity directly, a malicious
 * client could send pre-filled prices or force a CONFIRMED status.
 *
 * The client provides only two things:
 * - WHICH product they want (productId)
 * - HOW MANY units they want (quantity)
 *
 * Everything else — name, price, total, status — is determined by
 * order-service after validating with downstream services.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    /*
     * The product the customer wants to order.
     *
     * @NotNull — productId must be present in the request body.
     * Spring automatically converts a valid UUID string from JSON to UUID.
     * An invalid UUID string causes HttpMessageNotReadableException -> 400.
     *
     * order-service does not validate whether this productId exists in
     * product-service at the DTO level. That validation happens in the
     * service layer when ProductClient calls GET /api/products/{productId}.
     */
    @NotNull(message = "Product ID is required")
    private UUID productId;

    /*
     * Number of units the customer wants to order.
     *
     * @NotNull  — quantity must be present (Integer wrapper, not int primitive,
     *             so a missing field deserialises to null and @NotNull catches it)
     * @Min(1)   — ordering 0 units is a caller bug, reject it explicitly
     *
     * WHY Integer WRAPPER AND NOT int PRIMITIVE?
     * A missing int field in JSON defaults to 0 silently.
     * Using Integer means a missing field deserialises to null,
     * which @NotNull catches and rejects with a meaningful error message.
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
