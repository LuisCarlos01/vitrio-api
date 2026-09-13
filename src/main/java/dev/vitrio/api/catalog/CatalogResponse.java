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
        String logoUrl,
        Instant createdAt) {

    // logoUrl (nunca logoAssetId bruto) é resolvido fora daqui, não a partir do próprio Catalog —
    // diferente de Product/ProductResponse (que expõe imageAssetId bruto), porque o dashboard
    // precisa reexibir o logo atual sem outro endpoint pra resolver asset->URL (spec 007).
    static CatalogResponse from(Catalog catalog, String logoUrl) {
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
                logoUrl,
                catalog.getCreatedAt());
    }
}
