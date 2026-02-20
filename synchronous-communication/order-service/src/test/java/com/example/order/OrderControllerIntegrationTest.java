package com.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/*
 * Integration tests for POST /api/orders.
 *
 * APPROACH: Full Spring Boot context + WireMock.
 *
 * @SpringBootTest loads the complete application context including:
 *   - OrderController, OrderService, ProductClient, InventoryClient
 *   - CorrelationIdFilter, GlobalExceptionHandler
 *   - RestClientConfig (configured to point at WireMock servers)
 *   - H2 in-memory database (from application-test.yml)
 *   - Flyway migration (V1__create_orders_table.sql runs against H2)
 *
 * WireMock starts a real HTTP server on a random port and serves stubbed
 * responses. ProductClient and InventoryClient make real HTTP calls to
 * WireMock — the same code path as production, just with a mock server.
 *
 * @DynamicPropertySource overrides the service URLs in application-test.yml
 * with the actual WireMock ports chosen at runtime.
 *
 * WHY NOT MockRestServiceServer?
 * MockRestServiceServer intercepts at the RestTemplate/RestClient level —
 * it bypasses the actual HTTP stack. WireMock starts a real server, so
 * timeout handling, header forwarding, and error parsing are all exercised.
 *
 * WHAT IS TESTED:
 *   - Full HTTP request → controller → service → clients → DB → response
 *   - Correct HTTP status codes for every scenario
 *   - ErrorResponse body shape for failures
 *   - X-Correlation-ID forwarded to downstream services
 *   - X-Correlation-ID echoed back in response header
 *   - @Valid constraint enforcement (400 responses)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    // Two WireMock servers — one per downstream service
    static WireMockServer productWireMock;
    static WireMockServer inventoryWireMock;

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // WireMock lifecycle — start once for all tests, reset stubs between tests
    // -------------------------------------------------------------------------

    @BeforeAll
    static void startWireMock() {
        productWireMock   = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        inventoryWireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        productWireMock.start();
        inventoryWireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        productWireMock.stop();
        inventoryWireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        // Clear all stubs between tests — prevents test pollution
        productWireMock.resetAll();
        inventoryWireMock.resetAll();
    }

    /*
     * Injects WireMock ports into Spring's property registry BEFORE the
     * application context starts. This overrides the placeholder URLs in
     * application-test.yml with the real WireMock addresses.
     */
    @DynamicPropertySource
    static void overrideServiceUrls(DynamicPropertyRegistry registry) {
        registry.add("services.product-service.url",
                () -> "http://localhost:" + productWireMock.port());
        registry.add("services.inventory-service.url",
                () -> "http://localhost:" + inventoryWireMock.port());
    }

    // -------------------------------------------------------------------------
    // Test data helpers
    // -------------------------------------------------------------------------

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private String activeProductJson() {
        return """
                {
                  "id":     "%s",
                  "name":   "Wireless Keyboard",
                  "price":  49.99,
                  "status": "ACTIVE"
                }
                """.formatted(PRODUCT_ID);
    }

    private String stockReservedJson() {
        return """
                {
                  "productId":         "%s",
                  "availableQuantity": 8,
                  "reservedQuantity":  2
                }
                """.formatted(PRODUCT_ID);
    }

    private String orderRequestJson(UUID productId, Integer quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "productId", productId.toString(),
                "quantity",  quantity
        ));
    }

    private void stubActiveProduct() {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(activeProductJson())));
    }

    private void stubReserveSuccess() {
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stockReservedJson())));
    }

    private void stubReleaseSuccess() {
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/release"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stockReservedJson())));
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void postOrders_happyPath_returns201WithConfirmedOrder() throws Exception {
        stubActiveProduct();
        stubReserveSuccess();

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.productName").value("Wireless Keyboard"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(49.99))
                .andExpect(jsonPath("$.totalPrice").value(99.98))
                .andExpect(jsonPath("$.orderId").isNotEmpty());
    }

    @Test
    void postOrders_correlationIdEchoedInResponseHeader() throws Exception {
        stubActiveProduct();
        stubReserveSuccess();

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "my-trace-id")
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "my-trace-id"));
    }

    @Test
    void postOrders_noCorrelationIdProvided_generatesOneAndEchosIt() throws Exception {
        stubActiveProduct();
        stubReserveSuccess();

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void postOrders_correlationIdForwardedToProductService() throws Exception {
        stubActiveProduct();
        stubReserveSuccess();

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "trace-abc")
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isCreated());

        // Verify product-service received the correlation ID header
        productWireMock.verify(getRequestedFor(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .withHeader("X-Correlation-ID", equalTo("trace-abc")));

        // Verify inventory-service received the correlation ID header
        inventoryWireMock.verify(postRequestedFor(
                urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .withHeader("X-Correlation-ID", equalTo("trace-abc")));
    }

    // -------------------------------------------------------------------------
    // Validation failures — 400
    // -------------------------------------------------------------------------

    @Test
    void postOrders_missingProductId_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Product ID is required")));
    }

    @Test
    void postOrders_missingQuantity_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": \"" + PRODUCT_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Quantity is required")));
    }

    @Test
    void postOrders_quantityZero_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Quantity must be at least 1")));
    }

    @Test
    void postOrders_negativeQuantity_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, -5)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Product failures
    // -------------------------------------------------------------------------

    @Test
    void postOrders_productNotFound_returns404() throws Exception {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Inventory must not have been called
        inventoryWireMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void postOrders_productInactive_returns422() throws Exception {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","name":"Old Keyboard","price":29.99,"status":"INACTIVE"}
                                """.formatted(PRODUCT_ID))));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("INACTIVE")));

        inventoryWireMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void postOrders_productServiceDown_returns503() throws Exception {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        inventoryWireMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void postOrders_productService500_returns502() throws Exception {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    // -------------------------------------------------------------------------
    // Inventory failures
    // -------------------------------------------------------------------------

    @Test
    void postOrders_insufficientStock_returns422() throws Exception {
        stubActiveProduct();
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Insufficient stock available\"}")));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void postOrders_inventoryConflict_returns409() throws Exception {
        stubActiveProduct();
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .willReturn(aResponse().withStatus(409)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void postOrders_inventoryServiceDown_returns503() throws Exception {
        stubActiveProduct();
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void postOrders_inventoryService500_returns502() throws Exception {
        stubActiveProduct();
        inventoryWireMock.stubFor(post(urlPathEqualTo("/api/inventories/" + PRODUCT_ID + "/reserve"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isBadGateway());
    }

    // -------------------------------------------------------------------------
    // Error response shape
    // -------------------------------------------------------------------------

    @Test
    void postOrders_errorResponse_hasConsistentShape() throws Exception {
        productWireMock.stubFor(get(urlPathEqualTo("/api/products/" + PRODUCT_ID))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "shape-test-id")
                        .content(orderRequestJson(PRODUCT_ID, 2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value("shape-test-id"));
    }
}
