package dev.vitrio.api.csvimport;

import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura do histórico de importação (spec 009) — controller separado de
 * {@link CsvImportController} porque o path base diverge ({@code /imports}, não
 * {@code /products/import}) e a escrita (confirmação) já mora em {@link CsvImportService}.
 */
@RestController
@RequestMapping("/api/v1/catalogs/{catalogId}/imports")
public class CsvImportLogController {

    private final CatalogRepository catalogRepository;
    private final CsvImportLogRepository csvImportLogRepository;

    public CsvImportLogController(CatalogRepository catalogRepository, CsvImportLogRepository csvImportLogRepository) {
        this.catalogRepository = catalogRepository;
        this.csvImportLogRepository = csvImportLogRepository;
    }

    // 204 (não 404) quando o catálogo é meu mas nunca importou — "nunca importou" é um estado
    // válido, não um erro (spec 009, US2 cenário 2). 404 genérico continua reservado a catálogo
    // inexistente/de outra revendedora (ADR-0003).
    @GetMapping("/latest")
    public ResponseEntity<CsvImportLogResponse> latest(@PathVariable UUID catalogId, Authentication authentication) {
        UUID ownerId = UUID.fromString(authentication.getName());
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);

        return csvImportLogRepository
                .findFirstByCatalogIdOrderByConfirmedAtDesc(catalogId)
                .map(log -> ResponseEntity.ok(CsvImportLogResponse.from(log)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
