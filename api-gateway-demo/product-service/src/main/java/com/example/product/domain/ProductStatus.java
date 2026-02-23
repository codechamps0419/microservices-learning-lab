package com.example.product.domain;

/*
 * Represents the lifecycle state of a product.
 *
 * Stored as a STRING in the database via @Enumerated(EnumType.STRING)
 * on the entity field.
 *
 * WHY STRING AND NOT ORDINAL?
 * EnumType.ORDINAL stores the position of the enum value (0, 1, 2...).
 * If you ever add a new value between existing ones or reorder them,
 * every existing row in the database silently maps to the wrong value.
 * EnumType.STRING stores "ACTIVE" / "INACTIVE" — safe to reorder,
 * safe to add new values, and readable directly in the database.
 *
 * INTERVIEW QUESTION:
 * Why should you never use EnumType.ORDINAL in production?
 * Because ordinal is fragile. The moment a developer adds a new enum
 * value in the middle of the list, all existing data is corrupted
 * with no compile-time or runtime warning.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
