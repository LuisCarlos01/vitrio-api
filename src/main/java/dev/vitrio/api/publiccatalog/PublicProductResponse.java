package dev.vitrio.api.publiccatalog;

import dev.vitrio.api.product.Product;
import java.util.UUID;

/**
 * Nunca expõe {@code imageAssetId} bruto — só a {@code imageUrl} final (spec 005, "Regras de
 * negócio"). {@code quantityAvailable}/{@code isOrderable} continuam presentes mesmo quando o
 * produto não pode ser pedido: ele é consultável, só não entra no carrinho (`ideia.md`).
 */
public record PublicProductResponse(
        UUID id,
        String name,
        String sku,
        String description,
        String imageUrl,
        UUID categoryId,
        int quantityAvailable,
        boolean isOrderable) {

    static PublicProductResponse from(Product product, String imageUrl) {
        return new PublicProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                imageUrl,
                product.getCategoryId(),
                product.getQuantityAvailable(),
                product.isOrderable());
    }
}
