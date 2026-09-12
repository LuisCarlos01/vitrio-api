/**
 * Vitrine pública do Vitrio (spec 005): leitura anônima, sem autenticação, do catálogo
 * identificado por {@code slug} — consumida pelo {@code Customer} (CONTEXT.md), nunca pela
 * {@code Reseller} autenticada (esse é o papel de {@code catalog}/{@code product}).
 */
package dev.vitrio.api.publiccatalog;
