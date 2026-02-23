package com.example.order.controller;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.ErrorResponse;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/*
 * REST controller for order operations.
 *
 * Responsibilities:
 *   - Accept and validate HTTP requests
 *   - Delegate all business logic to OrderService
 *   - Return the correct HTTP status code
 *
 * This class contains NO business logic. It is a thin HTTP adapter.
 * All orchestration (product check, stock reservation, compensation) lives
 * in OrderService. Controllers that contain business logic are harder to
 * test and violate single responsibility.
 *
 * Context path /api is set in application.yml (server.servlet.context-path).
 * This controller maps to POST /api/orders.
 */
@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and management")
public class OrderController {

    private final OrderService orderService;

    /*
     * POST /api/orders
     *
     * Places a new order. The full flow:
     *   1. @Valid triggers Bean Validation on CreateOrderRequest
     *      — MethodArgumentNotValidException → 400 if invalid
     *   2. OrderService.placeOrder() orchestrates the downstream calls
     *   3. Returns 201 Created with the saved OrderResponse
     *
     * @ResponseStatus(CREATED) sets the default success status to 201.
     * Spring uses 200 by default for @PostMapping — 201 is semantically
     * correct for resource creation.
     *
     * WHY @Valid ON THE PARAMETER AND NOT ON THE METHOD:
     * @Valid on the @RequestBody parameter tells Spring to run Bean Validation
     * (JSR-380) on the deserialized object before the method body executes.
     * Placing it on the method would require AOP-based validation (different mechanism).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary     = "Place a new order",
        description = "Validates the product, reserves stock, and persists a CONFIRMED order. " +
                      "Automatically compensates (releases stock) if order persistence fails."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order placed successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation failure)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Stock reservation conflict — retry the request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Product inactive or insufficient stock",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Downstream service returned an error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Downstream service unavailable or timed out",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    }) //for swagger documentation
    public OrderResponse placeOrder(
            @Parameter(description = "Order details: productId and quantity")
            @Valid @RequestBody CreateOrderRequest request) {

        return orderService.placeOrder(request);
    }
}
