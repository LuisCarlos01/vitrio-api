package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
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
 * Teste de integração ponta a ponta do logo do catálogo (spec 007, US1) — cenários que precisam
 * de um {@code Asset} real (via S3/LocalStack), separado de {@link CatalogControllerUpdateTest}
 * pra não obrigar o resto da suíte de personalização a subir um container de S3. Ver
 * {@link AbstractCatalogIntegrationTest}.
 */
class CatalogControllerLogoTest extends AbstractCatalogIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-catalog-logo";

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
    void settingLogoAssetIdReturnsResolvedLogoUrl() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-setlogo@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Com Logo");
        String assetId = createAssetAndGetId(owner, id);

        performUpdate(owner, id, "{\"logoAssetId\":\"" + assetId + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isNotEmpty());
    }

    @Test
    void omittingLogoAssetIdLeavesCurrentLogoUnchanged() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-keeplogo@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Mantem Logo");
        String assetId = createAssetAndGetId(owner, id);
        performUpdate(owner, id, "{\"logoAssetId\":\"" + assetId + "\"}").andExpect(status().isOk());

        performUpdate(owner, id, "{\"name\":\"Renomeado\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isNotEmpty());
    }

    @Test
    void logoAssetIdFromAnotherCatalogIsRejectedWithoutChangingLogo() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-crosslogo@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(owner, "Catalogo A");
        String catalogB = createCatalogAndGetId(owner, "Catalogo B");
        String assetOfB = createAssetAndGetId(owner, catalogB);

        performUpdate(owner, catalogA, "{\"logoAssetId\":\"" + assetOfB + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid logo asset"));

        mockMvc.perform(get("/api/v1/catalogs/" + catalogA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.logoUrl").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions performUpdate(
            LoginResponse loginResponse, String catalogId, String jsonBody) throws Exception {
        return mockMvc.perform(patch("/api/v1/catalogs/" + catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
