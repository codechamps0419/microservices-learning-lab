package com.example.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Represents the JSON response order-service receives from product-service.
 *
 * =========================================================================
 * WHY THIS EXISTS INSTEAD OF IMPORTING ProductResponse FROM product-service
 * =========================================================================
 *
 * A remote call is NOT the same as a Java method call.
 *
 * A Java method call:
 * - Is in-process — same JVM, same memory space
 * - Cannot fail due to network issues
 * - Returns a typed Java object directly
 * - Is atomic with the caller's transaction
 * - Has no latency beyond CPU time
 *
 * A remote HTTP call:
 * - Crosses a network boundary — different process, different machine
 * - Can fail for reasons unrelated to business logic:
 *   connection refused, timeout, DNS failure, packet loss
 * - Returns bytes over the wire that must be deserialised
 * - Is completely outside the caller's transaction boundary
 * - Has latency: network round-trip + remote processing time
 * - Can partially succeed (request sent, response never received)
 *
 * Because of this fundamental difference, order-service must NOT import
 * ProductResponse from product-service's compiled code. Doing so would:
 *
 * 1. Create compile-time coupling — if product-service changes ProductResponse,
 *    order-service fails to compile even if the JSON contract is unchanged.
 *
 * 2. Force both services to be built and deployed together — they are no
 *    longer independently deployable.
 *
 * 3. Couple order-service to product-service's internal domain model —
 *    fields like ProductStatus (an enum from product-service's package)
 *    would need to be imported too, pulling in more and more of
 *    product-service's code.
 *
 * This DTO depends only on the HTTP contract (the JSON shape).
 * If product-service adds a new field, Jackson ignores it by default.
 * order-service is unaffected and does not need to be redeployed.
 *
 * =========================================================================
 * FIELDS INCLUDED
 * =========================================================================
 *
 * Only the fields order-service actually needs — not a full mirror of
 * product-service's ProductResponse. This is the minimum necessary contract.
 *
 * - id          — to confirm we got the right product
 * - name        — stored as productName snapshot on the order
 * - price       — stored as unitPrice snapshot on the order
 * - status      — order-service checks this is "ACTIVE" before proceeding
 *
 * Fields NOT included (description, category, createdAt, updatedAt,
 * availableStock) — order-service has no use for them.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductClientResponse {

    private UUID id;
    private String name;
    private BigDecimal price;

    /*
     * Stored as String, not as ProductStatus enum from product-service.
     *
     * If we used ProductStatus, we would need to import it from
     * product-service's package — exactly the coupling we are avoiding.
     *
     * order-service checks: "ACTIVE".equals(status)
     * Simple, decoupled, and resilient to product-service adding new statuses.
     */
    private String status;
}
