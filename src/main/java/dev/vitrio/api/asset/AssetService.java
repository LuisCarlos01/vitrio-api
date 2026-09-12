package dev.vitrio.api.asset;

import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {

    // 10MB (spec 003) — checado no conteúdo já lido, não em Content-Length declarado pelo
    // cliente, que pode divergir do corpo real.
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final CatalogRepository catalogRepository;
    private final AssetRepository assetRepository;
    private final AssetStorage assetStorage;

    public AssetService(CatalogRepository catalogRepository, AssetRepository assetRepository, AssetStorage assetStorage) {
        this.catalogRepository = catalogRepository;
        this.assetRepository = assetRepository;
        this.assetStorage = assetStorage;
    }

    @Transactional
    public AssetResponse upload(UUID ownerId, UUID catalogId, MultipartFile file) {
        // Isolamento (ADR-0003): "catálogo não existe" e "catálogo não é meu" chegam aqui pela
        // mesma query e viram a mesma exceção — 404 genérico, nunca 403 (spec 003, US1 cenário 4).
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            // Só falha num I/O de verdade (ex.: cliente encerrou o upload no meio) — traduzida
            // pra ProblemDetail no GlobalExceptionHandler, mesmo padrão de toda exceção de
            // domínio deste módulo.
            throw new AssetReadException(e);
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new FileTooLargeException();
        }

        ImageFormat format = ImageFormat.detect(content).orElseThrow(UnsupportedImageFormatException::new);

        // Id gerado aqui, não pelo banco (Asset não usa @GeneratedValue): precisa existir
        // antes do save pra compor o storageKey no formato exigido pela spec 003
        // ("{catalogId}/{assetId}.{extensão}").
        UUID assetId = UUID.randomUUID();
        String storageKey = catalogId + "/" + assetId + format.extension();
        String publicUrl = assetStorage.upload(storageKey, content, format.contentType());

        Asset asset = new Asset(assetId, catalogId, storageKey, format.contentType(), content.length, publicUrl);
        return AssetResponse.from(assetRepository.saveAndFlush(asset));
    }
}
