package dev.vitrio.api.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<Catalog, UUID> {

    boolean existsBySlug(String slug);

    List<Catalog> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    // Isolamento entre revendedoras (ADR-0003): a query já filtra por owner, então "não existe"
    // e "existe mas não é meu" são indistinguíveis pelo chamador — a base do 404 genérico da US4.
    Optional<Catalog> findByIdAndOwnerId(UUID id, UUID ownerId);
}
