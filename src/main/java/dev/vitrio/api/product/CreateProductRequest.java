package dev.vitrio.api.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de {@code POST .../products}. {@code quantityAvailable}/{@code isVisible}/
 * {@code isOrderable}/{@code isActive} não são informados aqui — nascem sempre nos defaults da
 * spec 004 (US2); editáveis depois via {@code PATCH}.
 */
public record CreateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String sku,
        @Size(max = 2000) String description,
        @NotNull UUID imageAssetId,
        UUID categoryId) {}
