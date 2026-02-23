package com.example.inventory.repository;

import com.example.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/*
 * All operations are keyed by productId, not the inventory's own id.
 * The inventory id is an internal surrogate key.
 * The productId is the business key — what callers know and use.
 */
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /*
     * Spring Data derives: SELECT * FROM inventories WHERE product_id = ?
     * Returns Optional because the inventory record may not exist yet
     * for a given productId (stock not yet added for that product).
     */
    Optional<Inventory> findByProductId(UUID productId);

    /*
     * Used by the service to enforce one inventory record per product
     * before attempting an INSERT.
     * Derives: SELECT COUNT(*) > 0 FROM inventories WHERE product_id = ?
     */
    boolean existsByProductId(UUID productId);
}
