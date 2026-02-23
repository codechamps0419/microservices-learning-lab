package com.example.product.dto;

import com.example.product.domain.Product;
import com.example.product.domain.ProductStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * DTO returned from all product endpoints.
 *
 * This is the only product representation that ever leaves the service.
 * The Product entity is never serialised directly.
 *
 * FIELDS INCLUDED:
 * All fields the API consumer legitimately needs.
 *
 * availableStock is null here and will be populated in Phase 5
 * when product-service calls inventory-service via RestClient.
 * Declaring it now avoids a breaking API change later.
 *
 * WHY @Setter HERE?
 * ProductResponse is built by the service layer field by field,
 * or via the static factory method below. @Setter supports both patterns.
 *
 * STATIC FACTORY METHOD from(Product):
 * Keeps the mapping logic in one place.
 * The service layer calls ProductResponse.from(product) — clean and readable.
 * If the entity changes, only this method needs updating.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /*
     * Populated in Phase 5 by InventoryClient.
     * null means stock data was not requested or inventory-service
     * was unavailable. The API consumer must handle null gracefully.
     */
    private Integer availableStock;

    /*
     * Maps a Product entity to a ProductResponse DTO.
     *
     * Static factory pattern — preferred over a constructor with many
     * parameters because the method name "from" makes the intent clear.
     *
     * This method is the single source of truth for entity-to-DTO mapping.
     * We deliberately avoid mapping libraries (MapStruct, ModelMapper) here
     * to keep the code explicit and easy to follow while learning.
     */
    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setCategory(product.getCategory());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}
