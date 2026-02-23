package com.example.inventory.service;

import com.example.inventory.domain.Inventory;
import com.example.inventory.dto.AddStockRequest;
import com.example.inventory.dto.ReserveStockRequest;
import com.example.inventory.dto.StockResponse;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.InventoryNotFoundException;
import com.example.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    // -------------------------------------------------------------------------
    // ADD STOCK — creates the inventory record for a product
    // -------------------------------------------------------------------------

    /*
     * Creates a new inventory record for a productId.
     * If a record already exists, we add to the existing available quantity
     * instead of rejecting the request. This supports restocking scenarios.
     *
     * TRANSACTION:
     * @Transactional — full read-write transaction.
     * The existsByProductId check and the save() are in the same transaction,
     * so no other thread can insert a duplicate between the check and the save.
     * The UNIQUE constraint on product_id is the final safety net.
     */
    @Transactional
    public StockResponse addStock(AddStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .map(existing -> {
                    existing.setAvailableQuantity(existing.getAvailableQuantity() + request.getQuantity());
                    return existing;
                })
                .orElseGet(() -> {
                    Inventory newInventory = new Inventory();
                    newInventory.setProductId(request.getProductId());
                    newInventory.setAvailableQuantity(request.getQuantity());
                    return newInventory;
                });

        return StockResponse.from(inventoryRepository.save(inventory));
    }

    // -------------------------------------------------------------------------
    // GET STOCK
    // -------------------------------------------------------------------------

    public StockResponse getStock(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        return StockResponse.from(inventory);
    }

    // -------------------------------------------------------------------------
    // RESERVE STOCK
    // -------------------------------------------------------------------------

    /*
     * Moves quantity from available to reserved.
     * This is the most concurrency-sensitive operation in the system.
     *
     * GUARD ORDER (important):
     * 1. Load the inventory — throw 404 if not found
     * 2. Check available >= requested — throw 422 if insufficient
     * 3. Apply the change
     * 4. Save — Hibernate adds WHERE version=? to the UPDATE
     *
     * If two threads pass step 2 concurrently (both see available=10,
     * both request 8), only one will succeed at step 4.
     * The other gets ObjectOptimisticLockingFailureException → 409.
     *
     * The application-level check at step 2 prevents the DB CHECK constraint
     * from being the first line of defence. We want a meaningful 422 error,
     * not a DataIntegrityViolationException from the DB.
     *
     * IMPORTANT — WHY @Transactional IS CRITICAL HERE:
     * The load and the save must be in the same transaction.
     * If they were in separate transactions, the version check would be
     * meaningless — another thread could change the row between the two calls.
     */
    @Transactional
    public StockResponse reserveStock(UUID productId, ReserveStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(productId,
                    request.getQuantity(), inventory.getAvailableQuantity());
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());

        return StockResponse.from(inventoryRepository.save(inventory));
    }

    // -------------------------------------------------------------------------
    // RELEASE RESERVATION
    // -------------------------------------------------------------------------

    /*
     * Moves quantity back from reserved to available.
     * Called when an order is cancelled before confirmation.
     *
     * We guard against releasing more than what is reserved.
     * This prevents reserved_quantity from going negative, which would
     * corrupt the accounting between available and reserved.
     */
    @Transactional
    public StockResponse releaseStock(UUID productId, ReserveStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getReservedQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(productId,
                    request.getQuantity(), inventory.getReservedQuantity());
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - request.getQuantity());
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());

        return StockResponse.from(inventoryRepository.save(inventory));
    }

    // -------------------------------------------------------------------------
    // CONFIRM RESERVATION (permanent deduction)
    // -------------------------------------------------------------------------

    /*
     * Permanently removes quantity from reserved.
     * Called when an order is fulfilled — the stock has physically left
     * the warehouse. It does NOT return to available.
     *
     * After confirm:
     * - reservedQuantity decreases
     * - availableQuantity stays the same (already reduced at reserve time)
     * - Total physical stock = available + reserved decreases permanently
     *
     * STOCK LIFECYCLE SUMMARY:
     *
     * Initial:  available=10, reserved=0
     * Reserve 3: available=7,  reserved=3
     * Confirm 3: available=7,  reserved=0  ← stock is gone
     *
     * Initial:  available=10, reserved=0
     * Reserve 3: available=7,  reserved=3
     * Release 3: available=10, reserved=0  ← stock returned (order cancelled)
     */
    @Transactional
    public StockResponse confirmStock(UUID productId, ReserveStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getReservedQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(productId,
                    request.getQuantity(), inventory.getReservedQuantity());
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - request.getQuantity());

        return StockResponse.from(inventoryRepository.save(inventory));
    }
}
