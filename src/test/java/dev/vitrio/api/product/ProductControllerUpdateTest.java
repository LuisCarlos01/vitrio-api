package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Teste de integração ponta a ponta de {@code PATCH .../products/{id}} (spec 004, US3). Ver
 * {@link AbstractProductIntegrationTest} para o setup comum.
 */
class ProductControllerUpdateTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-product-update";

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
    void patchingASingleFieldChangesOnlyThatField() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Update 1");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar Original", assetId);

        performUpdate(loginResponse, catalogId, productId, "{\"quantityAvailable\": 10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Colar Original"))
                .andExpect(jsonPath("$.quantityAvailable").value(10))
                .andExpect(jsonPath("$.isVisible").value(false));
    }

    @Test
    void deactivatingPreservesTheRecordWithoutTouchingVisibilityOrOrderability() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Update 2");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar", assetId);

        performUpdate(loginResponse, catalogId, productId, "{\"isVisible\": true, \"isOrderable\": true}")
                .andExpect(status().isOk());

        // isVisible/isOrderable são independentes de isActive (CONTEXT.md, "Estados do
        // produto") — desativar não mexe neles, mesmo enviados só como "isActive: false".
        performUpdate(loginResponse, catalogId, productId, "{\"isActive\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.isVisible").value(true))
                .andExpect(jsonPath("$.isOrderable").value(true))
                .andExpect(jsonPath("$.name").value("Colar"));
    }

    @Test
    void reactivatingDoesNotResetVisibilityOrOrderability() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Update 3");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar", assetId);

        performUpdate(loginResponse, catalogId, productId, "{\"isVisible\": true, \"isOrderable\": true}")
                .andExpect(status().isOk());
        performUpdate(loginResponse, catalogId, productId, "{\"isActive\": false}")
                .andExpect(status().isOk());

        // Reativar (isActive: true) não "restaura" nem "reseta" isVisible/isOrderable —
        // eles nunca foram tocados pelo isActive, então continuam exatamente como a
        // revendedora os deixou por último (US3 cenário 3).
        performUpdate(loginResponse, catalogId, productId, "{\"isActive\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.isVisible").value(true))
                .andExpect(jsonPath("$.isOrderable").value(true));
    }

    @Test
    void rejectsSkuThatCollidesWithAnotherProductInSameCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Update 4");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        createProductWithSku(loginResponse, catalogId, "Colar A", assetId, "SKU-A");
        String productBId = createProduct(loginResponse, catalogId, "Colar B", assetId);

        performUpdate(loginResponse, catalogId, productBId, "{\"sku\": \"SKU-A\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void allowsKeepingItsOwnSkuUnchanged() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Update 5");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProductWithSku(loginResponse, catalogId, "Colar", assetId, "SKU-SAME");

        performUpdate(loginResponse, catalogId, productId, "{\"sku\": \"SKU-SAME\", \"description\": \"Nova descricao\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-SAME"))
                .andExpect(jsonPath("$.description").value("Nova descricao"));
    }

    @Test
    void rejectsImageAssetIdFromAnotherCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-6@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Update 6A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Update 6B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);
        String assetOfB = createAssetAndGetId(loginResponse, catalogB);
        String productId = createProduct(loginResponse, catalogB, "Colar", assetOfB);

        performUpdate(loginResponse, catalogB, productId, "{\"imageAssetId\": \"" + assetOfA + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCategoryIdFromAnotherCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-7@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Update 7A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Update 7B");
        String assetOfB = createAssetAndGetId(loginResponse, catalogB);
        String categoryOfA = createCategoryForCatalog(loginResponse, catalogA, "Categoria de A");
        String productId = createProduct(loginResponse, catalogB, "Colar", assetOfB);

        performUpdate(loginResponse, catalogB, productId, "{\"categoryId\": \"" + categoryOfA + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingProductFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("update-product-8@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Update 8A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Update 8B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);
        String productOfA = createProduct(loginResponse, catalogA, "Colar", assetOfA);

        performUpdate(loginResponse, catalogB, productOfA, "{\"name\": \"Novo Nome\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void updatingProductOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("update-product-9-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Update 9");
        String assetId = createAssetAndGetId(owner, catalogId);
        String productId = createProduct(owner, catalogId, "Colar", assetId);
        LoginResponse intruder = registerAndLogin("update-product-9-intruder@example.com", "Str0ngP@ssw0rd!");

        performUpdate(intruder, catalogId, productId, "{\"name\": \"Novo Nome\"}")
                .andExpect(status().isNotFound());
    }

    private String createProduct(LoginResponse loginResponse, String catalogId, String name, String assetId) throws Exception {
        return createProductWithSku(loginResponse, catalogId, name, assetId, null);
    }

    private String createProductWithSku(LoginResponse loginResponse, String catalogId, String name, String assetId, String sku)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateProductRequest(name, sku, null, UUID.fromString(assetId), null))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, ProductResponse.class).id().toString();
    }

    private ResultActions performUpdate(LoginResponse loginResponse, String catalogId, String productId, String json)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
