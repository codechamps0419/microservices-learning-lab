package com.example.order.client;

import com.example.order.dto.StockClientResponse;
import com.example.order.exception.InventoryNotFoundException;
import com.example.order.exception.InventoryServiceException;
import com.example.order.exception.InventoryServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/*
 * WebClient-based client for inventory-service.
 *
 * This is a DEMO client — it exists alongside InventoryClient (RestClient)
 * to show the difference between the two approaches.
 * The existing POST /orders flow still uses InventoryClient unchanged.
 *
 * =========================================================================
 * WebClient vs RestClient
 * =========================================================================
 *
 * RestClient (used in InventoryClient):
 *   - Synchronous / blocking
 *   - Current thread waits until the response arrives
 *   - Simple, easy to read, no reactive knowledge needed
 *   - Part of spring-boot-starter-web
 *
 * WebClient (used here):
 *   - Reactive / non-blocking
 *   - Uses Project Reactor: Mono<T> (0 or 1 result), Flux<T> (0..N results)
 *   - The thread is NOT blocked while waiting — it is released and reused
 *   - Requires spring-boot-starter-webflux
 *   - More complex but handles high concurrency with fewer threads
 *
 * =========================================================================
 * MONO<T> — THE CORE REACTIVE TYPE
 * =========================================================================
 *
 * Mono<T> represents a future value — a promise that will eventually
 * produce either:
 *   - One value (onNext)
 *   - An error (onError)
 *   - Nothing (onComplete with no value)
 *
 * Mono is LAZY — nothing happens until someone subscribes.
 * .block() subscribes and waits for the result, converting back to
 * synchronous behaviour. We use .block() here because the rest of
 * order-service is synchronous (Tomcat threads, not reactive).
 *
 * In a fully reactive application you would return Mono<T> from the
 * controller and never call .block(). For this project, .block() is
 * the correct bridge between reactive WebClient and synchronous Spring MVC.
 *
 * =========================================================================
 * ERROR HANDLING WITH onStatus()
 * =========================================================================
 *
 * WebClient's onStatus() works similarly to RestClient's onStatus():
 *   - Predicate matches the HTTP status code
 *   - The function returns a Mono<Throwable> — the exception to throw
 *   - flatMap(response -> Mono.error(exception)) creates the error signal
 *
 * The exception propagates through the reactive pipeline and surfaces
 * when .block() is called — at which point it is thrown as a normal
 * Java exception.
 */
@Slf4j
@Component
public class InventoryWebClient {

    private final WebClient webClient;

    /*
     * @Qualifier("inventoryWebClient") injects the bean defined in WebClientConfig.
     * Same pattern as RestClientConfig — separate bean per downstream service.
     */
    public InventoryWebClient(@Qualifier("inventoryWebClientBean") WebClient webClient) {
        this.webClient = webClient;
    }

    /*
     * Calls GET /api/inventories/{productId} on inventory-service using WebClient.
     *
     * .block() converts the reactive Mono<StockClientResponse> into a plain
     * StockClientResponse — bridging reactive WebClient into synchronous Spring MVC.
     *
     * The correlation ID is forwarded the same way as in InventoryClient —
     * read from MDC and set as a request header.
     */
    public StockClientResponse getStock(UUID productId) {
        log.info("[WebClient] --> inventory-service getStock START productId={}", productId);

        StockClientResponse result = webClient.get()
                .uri("/api/inventories/{productId}", productId)
                .header("X-Correlation-ID", MDC.get("correlationId"))
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, response ->
                    Mono.error(new InventoryNotFoundException(productId))
                )
                .onStatus(HttpStatus.SERVICE_UNAVAILABLE::equals, response ->
                    Mono.error(new InventoryServiceUnavailableException(
                            "inventory-service returned 503 for productId: " + productId))
                )
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                    response.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new InventoryServiceException(
                                "inventory-service returned " + response.statusCode() + ": " + body)))
                )
                .bodyToMono(StockClientResponse.class)
                /*
                 * onErrorMap transforms a WebClientRequestException (network failure)
                 * into our typed InventoryServiceUnavailableException.
                 * WebClientRequestException is thrown when the connection is refused
                 * or the read timeout is exceeded — same scenarios as
                 * ResourceAccessException in RestClient.
                 */
                .onErrorMap(WebClientRequestException.class, e -> {
                    log.error("[WebClient] inventory-service unreachable productId={}: {}", productId, e.getMessage());
                    return new InventoryServiceUnavailableException(
                            "inventory-service is currently unavailable.", e);
                })
                .block();

        log.info("[WebClient] <-- inventory-service getStock END productId={} available={}",
                productId, result.getAvailableQuantity());
        return result;
    }
}
