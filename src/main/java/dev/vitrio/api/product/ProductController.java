package dev.vitrio.api.product;

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

/** Todas as rotas exigem Access token válido — mesmo padrão de {@code /api/v1/catalogs}. */
@RestController
@RequestMapping("/api/v1/catalogs/{catalogId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @PathVariable UUID catalogId, @Valid @RequestBody CreateProductRequest request, Authentication authentication) {
        ProductResponse response = productService.create(ownerId(authentication), catalogId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ProductResponse> list(@PathVariable UUID catalogId, Authentication authentication) {
        return productService.listByCatalog(ownerId(authentication), catalogId);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID catalogId, @PathVariable UUID id, Authentication authentication) {
        return productService.getOne(ownerId(authentication), catalogId, id);
    }

    private UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
