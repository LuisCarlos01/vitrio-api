package dev.vitrio.api.csvimport;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvImportLogRepository extends JpaRepository<CsvImportLog, UUID> {

    // "Última importação" (spec 009, US2) — índice composto (catalog_id, confirmed_at DESC) na
    // migration cobre exatamente esta consulta.
    Optional<CsvImportLog> findFirstByCatalogIdOrderByConfirmedAtDesc(UUID catalogId);
}
