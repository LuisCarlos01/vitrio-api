package dev.vitrio.api.csvimport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro agregado de uma confirmação de importação CSV (spec 009) — {@code confirmedAt} é o
 * instante em que o processamento de todas as linhas terminou, não o da requisição HTTP. Sem
 * referência às linhas/produtos individuais, deliberadamente (ver "Fora de escopo" da spec):
 * puro dado de auditoria pro card "última importação" do dashboard.
 */
@Entity
@Table(name = "csv_import_logs")
public class CsvImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "catalog_id", nullable = false, updatable = false)
    private UUID catalogId;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "accepted_count", nullable = false, updatable = false)
    private int acceptedCount;

    @Column(name = "rejected_count", nullable = false, updatable = false)
    private int rejectedCount;

    protected CsvImportLog() {
        // Construtor exigido pelo JPA.
    }

    public CsvImportLog(UUID catalogId, Instant confirmedAt, int acceptedCount, int rejectedCount) {
        this.catalogId = catalogId;
        this.confirmedAt = confirmedAt;
        this.acceptedCount = acceptedCount;
        this.rejectedCount = rejectedCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatalogId() {
        return catalogId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }
}
