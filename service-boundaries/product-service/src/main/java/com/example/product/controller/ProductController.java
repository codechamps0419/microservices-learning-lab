package com.example.product.controller;

import com.example.product.dto.*;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/*
 * REST controller for product operations.
 *
 * @RestController = @Controller + @ResponseBody
 * Every method return value is serialised to JSON automatically.
 * No need for @ResponseBody on individual methods.
 *
 * @RequestMapping("/products") — base path for all endpoints in this controller.
 * server.servlet.context-path=/api is set in application.yml, so the full
 * URL becomes /api/products. The controller only declares the resource path,
 * not the global prefix — avoids repeating /api in every @RequestMapping.
 *
 * CONTROLLER RESPONSIBILITIES (and nothing more):
 * 1. Parse and validate the HTTP request
 * 2. Call the service
 * 3. Return the appropriate HTTP response
 *
 * The controller must NOT contain any business logic.
 * No if-else for business rules. No direct repository calls.
 * If you find yourself writing business logic here, move it to the service.
 *
 * @RequiredArgsConstructor — injects ProductService via constructor injection.
 *
 * INTERVIEW QUESTION:
 * Why do we return ResponseEntity instead of just the DTO?
 * ResponseEntity gives us full control over the HTTP response:
 * - Status code (201 Created vs 200 OK)
 * - Response headers (e.g. Location header after POST)
 * - Response body
 * Returning just the DTO always gives 200 OK, which is incorrect for POST.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // -------------------------------------------------------------------------
    // POST /api/products
    // -------------------------------------------------------------------------
    /*
     * @RequestBody — deserialises the JSON request body into CreateProductRequest.
     * @Valid       — triggers Bean Validation on the DTO fields.
     *               If validation fails, Spring throws MethodArgumentNotValidException
     *               BEFORE the method body executes.
     *               GlobalExceptionHandler catches it and returns 400.
     *
     * Returns 201 Created — the correct status for a successful resource creation.
     * 200 OK on a POST is a common mistake. 201 signals that a new resource exists.
     *
     * EXAMPLE REQUEST:
     * POST /api/products
     * {
     *   "name": "Wireless Keyboard",
     *   "description": "Compact wireless keyboard with USB receiver",
     *   "price": 49.99,
     *   "category": "Electronics"
     * }
     *
     * EXAMPLE RESPONSE (201 Created):
     * {
     *   "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "name": "Wireless Keyboard",
     *   "description": "Compact wireless keyboard with USB receiver",
     *   "price": 49.99,
     *   "category": "Electronics",
     *   "status": "ACTIVE",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T10:30:00",
     *   "availableStock": null
     * }
     *
     * EXAMPLE RESPONSE (400 Bad Request — validation failure):
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Validation failed",
     *   "errors": {
     *     "name": "Product name is required",
     *     "price": "Price must be greater than zero"
     *   }
     * }
     *
     * EXAMPLE RESPONSE (409 Conflict — duplicate name):
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 409,
     *   "error": "Conflict",
     *   "message": "A product with the name 'Wireless Keyboard' already exists"
     * }
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid CreateProductRequest request) {

        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/products/{id}
    // -------------------------------------------------------------------------
    /*
     * @PathVariable — binds the {id} segment from the URL to the UUID parameter.
     * Spring automatically converts the String "a1b2c3d4-..." to a UUID.
     * If the string is not a valid UUID format, Spring returns 400 Bad Request
     * before the method is called.
     *
     * EXAMPLE REQUEST:
     * GET /api/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "name": "Wireless Keyboard",
     *   "description": "Compact wireless keyboard with USB receiver",
     *   "price": 49.99,
     *   "category": "Electronics",
     *   "status": "ACTIVE",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T10:30:00",
     *   "availableStock": null
     * }
     *
     * EXAMPLE RESPONSE (404 Not Found):
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Product not found with id: a1b2c3d4-e5f6-7890-abcd-ef1234567890"
     * }
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    // -------------------------------------------------------------------------
    // GET /api/products?category=Electronics&name=keyboard
    // -------------------------------------------------------------------------
    /*
     * @RequestParam(required = false) — both parameters are optional.
     * If omitted, Spring passes null to the service, which the JPQL
     * query handles by skipping that filter condition.
     *
     * EXAMPLE REQUESTS:
     * GET /api/products                              (all active products)
     * GET /api/products?category=Electronics        (filter by category)
     * GET /api/products?name=keyboard               (search by name fragment)
     * GET /api/products?category=Electronics&name=keyboard (both filters)
     *
     * EXAMPLE RESPONSE (200 OK):
     * [
     *   {
     *     "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *     "name": "Wireless Keyboard",
     *     "description": "Compact wireless keyboard with USB receiver",
     *     "price": 49.99,
     *     "category": "Electronics",
     *     "status": "ACTIVE",
     *     "createdAt": "2024-01-15T10:30:00",
     *     "updatedAt": "2024-01-15T10:30:00",
     *     "availableStock": null
     *   }
     * ]
     *
     * Returns empty array [] when no products match — never 404.
     * 404 means the endpoint does not exist. An empty result is still 200.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String name) {

        return ResponseEntity.ok(productService.searchProducts(category, name));
    }

    // -------------------------------------------------------------------------
    // PUT /api/products/{id}
    // -------------------------------------------------------------------------
    /*
     * Full replacement of product information.
     * All updatable fields must be provided.
     *
     * EXAMPLE REQUEST:
     * PUT /api/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890
     * {
     *   "name": "Wireless Keyboard Pro",
     *   "description": "Updated compact wireless keyboard with USB-C receiver",
     *   "price": 59.99,
     *   "category": "Electronics"
     * }
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "name": "Wireless Keyboard Pro",
     *   "description": "Updated compact wireless keyboard with USB-C receiver",
     *   "price": 59.99,
     *   "category": "Electronics",
     *   "status": "ACTIVE",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T11:45:00",
     *   "availableStock": null
     * }
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateProductRequest request) {

        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/products/{id}/price
    // -------------------------------------------------------------------------
    /*
     * Updates price only. All other fields remain unchanged.
     *
     * EXAMPLE REQUEST:
     * PATCH /api/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890/price
     * {
     *   "price": 44.99
     * }
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "name": "Wireless Keyboard Pro",
     *   "description": "Updated compact wireless keyboard with USB-C receiver",
     *   "price": 44.99,
     *   "category": "Electronics",
     *   "status": "ACTIVE",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T12:00:00",
     *   "availableStock": null
     * }
     */
    @PatchMapping("/{id}/price")
    public ResponseEntity<ProductResponse> updatePrice(
            @PathVariable UUID id,
            @RequestBody @Valid PriceUpdateRequest request) {

        return ResponseEntity.ok(productService.updatePrice(id, request));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/products/{id}/status
    // -------------------------------------------------------------------------
    /*
     * Activates or deactivates a product.
     *
     * EXAMPLE REQUEST (deactivate):
     * PATCH /api/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890/status
     * {
     *   "status": "INACTIVE"
     * }
     *
     * EXAMPLE RESPONSE (200 OK):
     * {
     *   "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     *   "name": "Wireless Keyboard Pro",
     *   "description": "Updated compact wireless keyboard with USB-C receiver",
     *   "price": 44.99,
     *   "category": "Electronics",
     *   "status": "INACTIVE",
     *   "createdAt": "2024-01-15T10:30:00",
     *   "updatedAt": "2024-01-15T12:15:00",
     *   "availableStock": null
     * }
     *
     * EXAMPLE RESPONSE (400 Bad Request — invalid enum value):
     * {
     *   "timestamp": "2024-01-15T12:15:00",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Malformed request body. Check field types and accepted values."
     * }
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid StatusUpdateRequest request) {

        return ResponseEntity.ok(productService.updateStatus(id, request));
    }
}
