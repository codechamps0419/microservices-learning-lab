package com.example.product.repository;

import com.example.product.domain.Product;
import com.example.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/*
 * Spring Data JPA repository for Product.
 *
 * JpaRepository<Product, UUID>:
 * - First type  = the entity class this repository manages
 * - Second type = the type of the primary key (UUID)
 *
 * Spring Data generates the full implementation at startup.
 * We get save(), findById(), findAll(), deleteById() etc. for free.
 * No @Repository annotation needed — Spring Data detects it automatically
 * because this interface extends JpaRepository.
 *
 * INTERVIEW QUESTION:
 * What is the difference between CrudRepository, JpaRepository,
 * and PagingAndSortingRepository?
 *
 * CrudRepository       — basic CRUD only (save, findById, delete)
 * PagingAndSortingRepository — adds findAll(Pageable) and findAll(Sort)
 * JpaRepository        — extends both above + adds flush(), saveAndFlush(),
 *                        deleteAllInBatch() and other JPA-specific methods
 *
 * Use JpaRepository unless you have a reason to restrict the API surface.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /*
     * Spring Data derives the SQL from the method name automatically.
     * existsByNameIgnoreCase → SELECT COUNT(*) > 0 FROM products WHERE LOWER(name) = LOWER(?)
     *
     * Used by the service layer to enforce unique product names
     * before attempting an INSERT or UPDATE.
     */
    boolean existsByNameIgnoreCase(String name);

    /*
     * Searches products by optional category and/or name fragment,
     * filtered by status.
     *
     * JPQL (Java Persistence Query Language) — NOT SQL.
     * JPQL operates on entity class names and field names, not table/column names.
     * Hibernate translates this to the correct SQL dialect at runtime.
     *
     * LOWER(...) LIKE LOWER(...) — case-insensitive name search.
     * CONCAT('%', :name, '%')    — wraps the search term with wildcards.
     *
     * NULL HANDLING:
     * (:category IS NULL OR ...) — if the caller passes null for category,
     * the entire category condition evaluates to true and is effectively skipped.
     * Same logic applies to :name.
     * This single query replaces four separate findBy... methods.
     *
     * @Param("category") — binds the method parameter to the named
     * parameter :category in the JPQL query. Required when using @Query
     * with named parameters.
     *
     * PRODUCTION NOTE:
     * For large datasets, this query should be paired with pagination
     * (Pageable). We keep it simple here to focus on the boundary concept.
     */
    @Query(value = """
            SELECT * FROM products
            WHERE (:category IS NULL OR LOWER(category) = LOWER(CAST(:category AS text)))
              AND (:name IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', CAST(:name AS text), '%')))
              AND status = :#{#status.name()}
            """, nativeQuery = true)
    List<Product> searchProducts(
            @Param("category") String category,
            @Param("name") String name,
            @Param("status") ProductStatus status
    );
}
