package com.example.order.repository;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/*
 * Spring Data JPA repository for Order.
 *
 * JpaRepository<Order, UUID>:
 * - First type  = the entity this repository manages
 * - Second type = the type of the primary key
 *
 * Spring Data generates the full implementation at startup.
 * save(), findById(), findAll(), deleteById() are available for free.
 *
 * BOUNDARY RULE:
 * This repository only accesses order_db.
 * It never touches product_db or inventory_db.
 * order-service is the only service that uses this repository.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /*
     * Finds all orders for a given product.
     * Useful for order history queries scoped to a product.
     *
     * Spring Data derives:
     * SELECT * FROM orders WHERE product_id = ?
     */
    List<Order> findByProductId(UUID productId);

    /*
     * Finds all orders with a given status.
     * Useful for admin queries (e.g. all PENDING orders).
     *
     * Spring Data derives:
     * SELECT * FROM orders WHERE status = ?
     */
    List<Order> findByStatus(OrderStatus status);
}
