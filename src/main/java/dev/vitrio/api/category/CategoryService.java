package dev.vitrio.api.category;

import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CatalogRepository catalogRepository;
    private final CategoryRepository categoryRepository;

    public CategoryService(CatalogRepository catalogRepository, CategoryRepository categoryRepository) {
        this.catalogRepository = catalogRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryResponse create(UUID ownerId, UUID catalogId, CreateCategoryRequest request) {
        requireOwnedCatalog(ownerId, catalogId);
        Category category = new Category(catalogId, request.name());
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listByCatalog(UUID ownerId, UUID catalogId) {
        requireOwnedCatalog(ownerId, catalogId);
        return categoryRepository.findByCatalogIdOrderByCreatedAtDesc(catalogId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse rename(UUID ownerId, UUID catalogId, UUID id, UpdateCategoryRequest request) {
        requireOwnedCatalog(ownerId, catalogId);
        Category category = findInCatalogOrThrow(catalogId, id);
        category.rename(request.name());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID ownerId, UUID catalogId, UUID id) {
        requireOwnedCatalog(ownerId, catalogId);
        Category category = findInCatalogOrThrow(catalogId, id);
        // Produtos que referenciam esta categoria são desvinculados (categoryId = null) via
        // ON DELETE SET NULL na FK de products.category_id (spec 004) — nunca excluídos.
        categoryRepository.delete(category);
    }

    // Isolamento (ADR-0003): "catálogo não existe" e "catálogo não é meu" viram a mesma
    // exceção — 404 genérico, nunca 403, mesmo padrão de CatalogService.
    private void requireOwnedCatalog(UUID ownerId, UUID catalogId) {
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);
    }

    // Uma categoria só existe no contexto do seu próprio catálogo (spec 004, US1 cenário 5) —
    // acessá-la por um catalogId diferente do dela é indistinguível de "não existe".
    private Category findInCatalogOrThrow(UUID catalogId, UUID id) {
        return categoryRepository.findByIdAndCatalogId(id, catalogId).orElseThrow(CategoryNotFoundException::new);
    }
}
