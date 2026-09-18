package dev.vitrio.api.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.catalog.AbstractCatalogIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;
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
 * Teste de integração ponta a ponta do redimensionamento/compressão no upload (spec 013) —
 * mesmo endpoint de {@link AssetControllerUploadTest}, focado nos cenários de otimização de
 * imagem. Postgres real e S3 real (LocalStack) via Testcontainers.
 */
class AssetImageOptimizationTest extends AbstractCatalogIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-image-optimization";
    // Mesmo default de application.yml (vitrio.asset.max-dimension-px) — duplicado aqui só como
    // valor de teste, não lido da config real (é o próprio comportamento default, sem override).
    private static final int MAX_DIMENSION_PX = 1600;

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
    void resizesJpegAboveLimitAndReducesByteSize() throws Exception {
        LoginResponse loginResponse = registerAndLogin("image-opt-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Otimizacao 1");
        byte[] original = ImageFixtures.jpegOfDimensions(2000, 1000);

        String body = performUpload(loginResponse, catalogId, "photo.jpg", original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.byteSize").value(org.hamcrest.Matchers.lessThan(original.length)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicUrl = jsonMapper.readValue(body, AssetResponse.class).publicUrl();

        BufferedImage decoded = download(publicUrl);
        // 2000x1000 encaixado num quadrado 1600x1600 preservando proporção: lado maior vira
        // exatamente 1600, o menor escala na mesma razão (0.8) -> 800.
        assertEquals(MAX_DIMENSION_PX, decoded.getWidth());
        assertEquals(800, decoded.getHeight());
    }

    @Test
    void doesNotUpscaleJpegAlreadyWithinLimit() throws Exception {
        LoginResponse loginResponse = registerAndLogin("image-opt-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Otimizacao 2");
        byte[] original = ImageFixtures.jpegOfDimensions(800, 600);

        String body = performUpload(loginResponse, catalogId, "photo.jpg", original)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicUrl = jsonMapper.readValue(body, AssetResponse.class).publicUrl();

        BufferedImage decoded = download(publicUrl);
        assertEquals(800, decoded.getWidth());
        assertEquals(600, decoded.getHeight());
    }

    @Test
    void resizesPngAboveLimitPreservingTransparency() throws Exception {
        LoginResponse loginResponse = registerAndLogin("image-opt-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Otimizacao 3");
        byte[] original = ImageFixtures.pngOfDimensions(2000, 1000);

        String body = performUpload(loginResponse, catalogId, "photo.png", original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicUrl = jsonMapper.readValue(body, AssetResponse.class).publicUrl();

        BufferedImage decoded = download(publicUrl);
        assertEquals(MAX_DIMENSION_PX, decoded.getWidth());
        assertEquals(800, decoded.getHeight());
        assertTrue(decoded.getColorModel().hasAlpha());
    }

    // Cenário 4 da spec 013 (nenhum JPEG/PNG válido até 10MB falha por causa do processamento em
    // si) exercitado com uma imagem de ruído aleatório — comprime muito pior que uma foto real,
    // então já chega na casa de MBs em dimensões bem menores que uma foto de celular de verdade
    // (o caso real da issue #22), sem pagar o custo de gerar dezenas de MB no teste.
    @Test
    void processesALargeNoisyImageWithoutFailing() throws Exception {
        LoginResponse loginResponse = registerAndLogin("image-opt-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Otimizacao 5");
        byte[] original = ImageFixtures.noisyJpegOfDimensions(1800, 1350);
        assertTrue(original.length > 1024 * 1024, "fixture deveria passar de 1MB pra ser um teste de verdade");

        String body = performUpload(loginResponse, catalogId, "photo.jpg", original)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicUrl = jsonMapper.readValue(body, AssetResponse.class).publicUrl();

        BufferedImage decoded = download(publicUrl);
        assertEquals(MAX_DIMENSION_PX, decoded.getWidth());
        assertEquals(1200, decoded.getHeight());
    }

    @Test
    void doesNotModifyPngAlreadyWithinLimit() throws Exception {
        LoginResponse loginResponse = registerAndLogin("image-opt-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Otimizacao 4");
        byte[] original = ImageFixtures.pngOfDimensions(800, 600);

        // PNG dentro do limite: sem knob de qualidade a ganhar, passa direto sem reprocessar —
        // prova mais forte que só checar dimensão: o byteSize bate exatamente com o original.
        performUpload(loginResponse, catalogId, "photo.png", original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.byteSize").value(original.length));
    }

    private BufferedImage download(String publicUrl) throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(publicUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        return ImageIO.read(new ByteArrayInputStream(response.body()));
    }

    private ResultActions performUpload(LoginResponse loginResponse, String catalogId, String filename, byte[] content)
            throws Exception {
        String contentType = filename.endsWith(".png") ? "image/png" : "image/jpeg";
        return mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/assets", catalogId)
                .file(new MockMultipartFile("file", filename, contentType, content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
