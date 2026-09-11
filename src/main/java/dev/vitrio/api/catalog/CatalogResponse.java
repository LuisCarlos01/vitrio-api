package dev.vitrio.api.catalog;

import java.time.Instant;
import java.util.UUID;

public record CatalogResponse(UUID id, String name, String slug, Instant createdAt) {

    static CatalogResponse from(Catalog catalog) {
        return new CatalogResponse(catalog.getId(), catalog.getName(), catalog.getSlug(), catalog.getCreatedAt());
    }
}
