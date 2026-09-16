package dev.vitrio.api.product;

import dev.vitrio.api.asset.AssetRepository;
import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.category.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    // Limite do MVP (ideia.md, spec 004) — checado só na criação, nunca na edição de um
    // produto já existente.
    private static final int MAX_PRODUCTS_PER_CATALOG = 50;

    private final CatalogRepository catalogRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final ProductRepository productRepository;

    public ProductService(
            CatalogRepository catalogRepository,
            CategoryRepository categoryRepository,
            AssetRepository assetRepository,
            ProductRepository productRepository) {
        this.catalogRepository = catalogRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(UUID ownerId, UUID catalogId, CreateProductRequest request) {
        requireOwnedCatalog(ownerId, catalogId);

        if (productRepository.countByCatalogId(catalogId) >= MAX_PRODUCTS_PER_CATALOG) {
            throw new ProductLimitExceededException();
        }
        if (request.sku() != null && productRepository.existsByCatalogIdAndSku(catalogId, request.sku())) {
            throw new DuplicateSkuException();
        }
        assetRepository
                .findByIdAndCatalogId(request.imageAssetId(), catalogId)
                .orElseThrow(InvalidImageAssetException::new);
        if (request.categoryId() != null) {
            categoryRepository
                    .findByIdAndCatalogId(request.categoryId(), catalogId)
                    .orElseThrow(InvalidCategoryException::new);
        }

        Product product = new Product(
                catalogId, request.name(), request.sku(), request.description(), request.imageAssetId(), request.categoryId());
        try {
            Product saved = productRepository.saveAndFlush(product);
            return ProductResponse.from(saved, assetRepository.resolvePublicUrl(saved.getImageAssetId(), catalogId));
        } catch (DataIntegrityViolationException e) {
            // Rede de segurança contra corrida de escrita concorrente: dois requests podem
            // passar pelo existsByCatalogIdAndSku acima antes de qualquer um commitar — o
            // índice único parcial (V9) pega isso no banco, e aqui vira o mesmo 409 do
            // check-then-insert, em vez de vazar como erro genérico.
            throw new DuplicateSkuException();
        }
    }

    @Transactional
    public ProductResponse update(UUID ownerId, UUID catalogId, UUID id, UpdateProductRequest request) {
        requireOwnedCatalog(ownerId, catalogId);
        Product product = findInCatalogOrThrow(catalogId, id);

        if (request.sku() != null
                && productRepository.existsByCatalogIdAndSkuAndIdNot(catalogId, request.sku(), id)) {
            throw new DuplicateSkuException();
        }
        if (request.imageAssetId() != null) {
            assetRepository
                    .findByIdAndCatalogId(request.imageAssetId(), catalogId)
                    .orElseThrow(InvalidImageAssetException::new);
        }
        if (request.categoryId() != null) {
            categoryRepository
                    .findByIdAndCatalogId(request.categoryId(), catalogId)
                    .orElseThrow(InvalidCategoryException::new);
        }

        try {
            product.applyUpdate(
                    request.name(),
                    request.sku(),
                    request.description(),
                    request.imageAssetId(),
                    request.categoryId(),
                    request.quantityAvailable(),
                    request.isVisible(),
                    request.isOrderable(),
                    request.isActive());
            Product saved = productRepository.saveAndFlush(product);
            return ProductResponse.from(saved, assetRepository.resolvePublicUrl(saved.getImageAssetId(), catalogId));
        } catch (DataIntegrityViolationException e) {
            // Mesma rede de segurança de create() contra corrida de escrita concorrente no sku.
            throw new DuplicateSkuException();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID catalogId, UUID id) {
        requireOwnedCatalog(ownerId, catalogId);
        Product product = findInCatalogOrThrow(catalogId, id);
        // Exclusão permanente (US5) — distinta de desativar (isActive=false, US3), que
        // preserva o cadastro. A confirmação ("não pode ser desfeita") é do frontend.
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listByCatalog(UUID ownerId, UUID catalogId) {
        requireOwnedCatalog(ownerId, catalogId);
        List<Product> products = productRepository.findByCatalogIdOrderByCreatedAtDesc(catalogId);
        Map<UUID, String> imageUrlsByAssetId =
                assetRepository.resolvePublicUrls(products.stream().map(Product::getImageAssetId).toList());
        return products.stream()
                .map(product -> ProductResponse.from(product, imageUrlsByAssetId.get(product.getImageAssetId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getOne(UUID ownerId, UUID catalogId, UUID id) {
        requireOwnedCatalog(ownerId, catalogId);
        Product product = findInCatalogOrThrow(catalogId, id);
        return ProductResponse.from(product, assetRepository.resolvePublicUrl(product.getImageAssetId(), catalogId));
    }

    // Isolamento (ADR-0003): "catálogo não existe" e "catálogo não é meu" viram a mesma
    // exceção — 404 genérico, nunca 403, mesmo padrão de CategoryService/AssetService.
    private void requireOwnedCatalog(UUID ownerId, UUID catalogId) {
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);
    }

    private Product findInCatalogOrThrow(UUID catalogId, UUID id) {
        return productRepository.findByIdAndCatalogId(id, catalogId).orElseThrow(ProductNotFoundException::new);
    }
}
