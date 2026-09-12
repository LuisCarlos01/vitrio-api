package dev.vitrio.api.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    // Isolamento (ADR-0003): usado por Product (spec 004) pra validar que um imageAssetId
    // referencia um Asset do mesmo catálogo, nunca de outro.
    Optional<Asset> findByIdAndCatalogId(UUID id, UUID catalogId);
}
