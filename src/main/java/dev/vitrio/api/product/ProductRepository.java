package dev.vitrio.api.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByCatalogIdOrderByCreatedAtDesc(UUID catalogId);

    // Isolamento (ADR-0003): "não existe" e "existe mas é de outro catálogo" viram a mesma
    // exceção — mesmo padrão de CategoryRepository/AssetRepository.
    Optional<Product> findByIdAndCatalogId(UUID id, UUID catalogId);

    long countByCatalogId(UUID catalogId);

    boolean existsByCatalogIdAndSku(UUID catalogId, String sku);

    // Usado no PATCH (US3): um produto não colide consigo mesmo ao manter o próprio sku.
    boolean existsByCatalogIdAndSkuAndIdNot(UUID catalogId, String sku, UUID id);
}
