package dev.vitrio.api.csvimport;

import java.time.Instant;
import java.util.UUID;

/** Corpo de {@code GET /api/v1/catalogs/{catalogId}/imports/latest} (spec 009, US2). */
public record CsvImportLogResponse(UUID catalogId, Instant confirmedAt, int acceptedCount, int rejectedCount) {

    static CsvImportLogResponse from(CsvImportLog log) {
        return new CsvImportLogResponse(log.getCatalogId(), log.getConfirmedAt(), log.getAcceptedCount(), log.getRejectedCount());
    }
}
