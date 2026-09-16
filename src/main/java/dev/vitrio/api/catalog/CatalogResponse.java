package dev.vitrio.api.catalog;

import java.time.Instant;
import java.util.UUID;

public record CatalogResponse(
        UUID id,
        String name,
        String slug,
        String primaryColorHex,
        String buttonColorHex,
        boolean hasCustomColor,
        String instagramHandle,
        String whatsappNumber,
        WhatsappVerificationStatus whatsappVerificationStatus,
        Instant whatsappVerifiedAt,
        String logoUrl,
        Instant createdAt) {

    // logoUrl (nunca logoAssetId bruto) é resolvido fora daqui, não a partir do próprio Catalog —
    // mesmo padrão hoje usado por ProductResponse.imageUrl (spec 011), que revogou a divergência
    // original entre os dois (spec 007) pelo mesmo motivo: reexibir a foto/logo de um recurso já
    // existente sem outro endpoint pra resolver asset->URL.
    static CatalogResponse from(Catalog catalog, String logoUrl) {
        return new CatalogResponse(
                catalog.getId(),
                catalog.getName(),
                catalog.getSlug(),
                CatalogColorDefaults.resolvePrimary(catalog),
                CatalogColorDefaults.resolveButton(catalog),
                CatalogColorDefaults.hasCustomColor(catalog),
                catalog.getInstagramHandle(),
                catalog.getWhatsappNumber(),
                catalog.getWhatsappVerificationStatus(),
                catalog.getWhatsappVerifiedAt(),
                logoUrl,
                catalog.getCreatedAt());
    }
}
