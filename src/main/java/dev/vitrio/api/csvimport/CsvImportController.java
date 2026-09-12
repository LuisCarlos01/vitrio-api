package dev.vitrio.api.csvimport;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Todas as rotas exigem Access token válido — mesmo padrão de {@code /api/v1/catalogs}. */
@RestController
@RequestMapping("/api/v1/catalogs/{catalogId}/products/import")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(path = "/preview", consumes = "multipart/form-data")
    public CsvImportPreviewResponse preview(
            @PathVariable UUID catalogId, @RequestParam MultipartFile file, Authentication authentication) {
        List<CsvImportRowResult> rows = csvImportService.validate(ownerId(authentication), catalogId, file);
        return new CsvImportPreviewResponse(rows);
    }

    private UUID ownerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
