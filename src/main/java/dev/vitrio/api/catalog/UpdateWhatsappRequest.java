package dev.vitrio.api.catalog;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code PUT /api/v1/catalogs/{id}/whatsapp}. Aceita formatos brasileiros comuns
 * (com/sem {@code +55}, com símbolos) — a normalização e validação de formato acontecem no
 * service, não aqui, porque dependem do tamanho do número já sem símbolos (spec 002, US2).
 */
public record UpdateWhatsappRequest(@NotBlank String whatsappNumber) {}
