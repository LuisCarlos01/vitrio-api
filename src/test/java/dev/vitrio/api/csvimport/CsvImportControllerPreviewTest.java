package dev.vitrio.api.csvimport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.product.AbstractProductIntegrationTest;
import dev.vitrio.api.product.Product;
import dev.vitrio.api.product.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Teste de integração ponta a ponta de {@code POST .../products/import/preview} (spec 006,
 * US1). Ver {@link AbstractProductIntegrationTest} para o setup comum.
 */
class CsvImportControllerPreviewTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-csv-preview";

    @Autowired
    private ProductRepository productRepository;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8")).withServices(Service.S3);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("vitrio.aws.s3.endpoint-override", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("vitrio.aws.s3.region", LOCALSTACK::getRegion);
        registry.add("vitrio.aws.s3.access-key-id", LOCALSTACK::getAccessKey);
        registry.add("vitrio.aws.s3.secret-access-key", LOCALSTACK::getSecretKey);
        registry.add("vitrio.aws.s3.bucket-name", () -> BUCKET_NAME);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET_NAME);
    }

    @Test
    void validCsvReturnsPerRowResults() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 1");
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar Dourado,SKU-1,Colar banhado a ouro,https://example.com/colar.jpg\n"
                + "Brinco Prata,,Brinco simples,https://example.com/brinco.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].lineNumber").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("Colar Dourado"))
                .andExpect(jsonPath("$.rows[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].errors.length()").value(0))
                .andExpect(jsonPath("$.rows[1].sku").doesNotExist())
                .andExpect(jsonPath("$.rows[1].valid").value(true));
    }

    @Test
    void toleratesHeaderCaseAndSurroundingWhitespace() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 2");
        String csv = " Nome , CODIGO ,Descricao,Imagem\n" + "Colar,SKU-2,Desc,https://example.com/a.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].name").value("Colar"));
    }

    @Test
    void trimsWhitespaceFromCellValues() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 3");
        String csv = "nome,codigo,descricao,imagem\n" + "  Colar Espacado  ,  SKU-3  ,  Desc  ,  https://example.com/a.jpg  \n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].name").value("Colar Espacado"))
                .andExpect(jsonPath("$.rows[0].sku").value("SKU-3"))
                .andExpect(jsonPath("$.rows[0].valid").value(true));
    }

    @Test
    void missingNameIsAnErrorButDoesNotBlockOtherRows() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 4");
        String csv = "nome,codigo,descricao,imagem\n"
                + ",SKU-4,Desc,https://example.com/a.jpg\n"
                + "Colar Valido,SKU-5,Desc,https://example.com/b.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("name is required"))
                .andExpect(jsonPath("$.rows[1].valid").value(true));
    }

    @Test
    void blankOrMalformedImageUrlIsAnError() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 5");
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar A,SKU-6,Desc,\n"
                + "Colar B,SKU-7,Desc,nao-e-uma-url\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("image is required"))
                .andExpect(jsonPath("$.rows[1].valid").value(false))
                .andExpect(jsonPath("$.rows[1].errors[0]").value("image must be a valid http(s) URL"));
    }

    @Test
    void skuComparisonIsCaseSensitive() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-5b@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 5B");
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar A,sku-x,Desc,https://example.com/a.jpg\n"
                + "Colar B,SKU-X,Desc,https://example.com/b.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[1].valid").value(true));
    }

    @Test
    void duplicateSkuWithinTheSameFileMarksAllOccurrencesAsError() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-6@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 6");
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar A,DUP,Desc,https://example.com/a.jpg\n"
                + "Colar B,DUP,Desc,https://example.com/b.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("code is already in use in this catalog"))
                .andExpect(jsonPath("$.rows[1].valid").value(false))
                .andExpect(jsonPath("$.rows[1].errors[0]").value("code is already in use in this catalog"));
    }

    @Test
    void skuConflictingWithExistingProductIsAnError() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-7@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 7");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        createProductWithSku(catalogId, assetId, "EXISTENTE");

        String csv = "nome,codigo,descricao,imagem\n" + "Colar Novo,EXISTENTE,Desc,https://example.com/a.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("code is already in use in this catalog"));
    }

    @Test
    void skuConflictWithExistingProductIsCaseSensitive() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-7b@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 7B");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        createProductWithSku(catalogId, assetId, "EXISTENTE");

        // Mesma letra, caixa diferente do SKU já existente ("EXISTENTE") — não deve colidir.
        String csv = "nome,codigo,descricao,imagem\n" + "Colar Novo,existente,Desc,https://example.com/a.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true));
    }

    @Test
    void rowsBeyondTheFiftyProductLimitAreMarkedAsError() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-8@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 8");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        for (int i = 0; i < 50; i++) {
            productRepository.saveAndFlush(new Product(UUID.fromString(catalogId), "Existente " + i, null, null, UUID.fromString(assetId), null));
        }

        String csv = "nome,codigo,descricao,imagem\n" + "Colar Excedente,,Desc,https://example.com/a.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("catalog has reached the maximum of 50 products"));
    }

    @Test
    void withinTheFiftyLimitRowsThatFitStayValidWhileTheExcessDoesNot() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-8b@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 8B");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        for (int i = 0; i < 48; i++) {
            productRepository.saveAndFlush(new Product(UUID.fromString(catalogId), "Existente " + i, null, null, UUID.fromString(assetId), null));
        }
        // 48 já existentes + 3 linhas no arquivo: as 2 primeiras cabem (completam 50), a 3ª excede.
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar 1,,Desc,https://example.com/a.jpg\n"
                + "Colar 2,,Desc,https://example.com/b.jpg\n"
                + "Colar 3,,Desc,https://example.com/c.jpg\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[1].valid").value(true))
                .andExpect(jsonPath("$.rows[2].valid").value(false))
                .andExpect(jsonPath("$.rows[2].errors[0]").value("catalog has reached the maximum of 50 products"));
    }

    @Test
    void fileLargerThanTwoMegabytesIsRejectedEntirely() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-8c@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 8C");
        StringBuilder csv = new StringBuilder("nome,codigo,descricao,imagem\n");
        // Uma única linha com descrição gigante já ultrapassa 2MB sozinha.
        csv.append("Colar,,").append("x".repeat(3 * 1024 * 1024)).append(",https://example.com/a.jpg\n");

        performPreview(loginResponse, catalogId, csv.toString()).andExpect(status().isBadRequest());
    }

    @Test
    void rowWithFewerColumnsThanHeaderTreatsMissingOnesAsEmpty() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-9@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 9");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar Sem Imagem,SKU-9\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(false))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("image is required"));
    }

    @Test
    void extraColumnLikePriceIsAlwaysIgnored() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-10@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 10");
        String csv = "nome,codigo,descricao,imagem,preco\n" + "Colar,SKU-10,Desc,https://example.com/a.jpg,99.90\n";

        performPreview(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].valid").value(true));
    }

    @Test
    void fileWithMoreThanFiveHundredRowsIsRejectedEntirely() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-11@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 11");
        StringBuilder csv = new StringBuilder("nome,codigo,descricao,imagem\n");
        for (int i = 0; i < 501; i++) {
            csv.append("Colar ").append(i).append(",,Desc,https://example.com/a.jpg\n");
        }

        performPreview(loginResponse, catalogId, csv.toString()).andExpect(status().isBadRequest());
    }

    @Test
    void previewNeverCreatesProducts() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-preview-12@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo CSV 12");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,,Desc,https://example.com/a.jpg\n";

        performPreview(loginResponse, catalogId, csv).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(
                0, productRepository.countByCatalogId(UUID.fromString(catalogId)));
    }

    @Test
    void previewingInCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("csv-preview-13-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo CSV 13");
        LoginResponse intruder = registerAndLogin("csv-preview-13-intruder@example.com", "Str0ngP@ssw0rd!");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,,Desc,https://example.com/a.jpg\n";

        performPreview(intruder, catalogId, csv).andExpect(status().isNotFound());
    }

    private void createProductWithSku(String catalogId, String assetId, String sku) {
        productRepository.saveAndFlush(
                new Product(UUID.fromString(catalogId), "Produto Existente", sku, null, UUID.fromString(assetId), null));
    }

    private ResultActions performPreview(LoginResponse loginResponse, String catalogId, String csv) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/products/import/preview", catalogId)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
