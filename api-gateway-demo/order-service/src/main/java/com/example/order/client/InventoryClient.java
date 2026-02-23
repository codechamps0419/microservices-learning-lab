package com.example.order.client;

import com.example.order.dto.StockClientResponse;
import com.example.order.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

/*
 * HTTP client for inventory-service.
 *
 * Exposes two operations:
 * - reserveStock  — POST /api/inventories/{productId}/reserve
 * - releaseStock  — POST /api/inventories/{productId}/release
 *
 * Same design principles as ProductClient:
 * - RestClientResponseException never escapes this class
 * - No generic Exception catching
 * - Correlation ID forwarded from MDC on every call
 * - Every non-2xx response maps to a specific typed exception
 *
 * =========================================================================
 * NOTE ON ACTUAL ENDPOINT PATHS
 * =========================================================================
 *
 * inventory-service exposes:
 *   POST /api/inventories/{productId}/reserve
 *   POST /api/inventories/{productId}/release
 *
 * These are the real paths from InventoryController. The client uses
 * these exact paths — not a hypothetical /reservations path.
 *
 * =========================================================================
 * WHY releaseStock DOES NOT THROW ON FAILURE
 * =========================================================================
 *
 * releaseStock is a compensation call — it is only invoked when the order
 * save has already failed. At that point, order-service is in an error path.
 *
 * If releaseStock also fails, there is nothing more order-service can do
 * synchronously. The stock remains reserved with no corresponding order —
 * a known inconsistency that requires a background reconciliation job in
 * production. Throwing here would mask the original order save failure.
 *
 * Instead, we log the failure at ERROR level so it is visible in monitoring,
 * and return normally so the original exception propagates to the caller.
 */
@Slf4j
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /*
     * Calls POST /api/inventories/{productId}/reserve on inventory-service.
     *
     * Moves `quantity` units from available to reserved.
     * Returns StockClientResponse — never null.
     *
     * Error mapping:
     *   404 -> InventoryNotFoundException    (no inventory record for this product)
     *   409 -> InventoryConflictException    (optimistic lock conflict — retry)
     *   422 -> InsufficientStockException    (not enough available stock)
     *        | InventoryValidationException  (other validation failure)
     *   500 -> InventoryServiceException     (inventory-service internal error)
     *   503 -> InventoryServiceUnavailableException
     *   timeout -> InventoryServiceUnavailableException
     */
    public StockClientResponse reserveStock(UUID productId, int quantity) {
        log.info("[RestClient] --> inventory-service reserveStock START productId={} quantity={}", productId, quantity);

        try {
            StockClientResponse result = restClient.post()
                    .uri("/api/inventories/{productId}/reserve", productId)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, (request, response) -> {
                        throw new InventoryNotFoundException(productId);
                    })
                    .onStatus(HttpStatus.CONFLICT::equals, (request, response) -> {
                        throw new InventoryConflictException(
                                "Stock reservation conflict for product: " + productId
                                + ". Another request updated the stock simultaneously. Please retry.");
                    })
                    .onStatus(HttpStatus.UNPROCESSABLE_ENTITY::equals, (request, response) -> {
                        /*
                         * inventory-service returns 422 for both:
                         * - Insufficient stock (InsufficientStockException inside inventory-service)
                         * - General validation failures
                         *
                         * We read the response message to distinguish them.
                         * "Insufficient stock" in the message -> InsufficientStockException
                         * Anything else                       -> InventoryValidationException
                         *
                         * This is a pragmatic approach. In production, a structured error
                         * response with an error code field would be cleaner than parsing
                         * the message string.
                         */
                        String body = new String(response.getBody().readAllBytes());
                        if (body.toLowerCase().contains("insufficient stock")) {
                            throw new InsufficientStockException(
                                    "Insufficient stock available for product: " + productId);
                        }
                        throw new InventoryValidationException(
                                "Inventory validation failed for product: " + productId);
                    })
                    .onStatus(HttpStatus.INTERNAL_SERVER_ERROR::equals, (request, response) -> {
                        throw new InventoryServiceException(
                                "inventory-service returned 500 for productId: " + productId);
                    })
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, (request, response) -> {
                        throw new InventoryServiceUnavailableException(
                                "inventory-service returned 503 for productId: " + productId);
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, response) -> {
                                throw new InventoryServiceException(
                                        "inventory-service returned unexpected status "
                                        + response.getStatusCode() + " for productId: " + productId);
                            })
                    .body(StockClientResponse.class);

            log.info("[RestClient] <-- inventory-service reserveStock END productId={} reserved={}",
                    productId, result.getReservedQuantity());
            return result;

        } catch (InventoryNotFoundException | InventoryConflictException
                 | InsufficientStockException | InventoryValidationException
                 | InventoryServiceException | InventoryServiceUnavailableException e) {
            throw e;

        } catch (ResourceAccessException e) {
            log.error("inventory-service is unreachable for productId={}: {}", productId, e.getMessage());
            throw new InventoryServiceUnavailableException(
                    "inventory-service is currently unavailable. Please try again later.", e);

        } catch (RestClientResponseException e) {
            log.error("Unexpected response from inventory-service for productId={}: status={}",
                    productId, e.getStatusCode());
            throw new InventoryServiceException(
                    "Unexpected response from inventory-service: " + e.getStatusCode());
        }
    }

    /*
     * Calls POST /api/inventories/{productId}/release on inventory-service.
     *
     * Moves `quantity` units back from reserved to available.
     * This is a compensation call — only invoked when the order save fails
     * after stock has already been reserved.
     *
     * DOES NOT THROW on failure — see class-level comment above.
     * Logs at ERROR level so the inconsistency is visible in monitoring.
     */
    public void releaseStock(UUID productId, int quantity) {
        log.info("Releasing stock (compensation): productId={}, quantity={}", productId, quantity);

        try {
            restClient.post()
                    .uri("/api/inventories/{productId}/release", productId)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, response) -> {
                                /*
                                 * Log but do not throw — we are already in the error path.
                                 * Throwing here would mask the original failure.
                                 */
                                log.error(
                                    "COMPENSATION FAILED: inventory-service returned {} " +
                                    "when releasing stock for productId={}. " +
                                    "Stock quantity={} remains reserved with no order. " +
                                    "Manual reconciliation required.",
                                    response.getStatusCode(), productId, quantity);
                            })
                    .toBodilessEntity();

            log.info("Stock released successfully: productId={}, quantity={}", productId, quantity);

        } catch (ResourceAccessException e) {
            log.error(
                "COMPENSATION FAILED: inventory-service unreachable when releasing stock " +
                "for productId={}. Stock quantity={} remains reserved with no order. " +
                "Manual reconciliation required. Cause: {}",
                productId, quantity, e.getMessage());

        } catch (RestClientResponseException e) {
            log.error(
                "COMPENSATION FAILED: unexpected response from inventory-service " +
                "when releasing stock for productId={}. Status={}. " +
                "Stock quantity={} remains reserved with no order.",
                productId, e.getStatusCode(), quantity);
        }
    }
}
