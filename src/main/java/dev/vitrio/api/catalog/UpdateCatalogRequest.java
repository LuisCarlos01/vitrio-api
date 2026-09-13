package dev.vitrio.api.catalog;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de {@code PATCH /api/v1/catalogs/{id}}. Todo campo é opcional — {@code null} significa
 * "não alterar", nunca "limpar o valor" (spec 002, US1; {@code logoAssetId} segue a mesma regra,
 * spec 007, US1).
 */
public record UpdateCatalogRequest(
        @Size(max = 255) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a #RRGGBB hex color") String primaryColorHex,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a #RRGGBB hex color") String buttonColorHex,
        @Size(max = 255) String instagramHandle,
        UUID logoAssetId) {}
