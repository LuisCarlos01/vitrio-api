package dev.vitrio.api.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de {@code PATCH .../products/{id}}. Todo campo é opcional — {@code null} significa "não
 * alterar", nunca "limpar o valor" (mesmo padrão de {@code UpdateCatalogRequest}, spec 002).
 */
public record UpdateProductRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String sku,
        @Size(max = 2000) String description,
        UUID imageAssetId,
        UUID categoryId,
        @Min(0) Integer quantityAvailable,
        Boolean isVisible,
        Boolean isOrderable,
        Boolean isActive) {}
