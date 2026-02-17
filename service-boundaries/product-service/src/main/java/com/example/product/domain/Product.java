package com.example.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * JPA entity — maps directly to the `products` table in product_db.
 *
 * RULE: This class must NEVER be returned from a controller.
 * Controllers always return DTOs (ProductResponse).
 *
 * WHY?
 * Entities carry JPA internal state: lazy-loaded proxies, Hibernate
 * session references, and bidirectional relationship cycles.
 * Serialising an entity with Jackson can cause:
 * - LazyInitializationException (session already closed)
 * - Infinite recursion on bidirectional relationships
 * - Leaking internal fields (version, audit timestamps) to API consumers
 *
 * LOMBOK ANNOTATIONS USED:
 * @Getter        — generates getters for all fields
 * @Setter        — generates setters for all fields
 * @NoArgsConstructor — generates the no-arg constructor JPA requires
 *                      to instantiate entities via reflection
 *
 * We deliberately do NOT use @Data on entities.
 * @Data generates equals() and hashCode() based on all fields,
 * which breaks JPA identity semantics and causes issues with
 * Hibernate's dirty checking and Set-based collections.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    /*
     * UUID primary key.
     *
     * GenerationType.UUID — Hibernate 6+ generates the UUID in the
     * application layer before the INSERT statement is sent to the DB.
     * This means the ID is available immediately after repository.save()
     * without needing a DB sequence or auto-increment round-trip.
     *
     * updatable = false — the primary key must never change after creation.
     * nullable  = false — every row must have an ID.
     *
     * WHY UUID OVER AUTO-INCREMENT LONG?
     * - Safe to generate without a DB round-trip (useful in distributed systems)
     * - Not guessable by clients (GET /products/1, 2, 3 is a security risk)
     * - Works across multiple database instances without collision
     *
     * TRADE-OFF:
     * UUIDs are larger (128-bit vs 64-bit) and random UUIDs cause
     * index fragmentation in B-tree indexes. For this project the
     * benefit outweighs the cost.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /*
     * Product display name.
     *
     * nullable = false — enforced at the DB level as NOT NULL.
     * length   = 255   — maps to VARCHAR(255), matching the Flyway migration.
     *
     * NOTE: JPA length attribute only affects schema generation.
     *       Since we use Flyway, schema generation is disabled.
     *       The length here is documentation — the real constraint
     *       lives in V1__create_products_table.sql.
     */
    @Column(nullable = false, length = 255)
    private String name;

    /*
     * Optional long-form product description.
     *
     * columnDefinition = "TEXT" maps to PostgreSQL TEXT type — no length limit.
     * No nullable = false because a product can exist without a description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /*
     * Product price.
     *
     * BigDecimal — the ONLY correct type for monetary values in Java.
     *
     * WHY NOT double OR float?
     * double and float use binary floating point (IEEE 754).
     * Binary floating point cannot represent 0.10 exactly.
     * Try this in Java: System.out.println(0.1 + 0.2) → 0.30000000000000004
     * This causes rounding errors in financial calculations.
     * BigDecimal uses exact decimal arithmetic — mandatory for prices.
     *
     * precision = 10 — up to 10 total digits
     * scale     = 2  — exactly 2 decimal places
     * Maps to NUMERIC(10,2) in PostgreSQL — supports up to 99,999,999.99
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /*
     * Product category (e.g. "Electronics", "Clothing").
     *
     * Optional — nullable by default (no nullable = false constraint).
     * length = 100 keeps the column lean; category names are short strings.
     */
    @Column(length = 100)
    private String category;

    /*
     * Lifecycle status of the product.
     *
     * @Enumerated(EnumType.STRING) — stores "ACTIVE" or "INACTIVE" as text.
     * See ProductStatus.java for the full explanation of why STRING not ORDINAL.
     *
     * Default value is ACTIVE — a newly created product is visible immediately.
     * nullable = false — every product must have a status.
     * length   = 20    — enough for any future status values.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    /*
     * Timestamp when this product was first persisted.
     *
     * updatable = false — once written on INSERT, this column never changes.
     *                     Hibernate will exclude it from all UPDATE statements.
     * nullable  = false — every product must have a creation timestamp.
     *
     * Set automatically by the @PrePersist callback below.
     * We do NOT rely on the caller to set this — it is infrastructure concern.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     * Timestamp of the most recent update to any field.
     *
     * Updated automatically by the @PreUpdate callback below.
     * nullable = false — always reflects the last modification time.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * @PrePersist — JPA lifecycle callback.
     * Runs automatically just before Hibernate executes the INSERT statement.
     *
     * Sets both timestamps so the service layer never needs to set them manually.
     * This is an infrastructure concern, not a business concern.
     *
     * PRODUCTION MISTAKE TO AVOID:
     * Do not use new Date() or System.currentTimeMillis() here.
     * LocalDateTime.now() uses the JVM clock. In production, ensure
     * the JVM timezone is set explicitly (e.g. -Duser.timezone=UTC)
     * to avoid timezone-related bugs across environments.
     */
    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /*
     * @PreUpdate — JPA lifecycle callback.
     * Runs automatically just before Hibernate executes an UPDATE statement.
     *
     * Keeps updatedAt current on every save without any manual intervention
     * in the service layer.
     */
    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
