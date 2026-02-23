package com.example.product.dto;

import com.example.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * DTO for PATCH /products/{id}/status — activates or deactivates a product.
 *
 * WHY NOT A BOOLEAN active FIELD?
 * Using the ProductStatus enum instead of a boolean future-proofs the API.
 * If a third state is needed later (e.g. DISCONTINUED, PENDING_REVIEW),
 * we add it to the enum without changing the endpoint or the DTO structure.
 * A boolean can only ever represent two states.
 *
 * @NotNull — the client must explicitly send a status value.
 * Jackson deserialises unknown enum values as null by default,
 * so @NotNull catches both missing and invalid status values.
 *
 * PRODUCTION NOTE:
 * To return a clear error for invalid enum values (e.g. "DELETED"),
 * configure Jackson with DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
 * or handle HttpMessageNotReadableException in GlobalExceptionHandler.
 * We will do this in Phase 2.
 */
@Getter
@Setter
@NoArgsConstructor
public class StatusUpdateRequest {

    @NotNull(message = "Status is required. Accepted values: ACTIVE, INACTIVE")
    private ProductStatus status;
}
