package com.example.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/*
 * DTO for PATCH /products/{id}/price — updates price only.
 *
 * WHY A DEDICATED DTO FOR PRICE?
 * Price changes are a distinct business operation.
 * In a real system, a price change might trigger:
 * - An audit log entry
 * - A notification to subscribed customers
 * - A price history record
 *
 * Having a dedicated endpoint and DTO makes it easy to add
 * that logic later without touching the general update flow.
 * This is the Single Responsibility Principle applied to APIs.
 */
@Getter
@Setter
@NoArgsConstructor
public class PriceUpdateRequest {

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price format is invalid: max 10 integer digits and 2 decimal places")
    private BigDecimal price;
}
