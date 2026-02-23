package com.example.product.service;

import com.example.product.domain.Product;
import com.example.product.domain.ProductStatus;
import com.example.product.dto.*;
import com.example.product.exception.DuplicateProductNameException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/*
 * Business logic layer for product operations.
 *
 * @Service — marks this as a Spring-managed bean.
 * Spring registers it in the application context so it can be
 * injected into ProductController via @RequiredArgsConstructor.
 *
 * @RequiredArgsConstructor (Lombok) — generates a constructor for all
 * final fields. Spring uses this constructor to inject dependencies.
 * This is constructor injection — the recommended approach over @Autowired.
 *
 * WHY CONSTRUCTOR INJECTION OVER @Autowired ON FIELDS?
 * 1. Dependencies are explicit and visible
 * 2. The class can be instantiated in unit tests without a Spring context
 *    — just call new ProductService(mockRepository)
 * 3. final fields guarantee the dependency is never null and never replaced
 *
 * TRANSACTION STRATEGY:
 * @Transactional(readOnly = true) on the class — all methods default to
 * read-only transactions. This is a performance hint to the database
 * (no dirty checking, no flush) and prevents accidental writes in
 * methods that should only read.
 *
 * Write methods override with @Transactional (readOnly = false by default).
 *
 * INTERVIEW QUESTION:
 * What does @Transactional(readOnly = true) actually do?
 * - Tells Hibernate to skip dirty checking at the end of the transaction
 *   (no need to compare entity state to detect changes)
 * - Tells the JDBC driver / connection pool it can use a read replica
 *   if one is configured
 * - Does NOT prevent you from calling save() — it just won't flush changes
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    /*
     * @Transactional — overrides the class-level readOnly = true.
     * This method writes to the DB so it needs a full read-write transaction.
     *
     * FLOW:
     * 1. Check for duplicate name — throw 409 if exists
     * 2. Map request DTO to entity
     * 3. Save entity — Hibernate executes INSERT, @PrePersist sets timestamps
     * 4. Map saved entity to response DTO and return
     *
     * WHY CHECK DUPLICATE BEFORE SAVE?
     * We could rely on a UNIQUE constraint in the DB and catch the
     * DataIntegrityViolationException. That works but gives a poor error
     * message. An explicit check gives a clear, business-meaningful 409.
     *
     * PRODUCTION NOTE:
     * There is a race condition: two concurrent requests could both pass
     * the duplicate check and both attempt to insert. A DB UNIQUE constraint
     * on the name column would catch this. We keep it simple here since
     * the focus is on service boundaries, not concurrency hardening.
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateProductNameException(request.getName());
        }

        Product product = new Product();
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory() != null ? request.getCategory().trim() : null);

        return ProductResponse.from(productRepository.save(product));
    }

    // -------------------------------------------------------------------------
    // READ — single product
    // -------------------------------------------------------------------------

    /*
     * Inherits readOnly = true from the class-level @Transactional.
     *
     * findById returns Optional<Product>.
     * orElseThrow unwraps the value or throws ProductNotFoundException.
     * GlobalExceptionHandler catches it and returns 404.
     *
     * NOTE: availableStock in ProductResponse is null here.
     * It will be populated in Phase 5 when we add InventoryClient.
     */
    public ProductResponse getProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    // -------------------------------------------------------------------------
    // READ — search and filter
    // -------------------------------------------------------------------------

    /*
     * Delegates to the JPQL query in ProductRepository.
     * Only ACTIVE products are returned by default — inactive products
     * are hidden from the search results.
     *
     * Both category and name are optional. Passing null skips that filter.
     * The JPQL query handles null parameters gracefully via IS NULL checks.
     */
    public List<ProductResponse> searchProducts(String category, String name) {
        return productRepository.searchProducts(category, name, ProductStatus.ACTIVE)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    // -------------------------------------------------------------------------
    // UPDATE — full replacement
    // -------------------------------------------------------------------------

    /*
     * PUT semantics: replace all updatable fields with the values from the request.
     * Fields not in UpdateProductRequest (id, status, createdAt, updatedAt)
     * are untouched.
     *
     * FLOW:
     * 1. Load existing product — throw 404 if not found
     * 2. Check if the new name conflicts with a DIFFERENT product
     * 3. Apply all field updates
     * 4. Save — Hibernate executes UPDATE, @PreUpdate refreshes updatedAt
     * 5. Return updated DTO
     *
     * WHY NOT JUST SAVE A NEW ENTITY WITH THE SAME ID?
     * Creating a new Product() and setting the ID would work for the UPDATE,
     * but it would lose the createdAt value since @PrePersist would not run
     * (we are calling save on a detached entity, not a new one).
     * Loading first and mutating is the correct JPA pattern.
     */
    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        boolean nameChanged = !product.getName().equalsIgnoreCase(request.getName());
        if (nameChanged && productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateProductNameException(request.getName());
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory() != null ? request.getCategory().trim() : null);

        return ProductResponse.from(productRepository.save(product));
    }

    // -------------------------------------------------------------------------
    // UPDATE — price only
    // -------------------------------------------------------------------------

    @Transactional
    public ProductResponse updatePrice(UUID id, PriceUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setPrice(request.getPrice());

        return ProductResponse.from(productRepository.save(product));
    }

    // -------------------------------------------------------------------------
    // UPDATE — status only
    // -------------------------------------------------------------------------

    /*
     * Activates or deactivates a product.
     *
     * This is intentionally a separate method from updateProduct.
     * Status changes are a distinct business operation — in a real system
     * they might require an approval workflow, audit log, or notification.
     * Keeping them separate makes that extension straightforward.
     */
    @Transactional
    public ProductResponse updateStatus(UUID id, StatusUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setStatus(request.getStatus());

        return ProductResponse.from(productRepository.save(product));
    }
}
