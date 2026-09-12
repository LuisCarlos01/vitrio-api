package dev.vitrio.api.publiccatalog;

import dev.vitrio.api.catalog.Catalog;
import dev.vitrio.api.catalog.CatalogColorDefaults;
import dev.vitrio.api.catalog.WhatsappVerificationStatus;
import java.util.List;

public record PublicCatalogResponse(
        String name,
        String primaryColorHex,
        String buttonColorHex,
        String instagramHandle,
        String whatsappNumber,
        List<PublicCategoryResponse> categories,
        List<PublicProductResponse> products) {

    static PublicCatalogResponse from(
            Catalog catalog, List<PublicCategoryResponse> categories, List<PublicProductResponse> products) {
        return new PublicCatalogResponse(
                catalog.getName(),
                CatalogColorDefaults.resolvePrimary(catalog),
                CatalogColorDefaults.resolveButton(catalog),
                catalog.getInstagramHandle(),
                // Número não verificado nunca é anunciado publicamente (spec 005, US1 cenário 5).
                catalog.getWhatsappVerificationStatus() == WhatsappVerificationStatus.VERIFIED
                        ? catalog.getWhatsappNumber()
                        : null,
                categories,
                products);
    }
}
