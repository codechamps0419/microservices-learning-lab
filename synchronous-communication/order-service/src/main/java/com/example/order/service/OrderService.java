package com.example.order.service;

import com.example.order.client.InventoryClient;
import com.example.order.client.ProductClient;
import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.ProductClientResponse;
import com.example.order.exception.InactiveProductException;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/*
 * Orchestrates the full order placement flow.
 *
 * This class coordinates three separate transactional boundaries:
 *   1. product-service   — product_db  (remote HTTP call)
 *   2. inventory-service — inventory_db (remote HTTP call)
 *   3. order-service     — order_db    (@Transactional wraps only this)
 *
 * =========================================================================
 * THE COMPENSATION PROBLEM
 * =========================================================================
 *
 * There is NO distributed transaction coordinator here. No XA. No 2PC.
 * Each @Transactional wraps only its own service's database.
 *
 * The critical failure window is between steps 2 and 3:
 *
 *   reserveStock()         → SUCCESS  (inventory_db updated)
 *   orderRepository.save() → FAILS   (order_db write fails)
 *
 * At this point the system is inconsistent:
 *   - inventory_db: stock is reserved
 *   - order_db: no order exists
 *
 * Compensation: call releaseStock() to undo the reservation.
 *
 * =========================================================================
 * LIMITATION 1 — COMPENSATION CAN ALSO FAIL
 * =========================================================================
 *
 * releaseStock() is itself a remote HTTP call. If inventory-service is
 * down at the moment of compensation, the release fails. The stock remains
 * reserved with no corresponding order — a known inconsistency.
 *
 * InventoryClient.releaseStock() never throws — it logs at ERROR level so
 * the inconsistency is visible in monitoring. The original save exception
 * is preserved and propagates to the caller unchanged.
 *
 * In production this requires a background reconciliation job that scans
 * for reservations with no confirmed order and releases them after a timeout.
 *
 * =========================================================================
 * LIMITATION 2 — THIS IS NOT A COMPLETE DISTRIBUTED TRANSACTION
 * =========================================================================
 *
 * A true distributed transaction (XA / two-phase commit) would prepare all
 * participants atomically and roll back all if any preparation failed.
 *
 * This implementation is best-effort: if step N fails, we try to undo
 * steps 1..N-1 manually. If the undo also fails, we accept the inconsistency.
 * The guarantee is "we tried our best" — not "the system is always consistent."
 *
 * =========================================================================
 * WHY SAGA WILL BE LEARNED IN A LATER PROJECT
 * =========================================================================
 *
 * The Saga pattern formalises exactly what we are doing manually here:
 *   - Each step has a defined compensating transaction
 *   - A saga orchestrator (or event choreography) manages the sequence
 *   - The saga log provides durability across crashes
 *   - Idempotent operations prevent double-compensation
 *
 * Saga requires an event bus (Kafka/RabbitMQ) or an orchestrator framework
 * (Axon, Eventuate) — neither is in scope for this synchronous-communication
 * project. After experiencing the limitations of manual compensation here,
 * the Saga pattern in a later project will make complete sense.
 *
 * =========================================================================
 * @Transactional SCOPE
 * =========================================================================
 *
 * @Transactional wraps ONLY the order_db save. It does NOT wrap the HTTP
 * calls — those are outside any transaction boundary. Opening a DB transaction
 * before the HTTP calls would hold a connection for the full duration of the
 * remote calls (seconds), exhausting the connection pool under load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;

    /*
     * Places a new order.
     *
     * HAPPY PATH:
     *   1. Read correlationId from MDC (set by CorrelationIdFilter)
     *   2. Call product-service — fetch name + price, assert ACTIVE status
     *   3. Call inventory-service — reserve the requested quantity
     *   4. Calculate totalPrice = unitPrice × quantity
     *   5. Save Order with CONFIRMED status
     *   6. Return OrderResponse
     *
     * FAILURE PATHS:
     *   - ProductNotFoundException    → stop, no inventory call, no save
     *   - InactiveProductException    → stop, no inventory call, no save
     *   - Any inventory exception     → stop, no save (reservation failed, nothing to undo)
     *   - Save fails after reservation → compensate: releaseStock(), then rethrow original
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        String correlationId = MDC.get("correlationId");

        log.info("Placing order: productId={}, quantity={}, correlationId={}",
                request.getProductId(), request.getQuantity(), correlationId);

        // Step 1 — fetch product; throws typed exception on any failure
        ProductClientResponse product = productClient.getProduct(request.getProductId());

        // Step 2 — reject non-ACTIVE products before touching inventory
        // status is a String (not ProductStatus enum) to avoid compile-time coupling.
        // Any status other than ACTIVE is rejected — safe default for unknown future statuses.
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            log.warn("Order rejected: product {} has status={}", request.getProductId(), product.getStatus());
            throw new InactiveProductException(request.getProductId(), product.getStatus());
        }

        // Step 3 — reserve stock; throws typed exception on any failure
        // If this throws, no save is attempted — nothing to compensate.
        inventoryClient.reserveStock(request.getProductId(), request.getQuantity());
        log.info("Stock reserved: productId={}, quantity={}", request.getProductId(), request.getQuantity());

        // Step 4 — build the order entity
        // Point of no return: stock is reserved. If save fails, we must compensate.
        BigDecimal unitPrice  = product.getPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setProductName(product.getName());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(unitPrice);
        order.setTotalPrice(totalPrice);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCorrelationId(correlationId);

        // Step 5 — persist; compensate if save fails
        try {
            Order saved = orderRepository.save(order);
            log.info("Order saved: orderId={}, status={}", saved.getId(), saved.getStatus());
            return OrderResponse.from(saved);

        } catch (Exception e) {
            /*
             * WHY catch (Exception e) IS ACCEPTABLE HERE:
             *
             * This is the one legitimate use of generic exception catching.
             * We are NOT swallowing the exception — we catch it solely to
             * perform compensation, then immediately rethrow it unchanged.
             *
             * A narrower catch (e.g. DataAccessException) would miss
             * RuntimeExceptions from JPA lifecycle callbacks, leaving stock
             * reserved with no order and no compensation attempted.
             *
             * Pattern: catch broadly → compensate → rethrow original.
             * The original exception propagates to GlobalExceptionHandler intact.
             */
            log.error("Order save failed after stock reservation — compensating. " +
                      "productId={}, quantity={}, correlationId={}",
                      request.getProductId(), request.getQuantity(), correlationId, e);

            // releaseStock never throws — logs ERROR internally if compensation fails.
            inventoryClient.releaseStock(request.getProductId(), request.getQuantity());

            throw e; // preserve original failure as the main exception
        }
    }
}
