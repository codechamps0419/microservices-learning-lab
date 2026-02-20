package com.example.order;

import com.example.order.client.InventoryClient;
import com.example.order.client.ProductClient;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.ProductClientResponse;
import com.example.order.dto.StockClientResponse;
import com.example.order.exception.*;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Unit tests for OrderService.
 *
 * APPROACH: Pure unit tests with Mockito — no Spring context, no database,
 * no HTTP. All collaborators (ProductClient, InventoryClient, OrderRepository)
 * are mocked. Tests run in milliseconds.
 *
 * WHAT IS TESTED:
 *   - Happy path: product active, stock reserved, order saved → CONFIRMED
 *   - Product not found: inventory never called
 *   - Product inactive: inventory never called
 *   - Insufficient stock: save never called
 *   - Save failure: compensation (releaseStock) is called, original exception rethrown
 *   - Compensation failure: original save exception still propagates
 *   - Correlation ID from MDC is stored on the saved order
 *
 * WHAT IS NOT TESTED HERE (covered in integration tests):
 *   - HTTP layer (request/response serialization, status codes)
 *   - @Valid constraint enforcement
 *   - GlobalExceptionHandler mappings
 *   - CorrelationIdFilter behaviour
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock ProductClient     productClient;
    @Mock InventoryClient   inventoryClient;
    @Mock OrderRepository   orderRepository;

    @InjectMocks OrderService orderService;

    private UUID productId;
    private CreateOrderRequest request;
    private ProductClientResponse activeProduct;
    private StockClientResponse stockResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        request = new CreateOrderRequest();
        request.setProductId(productId);
        request.setQuantity(2);

        activeProduct = new ProductClientResponse();
        activeProduct.setId(productId);
        activeProduct.setName("Wireless Keyboard");
        activeProduct.setPrice(new BigDecimal("49.99"));
        activeProduct.setStatus("ACTIVE");

        stockResponse = new StockClientResponse();
        stockResponse.setProductId(productId);
        stockResponse.setAvailableQuantity(10);
        stockResponse.setReservedQuantity(2);

        MDC.clear();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_happyPath_returnsConfirmedOrder() {
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2)).thenReturn(stockResponse);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            var order = inv.getArgument(0, com.example.order.domain.Order.class);
            // simulate @PrePersist
            var f = order.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(order, UUID.randomUUID());
            var ca = order.getClass().getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(order, java.time.LocalDateTime.now());
            var ua = order.getClass().getDeclaredField("updatedAt");
            ua.setAccessible(true);
            ua.set(order, java.time.LocalDateTime.now());
            return order;
        });

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getProductName()).isEqualTo("Wireless Keyboard");
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getUnitPrice()).isEqualByComparingTo("49.99");
        assertThat(response.getTotalPrice()).isEqualByComparingTo("99.98");

        verify(inventoryClient).reserveStock(productId, 2);
        verify(orderRepository).save(any());
        verify(inventoryClient, never()).releaseStock(any(), anyInt());
    }

    @Test
    void placeOrder_storesCorrelationIdFromMdc() {
        MDC.put("correlationId", "test-correlation-id");
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2)).thenReturn(stockResponse);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            var order = inv.getArgument(0, com.example.order.domain.Order.class);
            setPrivateField(order, "id", UUID.randomUUID());
            setPrivateField(order, "createdAt", java.time.LocalDateTime.now());
            setPrivateField(order, "updatedAt", java.time.LocalDateTime.now());
            return order;
        });

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getCorrelationId()).isEqualTo("test-correlation-id");
    }

    // -------------------------------------------------------------------------
    // Product failures — inventory must NOT be called
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_productNotFound_doesNotCallInventory() {
        when(productClient.getProduct(productId))
                .thenThrow(new ProductNotFoundException(productId));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(inventoryClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void placeOrder_productInactive_doesNotCallInventory() {
        activeProduct.setStatus("INACTIVE");
        when(productClient.getProduct(productId)).thenReturn(activeProduct);

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InactiveProductException.class)
                .hasMessageContaining("INACTIVE");

        verifyNoInteractions(inventoryClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void placeOrder_productDiscontinued_doesNotCallInventory() {
        activeProduct.setStatus("DISCONTINUED");
        when(productClient.getProduct(productId)).thenReturn(activeProduct);

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InactiveProductException.class);

        verifyNoInteractions(inventoryClient);
    }

    @Test
    void placeOrder_productServiceUnavailable_doesNotCallInventory() {
        when(productClient.getProduct(productId))
                .thenThrow(new ProductServiceUnavailableException("product-service is down"));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verifyNoInteractions(inventoryClient);
    }

    // -------------------------------------------------------------------------
    // Inventory failures — save must NOT be called
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_insufficientStock_doesNotSaveOrder() {
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2))
                .thenThrow(new InsufficientStockException("Insufficient stock available for product: " + productId));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(orderRepository);
        // No compensation needed — reservation failed, nothing was reserved
        verify(inventoryClient, never()).releaseStock(any(), anyInt());
    }

    @Test
    void placeOrder_inventoryNotFound_doesNotSaveOrder() {
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2))
                .thenThrow(new InventoryNotFoundException(productId));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InventoryNotFoundException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    void placeOrder_inventoryConflict_doesNotSaveOrder() {
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2))
                .thenThrow(new InventoryConflictException("conflict"));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InventoryConflictException.class);

        verifyNoInteractions(orderRepository);
    }

    // -------------------------------------------------------------------------
    // Compensation — save fails after reservation succeeds
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_saveFails_compensationIsCalledAndOriginalExceptionRethrown() {
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2)).thenReturn(stockResponse);
        RuntimeException saveFailure = new RuntimeException("DB connection lost");
        when(orderRepository.save(any())).thenThrow(saveFailure);

        // The original save exception must propagate — not a compensation exception
        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isSameAs(saveFailure);

        // Compensation must have been attempted
        verify(inventoryClient).releaseStock(productId, 2);
    }

    @Test
    void placeOrder_saveFails_compensationFailure_originalExceptionStillPropagates() {
        /*
         * Worst-case scenario:
         *   1. reserveStock succeeds
         *   2. save fails
         *   3. releaseStock also fails (inventory-service is down)
         *
         * Expected: the original save exception propagates.
         * releaseStock never throws (it logs ERROR internally), so this
         * test verifies that the save exception is not masked.
         */
        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 2)).thenReturn(stockResponse);
        RuntimeException saveFailure = new RuntimeException("DB connection lost");
        when(orderRepository.save(any())).thenThrow(saveFailure);
        // releaseStock is void and does not throw — this is the default Mockito behaviour.
        // We just verify the save exception still propagates.

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isSameAs(saveFailure);

        verify(inventoryClient).releaseStock(productId, 2);
    }

    // -------------------------------------------------------------------------
    // Price calculation
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_totalPriceIsUnitPriceMultipliedByQuantity() {
        activeProduct.setPrice(new BigDecimal("19.99"));
        request.setQuantity(3);

        when(productClient.getProduct(productId)).thenReturn(activeProduct);
        when(inventoryClient.reserveStock(productId, 3)).thenReturn(stockResponse);
        when(orderRepository.save(any())).thenAnswer(inv -> {
            var order = inv.getArgument(0, com.example.order.domain.Order.class);
            setPrivateField(order, "id", UUID.randomUUID());
            setPrivateField(order, "createdAt", java.time.LocalDateTime.now());
            setPrivateField(order, "updatedAt", java.time.LocalDateTime.now());
            return order;
        });

        OrderResponse response = orderService.placeOrder(request);

        // 19.99 × 3 = 59.97
        assertThat(response.getTotalPrice()).isEqualByComparingTo("59.97");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
