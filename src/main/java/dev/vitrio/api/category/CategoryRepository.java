package dev.vitrio.api.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByCatalogIdOrderByCreatedAtDesc(UUID catalogId);

    // Isolamento (ADR-0003): uma categoria só existe no contexto do seu próprio catálogo — a
    // mesma query cobre "não existe" e "existe mas é de outro catálogo".
    Optional<Category> findByIdAndCatalogId(UUID id, UUID catalogId);
}
