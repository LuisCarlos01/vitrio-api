package dev.vitrio.api.catalog;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Todas as rotas exigem Access token válido — {@code /api/v1/catalogs} não está na lista pública do SecurityConfig. */
@RestController
@RequestMapping("/api/v1/catalogs")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<CatalogResponse> create(
            @Valid @RequestBody CreateCatalogRequest request, Authentication authentication) {
        CatalogResponse response = catalogService.create(ownerId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<CatalogResponse> list(Authentication authentication) {
        return catalogService.listMine(ownerId(authentication));
    }

    @GetMapping("/{id}")
    public CatalogResponse get(@PathVariable UUID id, Authentication authentication) {
        return catalogService.getMine(ownerId(authentication), id);
    }

    private UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
