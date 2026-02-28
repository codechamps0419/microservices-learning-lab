package com.example.order.dto;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * DTO returned from all order endpoints.
 *
 * The Order entity is never returned directly from a controller.
 * This DTO is the only order representation that ever leaves the service.
 *
 * FIELDS INCLUDED:
 * All fields the API consumer legitimately needs to display an order.
 *
 * FIELDS NOT INCLUDED:
 * - No internal JPA state
 * - No version field (orders do not use optimistic locking)
 * - correlationId IS included — useful for the client to reference
 *   when reporting issues or tracing a specific order placement
 *
 * STATIC FACTORY METHOD from(Order):
 * Single source of truth for entity-to-DTO mapping.
 * If the entity changes, only this method needs updating.
 * The service layer calls OrderResponse.from(order) — clean and readable.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderResponse {

    private UUID orderId;
    private UUID productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setProductId(order.getProductId());
        response.setProductName(order.getProductName());
        response.setQuantity(order.getQuantity());
        response.setUnitPrice(order.getUnitPrice());
        response.setTotalPrice(order.getTotalPrice());
        response.setStatus(order.getStatus());
        response.setCorrelationId(order.getCorrelationId());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}
