package com.example.inventory.service;

import com.example.inventory.dto.AddStockRequest;
import com.example.inventory.dto.ReserveStockRequest;
import com.example.inventory.dto.StockResponse;
import com.example.inventory.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Concurrency integration test for InventoryService.
 *
 * WHY TESTCONTAINERS AND NOT H2?
 * H2 is an in-memory database that does not fully replicate PostgreSQL behaviour.
 * Specifically:
 * - H2's locking semantics differ from PostgreSQL's MVCC model
 * - The CHECK constraint syntax may differ
 * - Optimistic locking behaviour under concurrent load can differ
 *
 * Testcontainers spins up a real PostgreSQL Docker container for the test.
 * The test runs against the exact same database engine as production.
 * This gives us confidence that the optimistic locking behaviour we test
 * is the same behaviour we will see in production.
 *
 * PREREQUISITE: Docker must be running on your machine.
 *
 * @Testcontainers — JUnit 5 extension that manages the container lifecycle.
 *   Starts the container before the first test, stops it after the last.
 *
 * @Container — marks the PostgreSQLContainer as a Testcontainers-managed container.
 *   static field = one container shared across all tests in this class (faster).
 *   non-static field = new container per test (slower, more isolated).
 *
 * @ServiceConnection — Spring Boot 3.1+ auto-configuration magic.
 *   Reads the container's host, port, database name, username, and password
 *   and automatically overrides the DataSource configuration in application.yml.
 *   No need for @DynamicPropertySource or manual property overrides.
 *
 * @SpringBootTest — loads the full Spring application context.
 *   This is an integration test, not a unit test.
 *   The real InventoryService, InventoryRepository, and DataSource are used.
 *   No mocking.
 *
 * INTERVIEW QUESTION:
 * What is the difference between @SpringBootTest and @DataJpaTest?
 * @SpringBootTest  — loads the full application context (web layer, service, repo)
 * @DataJpaTest     — loads only the JPA slice (repository + entity + DataSource)
 *                    faster, but cannot test service layer logic
 * For concurrency tests we need @SpringBootTest because we are testing
 * the service layer's transaction behaviour, not just the repository.
 */
