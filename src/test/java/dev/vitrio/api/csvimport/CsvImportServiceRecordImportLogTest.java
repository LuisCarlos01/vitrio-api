package dev.vitrio.api.csvimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.vitrio.api.asset.AssetResponse;
import dev.vitrio.api.asset.AssetService;
import dev.vitrio.api.catalog.Catalog;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.product.CreateProductRequest;
import dev.vitrio.api.product.ProductRepository;
import dev.vitrio.api.product.ProductResponse;
import dev.vitrio.api.product.ProductService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Teste unitário (Mockito) de {@link CsvImportService#confirm} focado só na resiliência do
 * registro de log (spec 009, US1 cenário implícito "log é auditoria, nunca derruba a
 * confirmação") — complemento aos testes de integração ponta a ponta em
 * {@link CsvImportControllerConfirmTest}, que não conseguem forçar uma falha de persistência do
 * log sem substituir o repositório por um dublê. Mesmo padrão de {@code AuthServiceTest}: todos
 * os colaboradores mockados, sem banco, sem contexto Spring.
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceRecordImportLogTest {

    @Mock
    private CatalogRepository catalogRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductService productService;

    @Mock
    private AssetService assetService;

    @Mock
    private ImageDownloader imageDownloader;

    @Mock
    private CsvImportLogRepository csvImportLogRepository;

    @Test
    void logPersistenceFailureDoesNotPreventConfirmFromReturningResults() throws Exception {
        CsvImportService service = new CsvImportService(
                catalogRepository, productRepository, productService, assetService, imageDownloader, csvImportLogRepository);

        UUID ownerId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String csv = "nome,codigo,descricao,imagem\nColar,SKU-1,Desc,http://example.com/valid.jpg\n";
        MockMultipartFile file =
                new MockMultipartFile("file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(catalogRepository.findByIdAndOwnerId(catalogId, ownerId)).thenReturn(Optional.of(new Catalog(ownerId, "Loja", "loja")));
        when(productRepository.existsByCatalogIdAndSku(any(), anyString())).thenReturn(false);
        when(productRepository.countByCatalogId(catalogId)).thenReturn(0L);
        when(imageDownloader.download(anyString())).thenReturn(new byte[] {1, 2, 3});
        when(assetService.upload(any(), any()))
                .thenReturn(new AssetResponse(UUID.randomUUID(), catalogId, "image/jpeg", 3L, "http://s3/asset.jpg", Instant.now()));
        when(productService.create(any(), any(), any(CreateProductRequest.class)))
                .thenReturn(new ProductResponse(
                        productId, catalogId, "Colar", "SKU-1", "Desc", "http://s3/asset.jpg", null, 0, false, false, true, Instant.now()));
        // A falha real que este teste prova ser inofensiva: persistir o log explode.
        when(csvImportLogRepository.saveAndFlush(any())).thenThrow(new RuntimeException("db indisponível"));

        // Se a falha de persistência do log vazasse, esta chamada lançaria RuntimeException e o
        // teste falharia aqui — não é preciso um assertThatCode explícito.
        List<CsvImportConfirmRowResult> results = service.confirm(ownerId, catalogId, file);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).productId()).isEqualTo(productId);
        assertThat(results.get(0).errors()).isEmpty();
    }
}
