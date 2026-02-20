package com.example.order.controller;

import com.example.order.client.InventoryWebClient;
import com.example.order.dto.StockClientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/*
 * Demo controller — shows WebClient in action.
 *
 * This endpoint exists purely for learning and comparison.
 * It calls inventory-service via WebClient instead of RestClient.
 * The existing POST /orders flow is completely unchanged.
 *
 * Compare the two approaches:
 *   GET /api/orders/inventory/{productId}/restclient  → RestClient via InventoryClient
 *   GET /api/orders/inventory/{productId}/webclient   → WebClient via InventoryWebClient
 *
 * Both return the same StockClientResponse — the transport is the only difference.
 */
@RestController
@RequestMapping("/orders/inventory")
@RequiredArgsConstructor
@Tag(name = "WebClient Demo", description = "Demonstrates WebClient call to inventory-service")
public class OrderWebClientController {

    private final InventoryWebClient inventoryWebClient;

    @Operation(
        summary = "Get stock via WebClient",
        description = "Fetches stock details from inventory-service using reactive WebClient " +
                      "instead of synchronous RestClient. Same data, different programming model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock found"),
        @ApiResponse(responseCode = "404", description = "No inventory record for this product"),
        @ApiResponse(responseCode = "503", description = "inventory-service unavailable")
    })
    @GetMapping("/{productId}/webclient")
    public ResponseEntity<StockClientResponse> getStockViaWebClient(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryWebClient.getStock(productId));
    }
}
