package dev.vitrio.api.asset;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Todas as rotas exigem Access token válido — mesmo padrão de {@code /api/v1/catalogs}. */
@RestController
@RequestMapping("/api/v1/catalogs/{catalogId}/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AssetResponse> upload(
            @PathVariable UUID catalogId, @RequestParam MultipartFile file, Authentication authentication) {
        AssetResponse response = assetService.upload(ownerId(authentication), catalogId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID catalogId, @PathVariable UUID id, Authentication authentication) {
        assetService.delete(ownerId(authentication), catalogId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
