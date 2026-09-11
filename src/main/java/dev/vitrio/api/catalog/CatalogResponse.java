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

    // Placeholders neutros de marca (spec 002, US1, cenário 2) — nunca expõe null pro cliente
    // antes da revendedora customizar. Trocar quando o design visual definitivo existir.
    private static final String DEFAULT_PRIMARY_COLOR_HEX = "#6D28D9";
    private static final String DEFAULT_BUTTON_COLOR_HEX = "#059669";

    static CatalogResponse from(Catalog catalog) {
        return new CatalogResponse(
                catalog.getId(),
                catalog.getName(),
                catalog.getSlug(),
                catalog.getPrimaryColorHex() != null ? catalog.getPrimaryColorHex() : DEFAULT_PRIMARY_COLOR_HEX,
                catalog.getButtonColorHex() != null ? catalog.getButtonColorHex() : DEFAULT_BUTTON_COLOR_HEX,
                catalog.getInstagramHandle(),
                catalog.getWhatsappNumber(),
                catalog.getWhatsappVerificationStatus(),
                catalog.getWhatsappVerifiedAt(),
                catalog.getCreatedAt());
    }
}
