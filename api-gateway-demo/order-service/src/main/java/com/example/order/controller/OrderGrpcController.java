package com.example.order.controller;

import com.example.order.dto.ProductClientResponse;
import com.example.order.grpc.ProductGrpcClient;
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
 * Demo controller — shows gRPC in action.
 *
 * This endpoint exists purely for learning and comparison.
 * It calls product-service via gRPC instead of REST.
 * The existing POST /orders flow is completely unchanged.
 *
 * Compare the two approaches:
 *   GET /api/orders/product/{productId}       → REST via ProductClient
 *   GET /api/orders/product/{productId}/grpc  → gRPC via ProductGrpcClient
 *
 * Both return the same ProductClientResponse — the transport is the only
 * difference. This makes the comparison direct and easy to observe.
 */
@RestController
@RequestMapping("/orders/product")
@RequiredArgsConstructor
@Tag(name = "gRPC Demo", description = "Demonstrates gRPC call to product-service")
public class OrderGrpcController {

    private final ProductGrpcClient productGrpcClient;

    @Operation(
        summary = "Get product via gRPC",
        description = "Fetches product details from product-service using gRPC (HTTP/2 + protobuf) " +
                      "instead of REST (HTTP/1.1 + JSON). Same data, different transport."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "503", description = "product-service unavailable")
    })
    @GetMapping("/{productId}/grpc")
    public ResponseEntity<ProductClientResponse> getProductViaGrpc(@PathVariable UUID productId) {
        return ResponseEntity.ok(productGrpcClient.getProduct(productId));
    }
}
