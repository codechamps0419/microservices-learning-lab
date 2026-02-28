package com.example.order.domain;

/*
 * Represents the lifecycle state of an order.
 *
 * Stored as a STRING in the database via @Enumerated(EnumType.STRING).
 *
 * WHY STRING AND NOT ORDINAL?
 * EnumType.ORDINAL stores the position (0, 1, 2...).
 * If a new status is inserted between existing ones, every existing row
 * silently maps to the wrong value — silent data corruption with no warning.
 * EnumType.STRING stores "PENDING", "CONFIRMED", "REJECTED" — safe to
 * reorder, safe to add new values, and readable directly in the database.
 *
 * STATUS MEANINGS:
 *
 * PENDING   — order has been received but not yet processed.
 *             In this phase, orders move directly to CONFIRMED or REJECTED
 *             because the flow is synchronous. PENDING is reserved for
 *             future async flows (e.g. payment processing).
 *
 * CONFIRMED — product was active, stock was reserved, order was saved.
 *             This is the happy-path terminal state.
 *
 * REJECTED  — order could not be fulfilled. Reasons include:
 *             product inactive, insufficient stock, or downstream failure.
 *             Stock is NOT reserved when an order is REJECTED.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
