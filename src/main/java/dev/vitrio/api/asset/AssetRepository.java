package dev.vitrio.api.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    // Isolamento (ADR-0003): usado por Product (spec 004) pra validar que um imageAssetId
    // referencia um Asset do mesmo catálogo, nunca de outro.
    Optional<Asset> findByIdAndCatalogId(UUID id, UUID catalogId);

    // Extraído de CatalogService/PublicCatalogService (spec 007, US1/US2) — os dois resolviam o
    // logo do catálogo com o mesmo bloco byte a byte (assetId nulo -> null; senão busca isolada
    // por catalogId -> publicUrl ou null se o Asset não existir mais). "nulo" cobre tanto
    // "catálogo nunca definiu logo" quanto qualquer futuro campo opcional de asset com a mesma
    // forma — não é específico de logo apesar do nome do caso de uso.
    default String resolvePublicUrl(UUID assetId, UUID catalogId) {
        if (assetId == null) {
            return null;
        }
        return findByIdAndCatalogId(assetId, catalogId).map(Asset::getPublicUrl).orElse(null);
    }
}
