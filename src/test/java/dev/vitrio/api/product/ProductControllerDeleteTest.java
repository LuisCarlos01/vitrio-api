package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Teste de integração ponta a ponta de {@code DELETE .../products/{id}} (spec 004, US5). Ver
 * {@link AbstractProductIntegrationTest} para o setup comum.
 */
class ProductControllerDeleteTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-product-delete";

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
    void deletesProductPermanently() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-product-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 1");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar a Excluir", assetId);

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingIsDistinctFromDeactivating() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-product-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 2");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String deactivatedId = createProduct(loginResponse, catalogId, "Colar Desativado", assetId);
        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, deactivatedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // Desativado continua existindo e consultável — diferente de excluído (US5).
        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, deactivatedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void deletingProductFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-product-3@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Delete 3A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Delete 3B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);
        String productOfA = createProduct(loginResponse, catalogA, "Colar", assetOfA);

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/products/{id}", catalogB, productOfA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingProductOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("delete-product-4-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Delete 4");
        String assetId = createAssetAndGetId(owner, catalogId);
        String productId = createProduct(owner, catalogId, "Colar", assetId);
        LoginResponse intruder = registerAndLogin("delete-product-4-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-product-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 5");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar", assetId);

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId))
                .andExpect(status().isUnauthorized());
    }

    private String createProduct(LoginResponse loginResponse, String catalogId, String name, String assetId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new CreateProductRequest(name, null, null, UUID.fromString(assetId), null))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, ProductResponse.class).id().toString();
    }
}
