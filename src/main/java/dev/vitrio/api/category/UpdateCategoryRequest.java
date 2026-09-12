package dev.vitrio.api.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PATCH .../categories/{id}} — só renomeia (spec 004, US1). */
public record UpdateCategoryRequest(@NotBlank @Size(max = 255) String name) {}
