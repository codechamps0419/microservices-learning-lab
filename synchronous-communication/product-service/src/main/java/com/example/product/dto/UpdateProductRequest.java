package com.example.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/*
 * DTO for PUT /products/{id} — full replacement of product information.
 *
 * PUT semantics: the client sends the complete new state of the resource.
 * All updatable fields are required.
 *
 * Fields NOT included here (and therefore not updatable via this endpoint):
 * - id         — never changes
 * - status     — managed via PATCH /products/{id}/status
 * - createdAt  — set once on creation, never changes
 * - updatedAt  — managed by @PreUpdate, not the client
 *
 * INTERVIEW QUESTION:
 * What is the difference between PUT and PATCH?
 * PUT    — full replacement. Client sends the entire resource.
 *          Missing fields are treated as null/cleared.
 * PATCH  — partial update. Client sends only the fields to change.
 *          Missing fields are left unchanged.
 * We use PATCH for price and status because they are independent
 * operations with their own business meaning.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    /*
     * Description is optional even on a full update.
     * Sending null clears the description — valid PUT behaviour.
     */
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price format is invalid: max 10 integer digits and 2 decimal places")
    private BigDecimal price;

    private String category;
}
