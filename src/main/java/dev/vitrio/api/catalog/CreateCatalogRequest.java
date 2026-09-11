package dev.vitrio.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/v1/catalogs}. O slug nunca é informado — é sempre derivado do nome. */
public record CreateCatalogRequest(@NotBlank @Size(max = 255) String name) {}
