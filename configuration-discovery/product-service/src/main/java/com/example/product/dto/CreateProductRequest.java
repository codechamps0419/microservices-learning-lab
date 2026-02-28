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
 * DTO for POST /products request body.
 *
 * WHY A SEPARATE DTO INSTEAD OF ACCEPTING THE Product ENTITY DIRECTLY?
 *
 * The Product entity has fields the client must NOT control:
 * id, status, createdAt, updatedAt.
 * If we accepted the entity directly, a malicious client could send:
 * { "id": "some-uuid", "status": "INACTIVE", "createdAt": "2020-01-01" }
 * and overwrite internal fields.
 *
 * DTOs give us:
 * 1. Control over exactly what the client can provide
 * 2. Validation rules specific to the create operation
 * 3. Decoupling between the API contract and the internal domain model
 *    (you can change the entity without breaking the API and vice versa)
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateProductRequest {

    /*
     * @NotBlank — rejects three cases:
     * - null
     * - empty string ""
     * - whitespace-only string "   "
     *
     * Stronger than @NotNull for String fields.
     * A product with name "   " is just as invalid as one with name null.
     *
     * message — the error message returned in the 400 response body.
     * Always provide a meaningful message so the API consumer knows
     * exactly what they did wrong.
     */
    @NotBlank(message = "Product name is required")
    private String name;

    /*
     * No validation constraint — description is optional.
     * A product can be created without a description.
     */
    private String description;

    /*
     * @NotNull   — price must be present in the JSON body.
     *              Note: @NotBlank does not apply to BigDecimal, only String.
     *
     * @DecimalMin("0.01") — price must be at least 0.01.
     *              A price of 0.00 makes no business sense for a product.
     *              inclusive = true (default) means 0.01 itself is valid.
     *
     * @Digits(integer = 10, fraction = 2)
     *              — enforces the numeric format explicitly.
     *              Prevents a client from sending 1.999 which would
     *              silently round to 2.00 in the database.
     *              integer  = max digits before the decimal point
     *              fraction = max digits after the decimal point
     */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price format is invalid: max 10 integer digits and 2 decimal places")
    private BigDecimal price;

    /*
     * Optional — no validation constraint.
     * Category is a free-form string. The service layer trims and normalises it.
     */
    private String category;
}
