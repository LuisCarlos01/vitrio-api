package dev.vitrio.api.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, UUID catalogId, String name, Instant createdAt) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(), category.getCatalogId(), category.getName(), category.getCreatedAt());
    }
}
