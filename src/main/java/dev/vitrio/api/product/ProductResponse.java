package dev.vitrio.api.product;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID catalogId,
        String name,
        String sku,
        String description,
        UUID imageAssetId,
        UUID categoryId,
        int quantityAvailable,
        boolean isVisible,
        boolean isOrderable,
        boolean isActive,
        Instant createdAt) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCatalogId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getImageAssetId(),
                product.getCategoryId(),
                product.getQuantityAvailable(),
                product.isVisible(),
                product.isOrderable(),
                product.isActive(),
                product.getCreatedAt());
    }
}
