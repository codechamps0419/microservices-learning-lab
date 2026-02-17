package com.example.inventory.controller;

import com.example.inventory.dto.AddStockRequest;
import com.example.inventory.dto.ReserveStockRequest;
import com.example.inventory.dto.StockResponse;
import com.example.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // -------------------------------------------------------------------------
    // POST /api/inventories
    // -------------------------------------------------------------------------
    /*
     * EXAMPLE REQUEST (create new):
     * POST /api/inventories
     * { "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "quantity": 100 }
     *
     * EXAMPLE RESPONSE (201 Created):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 100,
     *   "reservedQuantity": 0,
     *   "updatedAt": "2024-01-15T10:30:00"
     * }
     *
     * EXAMPLE REQUEST (restock existing):
     * POST /api/inventories
     * { "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "quantity": 50 }
     *
     * EXAMPLE RESPONSE (201 Created — available is now 150):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 150,
     *   "reservedQuantity": 0,
     *   "updatedAt": "2024-01-15T11:00:00"
     * }
     */
    @PostMapping
    public ResponseEntity<StockResponse> addStock(@RequestBody @Valid AddStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.addStock(request));
    }

    // -------------------------------------------------------------------------
    // GET /api/inventories/{productId}
    // -------------------------------------------------------------------------
    /*
     * EXAMPLE REQUEST:
     * GET /api/inventories/a1b2c3d4-e5f6-7890-abcd-ef1234567890
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 97,
     *   "reservedQuantity": 3,
     *   "updatedAt": "2024-01-15T10:45:00"
     * }
     *
     * EXAMPLE RESPONSE (404 Not Found — stock never added):
     * {
     *   "timestamp": "2024-01-15T10:45:00",
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Inventory not found for product id: a1b2c3d4-..."
     * }
     */
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryService.getStock(productId));
    }

    // -------------------------------------------------------------------------
    // POST /api/inventories/{productId}/reserve
    // -------------------------------------------------------------------------
    /*
     * EXAMPLE REQUEST:
     * POST /api/inventories/a1b2c3d4-e5f6-7890-abcd-ef1234567890/reserve
     * { "quantity": 3 }
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 97,
     *   "reservedQuantity": 3,
     *   "updatedAt": "2024-01-15T10:45:00"
     * }
     *
     * EXAMPLE RESPONSE (422 — insufficient stock):
     * {
     *   "timestamp": "...",
     *   "status": 422,
     *   "error": "Unprocessable Entity",
     *   "message": "Insufficient stock for product id: ... Requested: 200, Available: 97"
     * }
     *
     * EXAMPLE RESPONSE (409 — concurrent update conflict):
     * {
     *   "timestamp": "...",
     *   "status": 409,
     *   "error": "Conflict",
     *   "message": "Stock was updated by another request. Please retry the operation."
     * }
     */
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<StockResponse> reserveStock(
            @PathVariable UUID productId,
            @RequestBody @Valid ReserveStockRequest request) {
        return ResponseEntity.ok(inventoryService.reserveStock(productId, request));
    }

    // -------------------------------------------------------------------------
    // POST /api/inventories/{productId}/release
    // -------------------------------------------------------------------------
    /*
     * EXAMPLE REQUEST:
     * POST /api/inventories/a1b2c3d4-e5f6-7890-abcd-ef1234567890/release
     * { "quantity": 3 }
     *
     * EXAMPLE RESPONSE (200 OK — stock returned to available):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 100,
     *   "reservedQuantity": 0,
     *   "updatedAt": "2024-01-15T11:00:00"
     * }
     */
    @PostMapping("/{productId}/release")
    public ResponseEntity<StockResponse> releaseStock(
            @PathVariable UUID productId,
            @RequestBody @Valid ReserveStockRequest request) {
        return ResponseEntity.ok(inventoryService.releaseStock(productId, request));
    }

    // -------------------------------------------------------------------------
    // POST /api/inventories/{productId}/confirm
    // -------------------------------------------------------------------------
    /*
     * EXAMPLE REQUEST:
     * POST /api/inventories/a1b2c3d4-e5f6-7890-abcd-ef1234567890/confirm
     * { "quantity": 3 }
     *
     * EXAMPLE RESPONSE (200 OK — reserved stock permanently deducted):
     * {
     *   "id": "f1e2d3c4-b5a6-7890-abcd-123456789abc",
     *   "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "availableQuantity": 97,
     *   "reservedQuantity": 0,
     *   "updatedAt": "2024-01-15T11:15:00"
     * }
     */
    @PostMapping("/{productId}/confirm")
    public ResponseEntity<StockResponse> confirmStock(
            @PathVariable UUID productId,
            @RequestBody @Valid ReserveStockRequest request) {
        return ResponseEntity.ok(inventoryService.confirmStock(productId, request));
    }
}
