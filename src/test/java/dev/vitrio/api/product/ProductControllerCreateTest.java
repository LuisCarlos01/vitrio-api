package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.asset.AssetResponse;
import dev.vitrio.api.auth.LoginResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/catalogs/{catalogId}/products}
 * (spec 004, US2). Ver {@link AbstractProductIntegrationTest} para o setup comum.
 */
class ProductControllerCreateTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-product-create";

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
    void createsProductWithConservativeDefaults() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 1");
        AssetResponse asset = createAsset(loginResponse, catalogId);

        performCreate(loginResponse, catalogId, new CreateProductRequest("Colar Dourado", null, null, asset.id(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Colar Dourado"))
                .andExpect(jsonPath("$.imageUrl").value(asset.publicUrl()))
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.quantityAvailable").value(0))
                .andExpect(jsonPath("$.isVisible").value(false))
                .andExpect(jsonPath("$.isOrderable").value(false))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createsProductWithValidCategory() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 2");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String categoryId = createCategoryForCatalog(loginResponse, catalogId, "Semijoias");

        performCreate(
                        loginResponse,
                        catalogId,
                        new CreateProductRequest("Anel", "SKU-1", "Descricao", UUID.fromString(assetId), UUID.fromString(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    @Test
    void rejectsImageAssetIdThatDoesNotExist() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 3");

        performCreate(loginResponse, catalogId, new CreateProductRequest("Colar", null, null, UUID.randomUUID(), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsImageAssetIdFromAnotherCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-4@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Produto 4A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Produto 4B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);

        performCreate(loginResponse, catalogB, new CreateProductRequest("Colar", null, null, UUID.fromString(assetOfA), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCategoryIdThatDoesNotExist() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 5");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        performCreate(
                        loginResponse,
                        catalogId,
                        new CreateProductRequest("Colar", null, null, UUID.fromString(assetId), UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCategoryIdFromAnotherCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-6@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Produto 6A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Produto 6B");
        String assetOfB = createAssetAndGetId(loginResponse, catalogB);
        String categoryOfA = createCategoryForCatalog(loginResponse, catalogA, "Categoria de A");

        performCreate(
                        loginResponse,
                        catalogB,
                        new CreateProductRequest("Colar", null, null, UUID.fromString(assetOfB), UUID.fromString(categoryOfA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateSkuInSameCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-7@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 7");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        performCreate(
                        loginResponse,
                        catalogId,
                        new CreateProductRequest("Colar", "DUP-1", null, UUID.fromString(assetId), null))
                .andExpect(status().isCreated());
        performCreate(
                        loginResponse,
                        catalogId,
                        new CreateProductRequest("Outro Colar", "DUP-1", null, UUID.fromString(assetId), null))
                .andExpect(status().isConflict());
    }

    @Test
    void allowsSameSkuAcrossDifferentCatalogs() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-8@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Produto 8A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Produto 8B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);
        String assetOfB = createAssetAndGetId(loginResponse, catalogB);

        performCreate(loginResponse, catalogA, new CreateProductRequest("Colar", "REPEAT", null, UUID.fromString(assetOfA), null))
                .andExpect(status().isCreated());
        performCreate(loginResponse, catalogB, new CreateProductRequest("Colar", "REPEAT", null, UUID.fromString(assetOfB), null))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsCreationWhenCatalogAlreadyHas50Products() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-9@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 9");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        for (int i = 0; i < 50; i++) {
            performCreate(loginResponse, catalogId, new CreateProductRequest("Produto " + i, null, null, UUID.fromString(assetId), null))
                    .andExpect(status().isCreated());
        }

        performCreate(loginResponse, catalogId, new CreateProductRequest("Produto 51", null, null, UUID.fromString(assetId), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingInCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("create-product-10-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Produto 10");
        String assetId = createAssetAndGetId(owner, catalogId);
        LoginResponse intruder = registerAndLogin("create-product-10-intruder@example.com", "Str0ngP@ssw0rd!");

        performCreate(intruder, catalogId, new CreateProductRequest("Colar", null, null, UUID.fromString(assetId), null))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-product-11@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Produto 11");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateProductRequest("Colar", null, null, UUID.fromString(assetId), null))))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions performCreate(LoginResponse loginResponse, String catalogId, CreateProductRequest request)
            throws Exception {
        return mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }
}
