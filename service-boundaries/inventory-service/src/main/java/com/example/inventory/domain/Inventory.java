package com.example.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * JPA entity — maps to the `inventories` table in inventory_db.
 *
 * SERVICE BOUNDARY RULE ENFORCED HERE:
 * This entity stores only productId (UUID).
 * There is no Product object, no product name, no price — nothing copied
 * from product-service. inventory-service has zero knowledge of what a
 * product is. It only knows "there is a thing identified by this UUID
 * and it has this many units available."
 *
 * If you ever feel the urge to add a `productName` field here for
 * convenience, that is a boundary violation. The caller assembles
 * combined data — not this service.
 *
 * =========================================================================
 * OPTIMISTIC LOCKING — HOW @Version WORKS
 * =========================================================================
 *
 * The `version` field is the heart of this entity.
 *
 * Without @Version — the lost update problem:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Time │ Thread A (reserve 2)    │ Thread B (reserve 3)       │
 * │  1   │ READ: available=10, v=0 │                            │
 * │  2   │                         │ READ: available=10, v=0    │
 * │  3   │ WRITE: available=8      │                            │
 * │  4   │                         │ WRITE: available=7         │
 * │      │ Both succeed. You sold  │ 5 units but deducted only  │
 * │      │ 3. available=7 is wrong.│ That is overselling.       │
 * └─────────────────────────────────────────────────────────────┘
 *
 * With @Version — optimistic locking:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Time │ Thread A (reserve 2)    │ Thread B (reserve 3)       │
 * │  1   │ READ: available=10, v=0 │                            │
 * │  2   │                         │ READ: available=10, v=0    │
 * │  3   │ UPDATE ... SET          │                            │
 * │      │ available=8, version=1  │                            │
 * │      │ WHERE version=0 → 1 row │                            │
 * │      │ updated. SUCCESS.       │                            │
 * │  4   │                         │ UPDATE ... SET             │
 * │      │                         │ available=7, version=1     │
 * │      │                         │ WHERE version=0 → 0 rows   │
 * │      │                         │ updated.                   │
 * │      │                         │ OptimisticLockException!   │
 * │      │                         │ → 409 Conflict             │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Hibernate automatically adds WHERE version = ? to every UPDATE.
 * If 0 rows are updated (because version changed), it throws
 * ObjectOptimisticLockingFailureException (a RuntimeException).
 * GlobalExceptionHandler catches it and returns 409 Conflict.
 *
 * Hibernate also increments the version column on every successful UPDATE.
 * You never touch the version field manually in your code.
 *
 * WHY "OPTIMISTIC"?
 * It assumes conflicts are rare. It does not lock the row on read.
 * It only checks for conflicts at write time.
 * This is correct for most web applications where reads >> writes.
 *
 * PESSIMISTIC LOCKING (SELECT FOR UPDATE) locks the row on read.
 * Use it when conflicts are frequent (e.g. auction bidding, flash sales).
 * It has higher throughput cost because readers block each other.
 *
 * INTERVIEW QUESTION:
 * What exception does Hibernate throw on an optimistic lock conflict?
 * ObjectOptimisticLockingFailureException (Spring Data wrapper) or
 * OptimisticLockException (JPA standard). Both are RuntimeExceptions.
 * The client should receive 409 Conflict and retry the operation.
 */
@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /*
     * The only reference to the product domain.
     * Just a UUID — no @ManyToOne, no join, no foreign key.
     *
     * unique = true — one inventory record per product.
     * A product cannot have two inventory rows.
     * Enforced at both the DB level (UNIQUE constraint in migration)
     * and here as documentation.
     *
     * nullable = false — every inventory record must belong to a product.
     * updatable = false — once set, the productId never changes.
     *   If a product is deleted, the inventory record is deleted too
     *   (handled by the service layer, not a cascade).
     */
    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    private UUID productId;

    /*
     * Units currently available for purchase.
     *
     * The DB-level CHECK (available_quantity >= 0) is the last line of defence.
     * The service layer enforces this first with an explicit check and throws
     * InsufficientStockException (422) before the DB constraint is ever reached.
     * The DB constraint exists because application code can have bugs.
     */
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    /*
     * Units that have been reserved (e.g. in a pending order) but not yet
     * permanently deducted. These units are no longer available for new
     * reservations but have not left the warehouse yet.
     *
     * Stock lifecycle:
     * ADD STOCK   → availableQuantity increases
     * RESERVE     → availableQuantity decreases, reservedQuantity increases
     * RELEASE     → reservedQuantity decreases, availableQuantity increases
     * CONFIRM     → reservedQuantity decreases (stock permanently gone)
     *
     * Total physical stock = availableQuantity + reservedQuantity
     */
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    /*
     * @Version — this single annotation enables optimistic locking.
     *
     * Hibernate manages this field entirely:
     * - Initialises to 0 on INSERT
     * - Increments by 1 on every UPDATE
     * - Adds WHERE version = ? to every UPDATE statement
     * - Throws ObjectOptimisticLockingFailureException if 0 rows updated
     *
     * Rules for this field:
     * - Never set it manually in your code
     * - Never include it in a DTO or expose it via the API
     * - Never use it for business logic
     * It is purely an infrastructure concern.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
