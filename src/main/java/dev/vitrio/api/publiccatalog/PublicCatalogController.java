package dev.vitrio.api.publiccatalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota pública (spec 005) — sem Access token, liberada em {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/v1/public/catalogs")
public class PublicCatalogController {

    private final PublicCatalogService publicCatalogService;

    public PublicCatalogController(PublicCatalogService publicCatalogService) {
        this.publicCatalogService = publicCatalogService;
    }

    @GetMapping("/{slug}")
    public PublicCatalogResponse getBySlug(@PathVariable String slug) {
        return publicCatalogService.getBySlug(slug);
    }
}
