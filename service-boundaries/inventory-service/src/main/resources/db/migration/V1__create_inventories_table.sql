-- =============================================================================
-- Migration  : V1__create_inventories_table.sql
-- Service    : inventory-service
-- Database   : inventory_db
-- =============================================================================

CREATE TABLE inventories
(
    id                 UUID    NOT NULL DEFAULT gen_random_uuid(),

    -- The only reference to the product domain.
    -- This is just a UUID — there is NO foreign key to product_db.
    -- A foreign key would create a hidden cross-database dependency,
    -- violating the service boundary rule.
    -- The UNIQUE constraint enforces one inventory record per product.
    product_id         UUID    NOT NULL,

    -- Available stock — units that can be reserved right now.
    -- CHECK constraint is the database-level guard against negative stock.
    -- The application layer checks first and throws InsufficientStockException.
    -- This constraint is the last line of defence if application logic has a bug.
    available_quantity INTEGER NOT NULL DEFAULT 0
        CONSTRAINT inventories_available_quantity_check CHECK (available_quantity >= 0),

    -- Reserved stock — units held for pending orders, not yet confirmed.
    reserved_quantity  INTEGER NOT NULL DEFAULT 0
        CONSTRAINT inventories_reserved_quantity_check CHECK (reserved_quantity >= 0),

    -- Optimistic locking version counter.
    -- Hibernate reads this on every SELECT and includes it in every UPDATE:
    -- UPDATE inventories SET ..., version = version + 1 WHERE id = ? AND version = ?
    -- If 0 rows are updated, Hibernate throws ObjectOptimisticLockingFailureException.
    version            BIGINT  NOT NULL DEFAULT 0,

    updated_at         TIMESTAMP NOT NULL,

    CONSTRAINT inventories_pkey PRIMARY KEY (id),
    CONSTRAINT inventories_product_id_unique UNIQUE (product_id)
);

-- Index on product_id — every query in InventoryRepository uses product_id,
-- not the surrogate id. Without this index, every lookup is a full table scan.
CREATE INDEX idx_inventories_product_id ON inventories (product_id);
