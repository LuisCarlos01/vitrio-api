package dev.vitrio.api.asset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.catalog.AbstractCatalogIntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
 * Teste de integração ponta a ponta de {@code POST /api/v1/catalogs/{catalogId}/assets} (spec
 * 003, US1) — Postgres real e S3 real (LocalStack) via Testcontainers, sem mockar o
 * {@code S3Client}, mesmo padrão de {@code catalog}.
 */
class AssetControllerUploadTest extends AbstractCatalogIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket";

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
    static void createBucket() throws IOException, InterruptedException {
        LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET_NAME);
    }

    @Test
    void uploadsValidJpegAndPersistsAssetWithPublicUrl() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 1");

        performUpload(loginResponse, catalogId, "photo.jpg", "image/jpeg", ImageFixtures.JPEG_BYTES)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogId").value(catalogId))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.byteSize").value(ImageFixtures.JPEG_BYTES.length))
                .andExpect(jsonPath("$.publicUrl").value(org.hamcrest.Matchers.containsString(BUCKET_NAME)));
    }

    @Test
    void uploadsValidPng() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 2");

        performUpload(loginResponse, catalogId, "photo.png", "image/png", ImageFixtures.PNG_BYTES)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void publicUrlServesTheImageDirectlyFromS3() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 3");

        String body = performUpload(loginResponse, catalogId, "photo.jpg", "image/jpeg", ImageFixtures.JPEG_BYTES)
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicUrl = jsonMapper.readValue(body, AssetResponse.class).publicUrl();

        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(publicUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertArrayEquals(ImageFixtures.JPEG_BYTES, response.body());
    }

    @Test
    void rejectsContentThatDoesNotMatchAnyAcceptedFormatDespiteJpegExtension() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 4");

        performUpload(loginResponse, catalogId, "photo.jpg", "image/jpeg", ImageFixtures.PLAIN_TEXT_BYTES)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFileLargerThan10MB() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 5");

        byte[] tooLarge = ImageFixtures.jpegOfSize(10 * 1024 * 1024 + 1);

        performUpload(loginResponse, catalogId, "photo.jpg", "image/jpeg", tooLarge)
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadToCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("asset-upload-6-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Asset 6");
        LoginResponse intruder = registerAndLogin("asset-upload-6-intruder@example.com", "Str0ngP@ssw0rd!");

        performUpload(intruder, catalogId, "photo.jpg", "image/jpeg", ImageFixtures.JPEG_BYTES)
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        LoginResponse loginResponse = registerAndLogin("asset-upload-7@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Asset 7");

        mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/assets", catalogId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", ImageFixtures.JPEG_BYTES)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions performUpload(
            LoginResponse loginResponse, String catalogId, String filename, String contentType, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/assets", catalogId)
                .file(new MockMultipartFile("file", filename, contentType, content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
