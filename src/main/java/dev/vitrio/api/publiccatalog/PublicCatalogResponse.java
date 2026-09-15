package dev.vitrio.api.publiccatalog;

import dev.vitrio.api.catalog.Catalog;
import dev.vitrio.api.catalog.CatalogColorDefaults;
import dev.vitrio.api.catalog.WhatsappVerificationStatus;
import java.util.List;

public record PublicCatalogResponse(
        String name,
        String primaryColorHex,
        String buttonColorHex,
        boolean hasCustomColor,
        String instagramHandle,
        String whatsappNumber,
        String logoUrl,
        List<PublicCategoryResponse> categories,
        List<PublicProductResponse> products) {

    // logoUrl já vem resolvido de quem chama (mesmo padrão de PublicProductResponse.imageUrl,
    // spec 007 US2) — nunca o logoAssetId bruto, e null quando o catálogo nunca definiu logo.
    static PublicCatalogResponse from(
            Catalog catalog,
            String logoUrl,
            List<PublicCategoryResponse> categories,
            List<PublicProductResponse> products) {
        return new PublicCatalogResponse(
                catalog.getName(),
                CatalogColorDefaults.resolvePrimary(catalog),
                CatalogColorDefaults.resolveButton(catalog),
                CatalogColorDefaults.hasCustomColor(catalog),
                catalog.getInstagramHandle(),
                // Número não verificado nunca é anunciado publicamente (spec 005, US1 cenário 5).
                catalog.getWhatsappVerificationStatus() == WhatsappVerificationStatus.VERIFIED
                        ? catalog.getWhatsappNumber()
                        : null,
                logoUrl,
                categories,
                products);
    }
}
