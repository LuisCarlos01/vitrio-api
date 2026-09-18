package dev.vitrio.api.asset;

import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.product.ProductRepository;
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
    private final ProductRepository productRepository;
    private final ImageOptimizer imageOptimizer;

    public AssetService(
            CatalogRepository catalogRepository,
            AssetRepository assetRepository,
            AssetStorage assetStorage,
            ProductRepository productRepository,
            ImageOptimizer imageOptimizer) {
        this.catalogRepository = catalogRepository;
        this.assetRepository = assetRepository;
        this.assetStorage = assetStorage;
        this.productRepository = productRepository;
        this.imageOptimizer = imageOptimizer;
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
        return upload(catalogId, content);
    }

    /**
     * Mesma validação (tamanho, formato por conteúdo real) e upload de {@link #upload(UUID, UUID,
     * MultipartFile)}, mas recebendo os bytes diretamente — usado pela importação de CSV (spec
     * 006), que já validou o catálogo e já tem os bytes baixados da URL da imagem, sem passar por
     * um {@link MultipartFile} nem repetir a checagem de posse do catálogo.
     */
    @Transactional
    public AssetResponse upload(UUID catalogId, byte[] content) {
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new FileTooLargeException();
        }

        ImageFormat format = ImageFormat.detect(content).orElseThrow(UnsupportedImageFormatException::new);
        // Redimensiona/recomprime antes de salvar (spec 013) — o que chega no S3 e é persistido
        // em Asset.byteSize já é o otimizado, nunca o original recebido do cliente.
        byte[] optimizedContent = imageOptimizer.optimize(content, format);

        // Id gerado aqui, não pelo banco (Asset não usa @GeneratedValue): precisa existir
        // antes do save pra compor o storageKey no formato exigido pela spec 003
        // ("{catalogId}/{assetId}.{extensão}").
        UUID assetId = UUID.randomUUID();
        String storageKey = catalogId + "/" + assetId + format.extension();
        String publicUrl = assetStorage.upload(storageKey, optimizedContent, format.contentType());

        Asset asset = new Asset(assetId, catalogId, storageKey, format.contentType(), optimizedContent.length, publicUrl);
        return AssetResponse.from(assetRepository.saveAndFlush(asset));
    }

    @Transactional
    public void delete(UUID ownerId, UUID catalogId, UUID id) {
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);
        Asset asset = assetRepository.findByIdAndCatalogId(id, catalogId).orElseThrow(AssetNotFoundException::new);

        // Tudo-ou-nada (spec 012): checa uso antes de tocar em S3 ou banco — nunca deveria ser
        // possível derrubar a imagem de um produto/loja já existente por engano.
        if (productRepository.existsByImageAssetId(id) || catalogRepository.existsByLogoAssetId(id)) {
            throw new AssetInUseException();
        }

        // S3 primeiro: se falhar, a transação não chega a remover o registro do banco — evita um
        // Asset com registro no banco mas sem objeto no S3 (publicUrl quebrada).
        assetStorage.delete(asset.getStorageKey());
        assetRepository.delete(asset);
    }
}
