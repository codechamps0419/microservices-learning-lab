package com.example.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * JPA entity — maps to the `orders` table in order_db.
 *
 * =========================================================================
 * WHY ORDER SERVICE STORES productId
 * =========================================================================
 *
 * order-service needs to know WHICH product was ordered so it can:
 * - Display the order to the customer
 * - Trigger stock reservation in inventory-service
 * - Link the order to the correct product if the order is queried later
 *
 * productId is stored as a plain UUID — not as a @ManyToOne relationship,
 * not as a foreign key to product_db. It is just an identifier.
 *
 * order-service does not own the product. It only references it by ID.
 * This is the cross-service reference pattern: store the ID, never the object.
 *
 * =========================================================================
 * WHY productName AND unitPrice ARE STORED AS SNAPSHOTS
 * =========================================================================
 *
 * At the moment an order is placed, productName and unitPrice are facts
 * of that specific transaction. They must be preserved exactly as they
 * were at order time, regardless of what happens to the product later.
 *
 * Consider what happens without snapshots:
 *
 *   - Customer orders "Wireless Keyboard" at £49.99
 *   - Product is later renamed to "Wireless Keyboard Pro"
 *   - Price is later changed to £59.99
 *   - The order now shows the wrong name and wrong price
 *   - The customer's invoice is incorrect
 *   - The order total no longer matches what the customer paid
 *
 * With snapshots:
 *   - productName = "Wireless Keyboard"  — frozen at order time
 *   - unitPrice   = 49.99               — frozen at order time
 *   - totalPrice  = 99.98               — calculated once, never recalculated
 *   - The order is a permanent historical record, not a live view
 *
 * This is intentional denormalization. It is not a boundary violation.
 * order-service is not copying product data to avoid making a call —
 * it is preserving the state of a transaction at a specific point in time.
 *
 * =========================================================================
 * WHY ORDER SERVICE MUST NOT USE A Product ENTITY RELATIONSHIP
 * =========================================================================
 *
 * A @ManyToOne relationship to a Product entity would require:
 * 1. Importing com.example.product.domain.Product into order-service
 *    — compile-time coupling between two independent services
 * 2. A foreign key from orders.product_id to products.id
 *    — impossible because products is in product_db and orders is in order_db
 *    — they are on separate database connections
 * 3. Both services sharing the same JPA EntityManager / DataSource
 *    — this destroys the database-per-service principle entirely
 *
 * If product-service changes its Product entity, order-service would
 * fail to compile. The services would no longer be independently deployable.
 * They would be a distributed monolith — the worst of both worlds.
 *
 * =========================================================================
 * WHY THE ORDER TRANSACTION REMAINS LOCAL TO order_db
 * =========================================================================
 *
 * A database transaction is scoped to a single database connection.
 * order-service's @Transactional annotation wraps operations against order_db
 * only. It has no knowledge of product_db or inventory_db.
 *
 * This means:
 * - The INSERT into orders is atomic within order_db
 * - The stock reservation in inventory_db is a SEPARATE transaction
 *   managed entirely by inventory-service
 * - There is no distributed transaction spanning both databases
 *
 * If the order INSERT fails after stock has been reserved, the stock
 * reservation is NOT automatically rolled back — it is in a different
 * database. This is why compensation (releasing the reservation) must
 * be done explicitly in the service layer (Phase 5).
 *
 * This is the fundamental constraint of microservices: you cannot have
 * ACID guarantees across service boundaries. You get local atomicity
 * within each service, and eventual consistency across services.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /*
     * Reference to the product in product-service.
     *
     * Just a UUID — no @ManyToOne, no join, no foreign key to product_db.
     * order-service does not own the product. It only knows its ID.
     *
     * nullable = false  — every order must reference a product
     * updatable = false — once placed, the product on an order never changes
     */
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    /*
     * Snapshot of the product name at the time the order was placed.
     *
     * This value is copied from product-service's response at order time
     * and stored permanently. It never changes after the order is created,
     * even if the product is renamed in product-service.
     *
     * updatable = false — snapshot fields are immutable after creation
     */
    @Column(name = "product_name", nullable = false, length = 255, updatable = false)
    private String productName;

    /*
     * Number of units ordered.
     *
     * CHECK (quantity > 0) is enforced at the DB level in the migration.
     * The service layer validates this first via @Min(1) on the request DTO.
     *
     * updatable = false — quantity is fixed at order time
     */
    @Column(nullable = false, updatable = false)
    private int quantity;

    /*
     * Snapshot of the product price per unit at the time the order was placed.
     *
     * BigDecimal — the only correct type for monetary values in Java.
     * double and float use binary floating point and cannot represent
     * values like 0.10 exactly. BigDecimal uses exact decimal arithmetic.
     *
     * precision = 10, scale = 2 — maps to NUMERIC(10,2) in PostgreSQL.
     * Supports prices up to 99,999,999.99.
     *
     * updatable = false — price snapshot is immutable after creation
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    /*
     * Total price = unitPrice × quantity.
     *
     * Calculated once at order time and stored. Never recalculated from
     * live data. If unitPrice changes in product-service, this total
     * remains correct for the historical order.
     *
     * updatable = false — total is fixed at order time
     */
    @Column(name = "total_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalPrice;

    /*
     * Current lifecycle status of the order.
     *
     * @Enumerated(EnumType.STRING) — stores "PENDING", "CONFIRMED", "REJECTED"
     * as text. Safe to reorder enum values or add new ones without corrupting
     * existing rows.
     *
     * NOT updatable = false here — status can change (PENDING -> CONFIRMED/REJECTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /*
     * Correlation ID from the incoming HTTP request.
     *
     * Stored on the order so that if a customer queries their order,
     * the correlation ID from the original placement request is available
     * for tracing. Nullable because orders placed before correlation ID
     * was introduced would not have this value.
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
