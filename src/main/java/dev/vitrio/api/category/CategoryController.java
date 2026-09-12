package dev.vitrio.api.category;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Todas as rotas exigem Access token válido — mesmo padrão de {@code /api/v1/catalogs}. */
@RestController
@RequestMapping("/api/v1/catalogs/{catalogId}/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @PathVariable UUID catalogId, @Valid @RequestBody CreateCategoryRequest request, Authentication authentication) {
        CategoryResponse response = categoryService.create(ownerId(authentication), catalogId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<CategoryResponse> list(@PathVariable UUID catalogId, Authentication authentication) {
        return categoryService.listByCatalog(ownerId(authentication), catalogId);
    }

    @PatchMapping("/{id}")
    public CategoryResponse rename(
            @PathVariable UUID catalogId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request,
            Authentication authentication) {
        return categoryService.rename(ownerId(authentication), catalogId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID catalogId, @PathVariable UUID id, Authentication authentication) {
        categoryService.delete(ownerId(authentication), catalogId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