@SpringBootTest
class InventoryConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(InventoryConcurrencyTest.class);

    /*
     * Uses inventory_test_db on local PostgreSQL — isolated from inventory_db.
     * Flyway runs all migrations automatically before tests.
     * No Docker required.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      () -> "jdbc:postgresql://localhost:5432/inventory_test_db");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    private InventoryService inventoryService;

    private UUID productId;

    /*
     * @BeforeEach — runs before every @Test method.
     * Creates a fresh productId and adds 10 units of stock.
     * Each test starts with a clean, known state.
     */
    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        log.info("\n============================================================");
        log.info("SETUP STARTED | thread={} | productId={}", Thread.currentThread().getName(), productId);

        AddStockRequest addStockRequest = new AddStockRequest();
        addStockRequest.setProductId(productId);
        addStockRequest.setQuantity(10);
        StockResponse initialStock = inventoryService.addStock(addStockRequest);
        log.info("SETUP COMPLETED | productId={} | available={} | reserved={}",
                productId, initialStock.getAvailableQuantity(), initialStock.getReservedQuantity());
        log.info("============================================================");
    }

    // -------------------------------------------------------------------------
    // TEST 1: Concurrent reservations must not oversell
    // -------------------------------------------------------------------------

    /*
     * SCENARIO:
     * 10 threads all try to reserve 1 unit simultaneously.
     * Available stock = 10.
     * Expected: all 10 succeed, final available = 0, reserved = 10.
     *
     * Without @Version, some threads would read the same version and
     * multiple updates would collide, potentially resulting in wrong counts.
     * With @Version, each successful update increments the version,
     * forcing concurrent threads to retry or fail with 409.
     *
     * HOW THE TEST WORKS:
     * CountDownLatch startLatch — all 10 threads wait at the gate.
     * When countDown() is called, all 10 are released simultaneously,
     * maximising the chance of a real concurrent collision.
     *
     * We count successes and failures separately.
     * Success + Failure must equal total threads (10).
     * Available stock must equal 10 - successes (no overselling).
     */
    @Test
    @DisplayName("10 concurrent reservations of 1 unit each — no overselling, total = 10")
    void concurrentReservations_noOverselling() throws InterruptedException {
        log.info("TEST 1 STARTED | 10 concurrent reservations of 1 unit");
        StockResponse beforeTest = inventoryService.getStock(productId);
        logStock("TEST 1 INITIAL STOCK", beforeTest);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ReserveStockRequest request = new ReserveStockRequest();
        request.setQuantity(1);

        for (int i = 0; i < threadCount; i++) {
            int taskNumber = i + 1;
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                log.info("TEST 1 | task={} | thread={} | waiting at start gate", taskNumber, threadName);
                try {
                    startLatch.await();
                    log.info("TEST 1 | task={} | thread={} | calling reserveStock(quantity=1)", taskNumber, threadName);

                    StockResponse result = inventoryService.reserveStock(productId, request);
                    int successes = successCount.incrementAndGet();
                    log.info("TEST 1 | task={} | SUCCESS | available={} | reserved={} | totalSuccess={}",
                            taskNumber, result.getAvailableQuantity(), result.getReservedQuantity(), successes);
                } catch (Exception e) {
                    int failures = failureCount.incrementAndGet();
                    log.warn("TEST 1 | task={} | FAILED | exception={} | message={} | totalFailure={}",
                            taskNumber, e.getClass().getSimpleName(), e.getMessage(), failures);
                } finally {
                    doneLatch.countDown();
                    log.info("TEST 1 | task={} | finished | remainingTasks={}", taskNumber, doneLatch.getCount());
                }
            });
        }

        log.info("TEST 1 | releasing all {} threads", threadCount);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        log.info("TEST 1 | all threads completed within timeout={}", completed);
        assertThat(completed).as("All concurrent tasks should finish within 10 seconds").isTrue();

        StockResponse finalStock = inventoryService.getStock(productId);
        logStock("TEST 1 FINAL STOCK", finalStock);
        log.info("TEST 1 SUMMARY | success={} | failure={} | total={}",
                successCount.get(), failureCount.get(), threadCount);

        // Every thread either succeeded or failed — no thread was lost
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);

        // Available stock = initial(10) - successes. Never negative.
        assertThat(finalStock.getAvailableQuantity()).isEqualTo(10 - successCount.get());

        // Reserved stock = exactly the number of successful reservations
        assertThat(finalStock.getReservedQuantity()).isEqualTo(successCount.get());

        // The critical assertion: available never went negative
        assertThat(finalStock.getAvailableQuantity()).isGreaterThanOrEqualTo(0);
        log.info("TEST 1 PASSED | no overselling detected");
    }

    // -------------------------------------------------------------------------
    // TEST 2: Oversell attempt — requesting more than available
    // -------------------------------------------------------------------------

    /*
     * SCENARIO:
     * 5 threads each try to reserve 3 units simultaneously.
     * Available stock = 10. Total requested = 15.
     * Expected: some succeed (up to floor(10/3) = 3 threads),
     * the rest fail with InsufficientStockException or OptimisticLockException.
     * Final available stock must be >= 0 (never negative).
     */
    @Test
    @DisplayName("Concurrent oversell attempt — available stock never goes negative")
    void concurrentOversell_stockNeverNegative() throws InterruptedException {
        log.info("TEST 2 STARTED | 5 concurrent reservations of 3 units; only 10 available");
        StockResponse beforeTest = inventoryService.getStock(productId);
        logStock("TEST 2 INITIAL STOCK", beforeTest);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        ReserveStockRequest request = new ReserveStockRequest();
        request.setQuantity(3); // 5 threads × 3 units = 15 requested, only 10 available

        for (int i = 0; i < threadCount; i++) {
            int taskNumber = i + 1;
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                log.info("TEST 2 | task={} | thread={} | waiting at start gate", taskNumber, threadName);
                try {
                    startLatch.await();
                    log.info("TEST 2 | task={} | thread={} | calling reserveStock(quantity=3)", taskNumber, threadName);

                    StockResponse result = inventoryService.reserveStock(productId, request);
                    log.info("TEST 2 | task={} | SUCCESS | available={} | reserved={}",
                            taskNumber, result.getAvailableQuantity(), result.getReservedQuantity());
                } catch (Exception e) {
                    exceptions.add(e);
                    log.warn("TEST 2 | task={} | FAILED | exception={} | message={}",
                            taskNumber, e.getClass().getSimpleName(), e.getMessage());
                } finally {
                    doneLatch.countDown();
                    log.info("TEST 2 | task={} | finished | remainingTasks={}", taskNumber, doneLatch.getCount());
                }
            });
        }

        log.info("TEST 2 | releasing all {} threads", threadCount);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        log.info("TEST 2 | all threads completed within timeout={}", completed);
        assertThat(completed).as("All concurrent tasks should finish within 10 seconds").isTrue();

        StockResponse finalStock = inventoryService.getStock(productId);
        logStock("TEST 2 FINAL STOCK", finalStock);
        log.info("TEST 2 SUMMARY | failures={} | requestedTotal={} | initialAvailable=10",
                exceptions.size(), threadCount * request.getQuantity());

        // The most important assertion in this entire test class:
        // available stock must NEVER be negative regardless of concurrency
        assertThat(finalStock.getAvailableQuantity()).isGreaterThanOrEqualTo(0);

        // Some requests must have failed (we requested more than available)
        assertThat(exceptions).isNotEmpty();

        // All failures must be either InsufficientStock or optimistic lock conflicts
        // No unexpected exceptions (NullPointerException, etc.)
        for (Exception e : exceptions) {
            assertThat(e).isInstanceOfAny(
                    InsufficientStockException.class,
                    org.springframework.orm.ObjectOptimisticLockingFailureException.class
            );
        }
        log.info("TEST 2 PASSED | stock never became negative");
    }

    // -------------------------------------------------------------------------
    // TEST 3: Full stock lifecycle — sequential correctness
    // -------------------------------------------------------------------------

    /*
     * SCENARIO (sequential, not concurrent):
     * Verifies the complete reserve → release → confirm lifecycle
     * produces correct stock numbers at each step.
     *
     * This is not a concurrency test — it verifies business logic correctness
     * before we stress it with concurrent threads.
     *
     * Starting state: available=10, reserved=0
     */
    @Test
    @DisplayName("Full stock lifecycle: add → reserve → release → reserve → confirm")
    void stockLifecycle_correctAtEachStep() {
        log.info("TEST 3 STARTED | sequential stock lifecycle");
        logStock("TEST 3 INITIAL STOCK", inventoryService.getStock(productId));

        ReserveStockRequest threeUnits = new ReserveStockRequest();
        threeUnits.setQuantity(3);

        // Reserve 3
        log.info("TEST 3 | STEP 1 | reserve 3 units");
        StockResponse afterReserve = inventoryService.reserveStock(productId, threeUnits);
        logStock("TEST 3 AFTER RESERVE", afterReserve);
        assertThat(afterReserve.getAvailableQuantity()).isEqualTo(7);
        assertThat(afterReserve.getReservedQuantity()).isEqualTo(3);

        // Release 3 (order cancelled)
        log.info("TEST 3 | STEP 2 | release 3 reserved units");
        StockResponse afterRelease = inventoryService.releaseStock(productId, threeUnits);
        logStock("TEST 3 AFTER RELEASE", afterRelease);
        assertThat(afterRelease.getAvailableQuantity()).isEqualTo(10);
        assertThat(afterRelease.getReservedQuantity()).isEqualTo(0);

        // Reserve 3 again (new order)
        log.info("TEST 3 | STEP 3 | reserve 3 units again");
        StockResponse secondReserve = inventoryService.reserveStock(productId, threeUnits);
        logStock("TEST 3 AFTER SECOND RESERVE", secondReserve);

        // Confirm 3 (order fulfilled — stock permanently gone)
        log.info("TEST 3 | STEP 4 | confirm 3 reserved units");
        StockResponse afterConfirm = inventoryService.confirmStock(productId, threeUnits);
        logStock("TEST 3 AFTER CONFIRM", afterConfirm);
        assertThat(afterConfirm.getAvailableQuantity()).isEqualTo(7);
        assertThat(afterConfirm.getReservedQuantity()).isEqualTo(0);
        log.info("TEST 3 PASSED | full stock lifecycle is correct");
    }

    private void logStock(String stage, StockResponse stock) {
        log.info("{} | productId={} | available={} | reserved={}",
                stage, productId, stock.getAvailableQuantity(), stock.getReservedQuantity());
    }
}