package dev.vitrio.api.catalog;

import dev.vitrio.api.asset.AssetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CatalogRepository catalogRepository;
    private final AssetRepository assetRepository;

    public CatalogService(CatalogRepository catalogRepository, AssetRepository assetRepository) {
        this.catalogRepository = catalogRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public CatalogResponse create(UUID ownerId, CreateCatalogRequest request) {
        String slug = uniqueSlugFor(request.name());
        Catalog catalog = new Catalog(ownerId, request.name(), slug);
        return toResponse(catalogRepository.saveAndFlush(catalog));
    }

    @Transactional(readOnly = true)
    public List<CatalogResponse> listMine(UUID ownerId) {
        return catalogRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogResponse getMine(UUID ownerId, UUID id) {
        return toResponse(findOwnedOrThrow(ownerId, id));
    }

    @Transactional
    public CatalogResponse update(UUID ownerId, UUID id, UpdateCatalogRequest request) {
        Catalog catalog = findOwnedOrThrow(ownerId, id);
        if (request.logoAssetId() != null) {
            assetRepository
                    .findByIdAndCatalogId(request.logoAssetId(), id)
                    .orElseThrow(InvalidLogoAssetException::new);
        }
        catalog.applyPersonalization(
                request.name(), request.primaryColorHex(), request.buttonColorHex(), request.instagramHandle());
        catalog.updateLogo(request.logoAssetId());
        return toResponse(catalog);
    }

    @Transactional
    public CatalogResponse updateWhatsapp(UUID ownerId, UUID id, UpdateWhatsappRequest request) {
        Catalog catalog = findOwnedOrThrow(ownerId, id);
        catalog.updateWhatsappNumber(WhatsappNumberNormalizer.normalize(request.whatsappNumber()));
        return toResponse(catalog);
    }

    @Transactional
    public CatalogResponse verifyWhatsapp(UUID ownerId, UUID id) {
        Catalog catalog = findOwnedOrThrow(ownerId, id);
        if (catalog.getWhatsappNumber() == null) {
            throw new WhatsappNumberNotConfiguredException();
        }
        catalog.verifyWhatsapp(Instant.now());
        return toResponse(catalog);
    }

    // logoAssetId já foi validado contra o mesmo catalogId em quem grava (update()) — a busca
    // isolada por catalogId dentro de resolvePublicUrl aqui é rede de segurança contra
    // inconsistência de dado, mesmo padrão de isolamento paranoico usado em todo o domínio
    // (ADR-0003), não uma reautorização de fato necessária no caminho feliz.
    private CatalogResponse toResponse(Catalog catalog) {
        String logoUrl = assetRepository.resolvePublicUrl(catalog.getLogoAssetId(), catalog.getId());
        return CatalogResponse.from(catalog, logoUrl);
    }

    // Único ponto de isolamento (ADR-0003): "não existe" e "existe mas não é meu" chegam aqui
    // pela mesma query e viram a mesma exceção — reaproveitado por toda leitura/escrita que
    // opera sobre um catálogo específico por id.
    private Catalog findOwnedOrThrow(UUID ownerId, UUID id) {
        return catalogRepository.findByIdAndOwnerId(id, ownerId).orElseThrow(CatalogNotFoundException::new);
    }

    // Verifica-então-insere: aceitável na escala deste projeto (sem cadastro concorrente de
    // catálogos com o mesmo nome em produção real) — o índice único em `slug` continua como
    // rede de segurança contra uma colisão de corrida, que apareceria como erro 500 nesse caso
    // raro em vez de resolvida automaticamente. Revisitar se o volume de escrita justificar.
    private String uniqueSlugFor(String name) {
        String base = SlugGenerator.slugify(name);
        if (!catalogRepository.existsBySlug(base)) {
            return base;
        }
        int suffix = 2;
        String candidate;
        do {
            candidate = base + "-" + suffix++;
        } while (catalogRepository.existsBySlug(candidate));
        return candidate;
    }
}
