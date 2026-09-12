package dev.vitrio.api.publiccatalog;

import dev.vitrio.api.category.Category;
import java.util.UUID;

public record PublicCategoryResponse(UUID id, String name) {

    static PublicCategoryResponse from(Category category) {
        return new PublicCategoryResponse(category.getId(), category.getName());
    }
}
