package com.example.order.client;

import com.example.order.dto.ProductClientResponse;
import com.example.order.exception.ProductNotFoundException;
import com.example.order.exception.ProductServiceException;
import com.example.order.exception.ProductServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/*
 * HTTP client for product-service.
 *
 * This class is the ONLY place in order-service that knows how to talk to
 * product-service. All HTTP concerns — URL construction, header propagation,
 * error mapping, timeout handling — are contained here.
 *
 * The rest of order-service (OrderService, OrderController) works with
 * typed exceptions and typed DTOs. They never see RestClientResponseException,
 * HTTP status codes, or raw response bodies.
 *
 * =========================================================================
 * WHY RestClientResponseException MUST NOT ESCAPE THIS CLASS
 * =========================================================================
 *
 * RestClientResponseException is a Spring HTTP infrastructure exception.
 * If it escapes into OrderService or OrderController, those classes become
 * coupled to the HTTP transport layer. They would need to know about HTTP
 * status codes, response bodies, and Spring's RestClient internals.
 *
 * This violates the separation of concerns:
 * - OrderService should think in business terms: "product not found",
 *   "stock unavailable", "service down"
 * - ProductClient should think in HTTP terms: "404", "503", "timeout"
 *
 * The client layer translates HTTP language into business language.
 * Nothing outside this class ever sees an HTTP exception.
 *
 * =========================================================================
 * WHY GENERIC Exception CATCHING IS HARMFUL
 * =========================================================================
 *
 * catch (Exception e) { ... } catches everything — including:
 * - NullPointerException (a bug in this code)
 * - IllegalArgumentException (a programming error)
 * - OutOfMemoryError (a JVM crisis)
 * - InterruptedException (thread management)
 *
 * Catching all of these and wrapping them in a single exception type:
 * 1. Hides bugs — a NullPointerException in the mapping code would be
 *    silently swallowed and reported as "product service unavailable"
 * 2. Makes debugging impossible — the real cause is lost
 * 3. Produces misleading error messages — the caller gets the wrong diagnosis
 *
 * We catch ONLY the specific exceptions we know how to handle:
 * - RestClientResponseException — non-2xx HTTP response from the server
 * - ResourceAccessException     — network failure (timeout, connection refused)
 *
 * Any other exception propagates naturally and surfaces as a 500 with a
 * real stack trace — which is exactly what you want when debugging a bug.
 */
@Slf4j
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Qualifier("productRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /*
     * Calls GET /api/products/{productId} on product-service.
     *
     * Returns ProductClientResponse — never null.
     * Throws a typed business exception for every non-2xx response.
     *
     * CORRELATION ID PROPAGATION:
     * The X-Correlation-ID header is read from MDC and forwarded on every
     * outgoing request. MDC is populated by CorrelationIdFilter (Phase 4)
     * for every incoming request to order-service.
     *
     * If MDC has no correlationId (e.g. during startup health checks),
     * the header is simply not sent — no NullPointerException.
     *
     * WHY MDC AND NOT A METHOD PARAMETER?
     * Passing correlationId as a parameter through every method signature
     * (OrderController -> OrderService -> ProductClient) pollutes every
     * layer with an infrastructure concern. MDC is thread-local — it is
     * available anywhere in the same thread without being passed explicitly.
     */
    public ProductClientResponse getProduct(UUID productId) {
        log.info("[RestClient] --> product-service getProduct START productId={}", productId);

        try {
            ProductClientResponse result = restClient.get()
                    .uri("/api/products/{productId}", productId)
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, (request, response) -> {
                        throw new ProductNotFoundException(productId);
                    })
                    .onStatus(HttpStatus.INTERNAL_SERVER_ERROR::equals, (request, response) -> {
                        throw new ProductServiceException(
                                "product-service returned 500 for productId: " + productId);
                    })
                    .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, (request, response) -> {
                        throw new ProductServiceUnavailableException(
                                "product-service returned 503 for productId: " + productId);
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, response) -> {
                                throw new ProductServiceException(
                                        "product-service returned unexpected status "
                                        + response.getStatusCode() + " for productId: " + productId);
                            })
                    .body(ProductClientResponse.class);

            log.info("[RestClient] ← product-service getProduct END productId={} name={}",
                    productId, result.getName());
            return result;

        } catch (ProductNotFoundException | ProductServiceException
                 | ProductServiceUnavailableException e) {
            /*
             * Re-throw our own typed exceptions immediately.
             * They were thrown inside the onStatus() lambdas above.
             * Without this re-throw, the outer catch below would wrap them.
             */
            throw e;

        } catch (ResourceAccessException e) {
            /*
             * ResourceAccessException wraps I/O failures:
             * - java.net.ConnectException     — connection refused (service is down)
             * - java.net.SocketTimeoutException — connect or read timeout exceeded
             *
             * We preserve the original cause so the stack trace is not lost.
             * The message tells the caller which service failed.
             */
            log.error("product-service is unreachable for productId={}: {}", productId, e.getMessage());
            throw new ProductServiceUnavailableException(
                    "product-service is currently unavailable. Please try again later.", e);

        } catch (RestClientResponseException e) {
            /*
             * Catch-all for any non-2xx response not handled by onStatus() above.
             * This should not be reached in normal operation, but defensive
             * handling prevents RestClientResponseException from escaping.
             */
            log.error("Unexpected response from product-service for productId={}: status={}",
                    productId, e.getStatusCode());
            throw new ProductServiceException(
                    "Unexpected response from product-service: " + e.getStatusCode());
        }
    }

    public ProductInstanceInfo getInstanceInfo() {
        return restClient.get()
                .uri("/api/products/instance-info")
                .retrieve()
                .body(ProductInstanceInfo.class);
    }

    public record ProductInstanceInfo(String applicationName, int port, String instanceId) {}
}
