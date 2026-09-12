package dev.vitrio.api.catalog;

import java.time.Instant;
import java.util.UUID;

public record CatalogResponse(
        UUID id,
        String name,
        String slug,
        String primaryColorHex,
        String buttonColorHex,
        String instagramHandle,
        String whatsappNumber,
        WhatsappVerificationStatus whatsappVerificationStatus,
        Instant whatsappVerifiedAt,
        Instant createdAt) {

    static CatalogResponse from(Catalog catalog) {
        return new CatalogResponse(
                catalog.getId(),
                catalog.getName(),
                catalog.getSlug(),
                CatalogColorDefaults.resolvePrimary(catalog),
                CatalogColorDefaults.resolveButton(catalog),
                catalog.getInstagramHandle(),
                catalog.getWhatsappNumber(),
                catalog.getWhatsappVerificationStatus(),
                catalog.getWhatsappVerifiedAt(),
                catalog.getCreatedAt());
    }
}
