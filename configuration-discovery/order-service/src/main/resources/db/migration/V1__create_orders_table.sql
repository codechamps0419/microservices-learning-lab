-- =============================================================================
-- Migration  : V1__create_orders_table.sql
-- Service    : order-service
-- Database   : order_db
-- =============================================================================

CREATE TABLE orders
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),

    -- Reference to the product in product-service.
    -- This is just a UUID — there is NO foreign key to product_db.
    -- A foreign key would require both tables on the same database connection,
    -- which is impossible when each service owns its own database.
    -- order-service trusts the productId it receives and stores it as-is.
    product_id   UUID           NOT NULL,

    -- Snapshot of the product name at the time the order was placed.
    -- Stored permanently. Never updated even if the product is renamed.
    -- This ensures order history is always accurate.
    product_name VARCHAR(255)   NOT NULL,

    -- Number of units ordered. Must be at least 1.
    -- Application layer validates this first via @Min(1) on the request DTO.
    -- DB CHECK is the last line of defence.
    quantity     INTEGER        NOT NULL
                     CONSTRAINT orders_quantity_check CHECK (quantity > 0),

    -- Snapshot of the price per unit at the time the order was placed.
    -- NUMERIC(10,2) — exact decimal arithmetic, never floating point.
    -- Stored permanently. Never updated even if the product price changes.
    unit_price   NUMERIC(10, 2) NOT NULL
                     CONSTRAINT orders_unit_price_check CHECK (unit_price > 0),

    -- Calculated as unit_price * quantity at order time.
    -- Stored permanently so the total is always correct for the historical order.
    -- NUMERIC(12,2) — slightly wider than unit_price to accommodate large totals.
    total_price  NUMERIC(12, 2) NOT NULL
                     CONSTRAINT orders_total_price_check CHECK (total_price > 0),

    -- Lifecycle status of the order.
    -- Stored as a string — safe to add new statuses without corrupting existing rows.
    -- CHECK constraint ensures only valid enum values reach the database.
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                     CONSTRAINT orders_status_check
                         CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),

    -- Correlation ID from the original HTTP request.
    -- Stored for traceability — links this order to logs across all three services.
    -- Nullable: orders placed before correlation ID was introduced have no value.
    correlation_id VARCHAR(100),

    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,

    CONSTRAINT orders_pkey PRIMARY KEY (id)
);

-- Index on product_id — supports findByProductId() queries (order history per product).
CREATE INDEX idx_orders_product_id ON orders (product_id);

-- Index on status — supports findByStatus() queries (e.g. all PENDING orders).
CREATE INDEX idx_orders_status ON orders (status);
