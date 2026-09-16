package dev.vitrio.api.product;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID catalogId,
        String name,
        String sku,
        String description,
        String imageUrl,
        UUID categoryId,
        int quantityAvailable,
        boolean isVisible,
        boolean isOrderable,
        boolean isActive,
        Instant createdAt) {

    // imageUrl (nunca imageAssetId bruto) é resolvido fora daqui, não a partir do próprio
    // Product — mesmo padrão de CatalogResponse.logoUrl (spec 007): o dashboard precisa
    // reexibir a foto de um produto já existente sem o publicUrl original do upload (spec 011).
    static ProductResponse from(Product product, String imageUrl) {
        return new ProductResponse(
                product.getId(),
                product.getCatalogId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                imageUrl,
                product.getCategoryId(),
                product.getQuantityAvailable(),
                product.isVisible(),
                product.isOrderable(),
                product.isActive(),
                product.getCreatedAt());
    }
}
