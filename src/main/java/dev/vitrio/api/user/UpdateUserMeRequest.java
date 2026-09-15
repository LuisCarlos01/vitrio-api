package dev.vitrio.api.user;

import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /api/v1/users/me}. {@code null} significa "não alterar" (spec 008, US2
 * cenário 2, mesma semântica de {@code PATCH /api/v1/catalogs/{id}}, spec 002) — mas uma string
 * vazia enviada explicitamente é validação inválida (cenário 3), nunca "limpar o nome".
 */
public record UpdateUserMeRequest(@Size(min = 1, max = 255) String name) {}
