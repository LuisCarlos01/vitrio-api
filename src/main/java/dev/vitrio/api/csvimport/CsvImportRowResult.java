package dev.vitrio.api.csvimport;

import java.util.List;

/**
 * Resultado da validação de uma linha do CSV (spec 006) — {@code name}/{@code sku}/
 * {@code description}/{@code imageUrl} já normalizados (trim aplicado). {@code errors} vazio
 * significa linha válida; uma linha pode acumular mais de um motivo (ex.: nome vazio e SKU
 * duplicado ao mesmo tempo).
 */
public record CsvImportRowResult(
        int lineNumber, String name, String sku, String description, String imageUrl, List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }
}
