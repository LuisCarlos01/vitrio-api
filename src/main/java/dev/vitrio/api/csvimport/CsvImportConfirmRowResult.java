package dev.vitrio.api.csvimport;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de uma linha na confirmação de importação (spec 006, US2) — mesmos valores
 * normalizados de {@link CsvImportRowResult}, mais {@code productId} quando o {@code Product} foi
 * criado (linha estruturalmente válida e cuja imagem foi baixada e enviada com sucesso).
 * {@code errors} vazio e {@code productId} não nulo significa sucesso; um {@code productId} nulo
 * sempre vem acompanhado de ao menos um erro (falha estrutural ou de download/upload de imagem).
 * Registro deliberadamente separado de {@code CsvImportRowResult} (usado só pela prévia) — a
 * prévia nunca tem {@code productId}, e não faz sentido esse campo existir lá sempre nulo.
 */
public record CsvImportConfirmRowResult(
        int lineNumber, String name, String sku, String description, String imageUrl, UUID productId, List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }

    // Carrega os 5 valores normalizados de uma linha da prévia (CsvImportRowResult) sem repeti-los
    // a cada retorno de CsvImportService.confirmRow — dependência num único sentido (confirmação
    // depende da prévia), a prévia continua sem conhecer este tipo.
    public static CsvImportConfirmRowResult from(CsvImportRowResult row, UUID productId, List<String> errors) {
        return new CsvImportConfirmRowResult(
                row.lineNumber(), row.name(), row.sku(), row.description(), row.imageUrl(), productId, errors);
    }
}
