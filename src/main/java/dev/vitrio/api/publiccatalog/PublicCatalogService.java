package dev.vitrio.api.publiccatalog;

import dev.vitrio.api.asset.Asset;
import dev.vitrio.api.asset.AssetRepository;
import dev.vitrio.api.catalog.Catalog;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.category.CategoryRepository;
import dev.vitrio.api.product.Product;
import dev.vitrio.api.product.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCatalogService {

    private final CatalogRepository catalogRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;

    public PublicCatalogService(
            CatalogRepository catalogRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            AssetRepository assetRepository) {
        this.catalogRepository = catalogRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public PublicCatalogResponse getBySlug(String slug) {
        Catalog catalog = catalogRepository.findBySlug(slug).orElseThrow(PublicCatalogNotFoundException::new);

        List<PublicCategoryResponse> categories = categoryRepository
                .findByCatalogIdOrderByCreatedAtDesc(catalog.getId())
                .stream()
                .map(PublicCategoryResponse::from)
                .toList();

        List<Product> visibleProducts =
                productRepository.findByCatalogIdAndActiveTrueAndVisibleTrueOrderByCreatedAtDesc(catalog.getId());
        Map<UUID, String> imageUrlsByAssetId = assetRepository
                .findAllById(visibleProducts.stream().map(Product::getImageAssetId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Asset::getId, Asset::getPublicUrl));
        List<PublicProductResponse> products = visibleProducts.stream()
                .map(product -> PublicProductResponse.from(product, imageUrlsByAssetId.get(product.getImageAssetId())))
                .toList();

        String logoUrl = assetRepository.resolvePublicUrl(catalog.getLogoAssetId(), catalog.getId());

        return PublicCatalogResponse.from(catalog, logoUrl, categories, products);
    }
}
