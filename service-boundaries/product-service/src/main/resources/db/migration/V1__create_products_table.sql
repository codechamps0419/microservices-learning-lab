-- =============================================================================
-- Migration  : V1__create_products_table.sql
-- Service    : product-service
-- Database   : product_db
-- =============================================================================
-- FLYWAY NAMING CONVENTION:
-- V{version}__{description}.sql  (two underscores between version and description)
--
-- Flyway runs this script exactly once on first startup.
-- It records the execution in the flyway_schema_history table.
-- On every subsequent startup, Flyway sees this script is already applied
-- and skips it. If you modify this file after it has run, Flyway will
-- throw a checksum mismatch error — intentional protection against
-- accidental schema changes.
--
-- TO MAKE SCHEMA CHANGES: always create a new migration file.
-- e.g. V2__add_brand_column.sql
-- Never edit a migration that has already been applied.
-- =============================================================================

CREATE TABLE products
(
    -- UUID primary key.
    -- gen_random_uuid() is a PostgreSQL built-in function (pgcrypto extension
    -- is not needed in PostgreSQL 13+). Hibernate also generates the UUID
    -- in the application layer via GenerationType.UUID, so this DEFAULT
    -- acts as a safety net if a row is ever inserted directly via SQL.
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),

    -- Product display name. Mandatory, max 255 characters.
    name        VARCHAR(255)   NOT NULL,

    -- Optional long-form description. TEXT has no length limit in PostgreSQL.
    description TEXT,

    -- Exact decimal price. NUMERIC(10,2) stores up to 99,999,999.99 exactly.
    -- Never use FLOAT or DOUBLE PRECISION for money — they use binary
    -- floating point and cannot represent values like 0.10 exactly.
    price       NUMERIC(10, 2) NOT NULL,

    -- Optional product category. VARCHAR(100) is sufficient for category names.
    category    VARCHAR(100),

    -- Product lifecycle status. Stored as a string, not an integer.
    -- CHECK constraint ensures only valid enum values reach the database,
    -- providing a second layer of defence after application validation.
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                    CONSTRAINT products_status_check CHECK (status IN ('ACTIVE', 'INACTIVE')),

    -- Audit timestamps. Both are mandatory.
    -- Set by JPA @PrePersist and @PreUpdate callbacks, not by the client.
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,

    CONSTRAINT products_pkey PRIMARY KEY (id)
);

-- Index on category for the search/filter query in ProductRepository.
-- Without this index, searching by category performs a full table scan.
-- Named explicitly so it can be referenced in future migrations if needed.
CREATE INDEX idx_products_category ON products (category);

-- Index on status to efficiently filter active/inactive products.
CREATE INDEX idx_products_status ON products (status);
