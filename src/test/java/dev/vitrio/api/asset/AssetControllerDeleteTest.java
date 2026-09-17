package dev.vitrio.api.asset;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.catalog.AbstractCatalogIntegrationTest;
import dev.vitrio.api.product.CreateProductRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Teste de integração ponta a ponta de {@code DELETE /api/v1/catalogs/{catalogId}/assets/{id}}
 * (spec 012, US1) — Postgres real e S3 real (LocalStack) via Testcontainers, mesmo padrão de
 * {@link AssetControllerUploadTest}.
 */
class AssetControllerDeleteTest extends AbstractCatalogIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-asset-delete";

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
    void deletesUnreferencedAssetFromDatabaseAndStorage() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 1");
        AssetResponse asset = createAsset(loginResponse, catalogId);

        performDelete(loginResponse, catalogId, asset.id()).andExpect(status().isNoContent());

        // Objeto sumiu do S3 (efeito do lado do storage, não só do banco).
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(asset.publicUrl())).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        assertNotEquals(200, response.statusCode());

        // Registro sumiu do banco: uma segunda exclusão do mesmo id já é 404, não 409/204 de novo.
        performDelete(loginResponse, catalogId, asset.id()).andExpect(status().isNotFound());
    }

    @Test
    void refusesToDeleteAssetReferencedByAProduct() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 2");
        AssetResponse asset = createAsset(loginResponse, catalogId);
        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new CreateProductRequest("Colar", null, null, asset.id(), null))));

        performDelete(loginResponse, catalogId, asset.id()).andExpect(status().isConflict());
    }

    @Test
    void refusesToDeleteAssetReferencedAsCatalogLogo() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 3");
        AssetResponse asset = createAsset(loginResponse, catalogId);
        mockMvc.perform(patch("/api/v1/catalogs/{id}", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoAssetId\": \"" + asset.id() + "\"}"));

        performDelete(loginResponse, catalogId, asset.id()).andExpect(status().isConflict());
    }

    @Test
    void deletingAssetFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-4@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Delete 4A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Delete 4B");
        AssetResponse assetOfA = createAsset(loginResponse, catalogA);

        performDelete(loginResponse, catalogB, assetOfA.id()).andExpect(status().isNotFound());
    }

    @Test
    void deletingFromCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("asset-delete-5-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Delete 5");
        AssetResponse asset = createAsset(owner, catalogId);
        LoginResponse intruder = registerAndLogin("asset-delete-5-intruder@example.com", "Str0ngP@ssw0rd!");

        performDelete(intruder, catalogId, asset.id()).andExpect(status().isNotFound());
    }

    @Test
    void deletingNonExistentAssetIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-6@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 6");

        performDelete(loginResponse, catalogId, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-delete-7@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 7");
        AssetResponse asset = createAsset(loginResponse, catalogId);

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/assets/{id}", catalogId, asset.id()))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(
            LoginResponse loginResponse, String catalogId, UUID assetId) throws Exception {
        return mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/assets/{id}", catalogId, assetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
