package dev.vitrio.api.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CatalogRepository catalogRepository;

    public CatalogService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @Transactional
    public CatalogResponse create(UUID ownerId, CreateCatalogRequest request) {
        String slug = uniqueSlugFor(request.name());
        Catalog catalog = new Catalog(ownerId, request.name(), slug);
        return CatalogResponse.from(catalogRepository.saveAndFlush(catalog));
    }

    @Transactional(readOnly = true)
    public List<CatalogResponse> listMine(UUID ownerId) {
        return catalogRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(CatalogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogResponse getMine(UUID ownerId, UUID id) {
        Catalog catalog = catalogRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(CatalogNotFoundException::new);
        return CatalogResponse.from(catalog);
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
