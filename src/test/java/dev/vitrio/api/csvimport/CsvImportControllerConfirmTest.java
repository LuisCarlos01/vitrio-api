package dev.vitrio.api.csvimport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.product.AbstractProductIntegrationTest;
import dev.vitrio.api.product.Product;
import dev.vitrio.api.product.ProductRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
 * Teste de integração ponta a ponta de {@code POST .../products/import/confirm} (spec 006, US2):
 * download de imagem real (contra um {@code com.sun.net.httpserver.HttpServer} local), upload no
 * S3 (LocalStack) e criação do {@code Product}.
 *
 * <p>O servidor de imagem de teste sobe em loopback, o que a {@link SsrfIpBlocklist} de produção
 * rejeitaria — por isso {@link PermissiveBlocklistConfig} substitui o bean só nesta classe por uma
 * versão que nunca bloqueia nada. A lógica real da blocklist já está provada, sem rede, em
 * {@link SsrfIpBlocklistTest}; os testes de download em si (timeout, redirect, limite de 10MB,
 * bloqueio de IP) estão em {@link ImageDownloaderTest}. Esta classe cobre só a integração ponta a
 * ponta: HTTP → validação estrutural → download → upload → criação do Product → resposta.
 */
@Import(CsvImportControllerConfirmTest.PermissiveBlocklistConfig.class)
class CsvImportControllerConfirmTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-csv-confirm";

    private static final byte[] JPEG_BYTES =
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0};

    private static HttpServer imageServer;

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
    static void setUp() throws Exception {
        LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + BUCKET_NAME);

        imageServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        imageServer.createContext("/valid.jpg", exchange -> respond(exchange, 200, JPEG_BYTES));
        imageServer.createContext("/not-an-image.txt", exchange -> respond(exchange, 200, "not an image".getBytes(StandardCharsets.UTF_8)));
        imageServer.createContext("/too-big.jpg", exchange -> {
            byte[] tooBig = new byte[11 * 1024 * 1024];
            respond(exchange, 200, tooBig);
        });
        imageServer.createContext("/missing.jpg", exchange -> respond(exchange, 404, new byte[0]));
        imageServer.setExecutor(Executors.newCachedThreadPool());
        imageServer.start();
    }

    @AfterAll
    static void tearDown() {
        imageServer.stop(0);
    }

    @Test
    void allValidRowsAreCreatedWithConservativeDefaults() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 1");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar Dourado,SKU-C1,Colar banhado a ouro," + imageUrl("/valid.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].productId").exists())
                .andExpect(jsonPath("$.rows[0].errors.length()").value(0));

        String responseBody = performConfirm(loginResponse, catalogId, csv2("SKU-C1B", imageUrl("/valid.jpg")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID productId = UUID.fromString(jsonMapper.readTree(responseBody).at("/rows/0/productId").asText());
        Product created = productRepository.findById(productId).orElseThrow();
        Assertions.assertFalse(created.isVisible());
        Assertions.assertFalse(created.isOrderable());
        Assertions.assertTrue(created.isActive());
        Assertions.assertEquals(0, created.getQuantityAvailable());
        Assertions.assertNull(created.getCategoryId());
    }

    @Test
    void mixedValidAndInvalidRowsReportEachOutcome() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 2");
        String csv = "nome,codigo,descricao,imagem\n"
                + ",SKU-BAD,Sem nome," + imageUrl("/valid.jpg") + "\n"
                + "Colar Valido,SKU-C2," + "Desc," + imageUrl("/valid.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].errors[0]").value("name is required"))
                .andExpect(jsonPath("$.rows[1].productId").exists())
                .andExpect(jsonPath("$.rows[1].errors.length()").value(0));

        Assertions.assertEquals(1, productRepository.countByCatalogId(UUID.fromString(catalogId)));
    }

    @Test
    void nonImageContentFailsWithTheSameFormatErrorAsSpec003() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 3");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,SKU-C3,Desc," + imageUrl("/not-an-image.txt") + "\n";

        performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].errors[0]").value("Unsupported image format"));
    }

    @Test
    void imageLargerThanTenMegabytesFailsBeforeAnyUpload() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 4");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,SKU-C4,Desc," + imageUrl("/too-big.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].errors[0]").value("File exceeds the maximum allowed size of 10MB"));

        Assertions.assertEquals(0, productRepository.countByCatalogId(UUID.fromString(catalogId)));
    }

    @Test
    void downloadFailureUsesTheGenericMessageRegardlessOfCause() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 5");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,SKU-C5,Desc," + imageUrl("/missing.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].errors[0]").value("could not download image from URL"));
    }

    @Test
    void codeColumnBecomesSkuAndAbsentCodeCreatesNullSku() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-6@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Confirm 6");
        String csv = "nome,codigo,descricao,imagem\n"
                + "Colar Com Codigo,SKU-C6,Desc," + imageUrl("/valid.jpg") + "\n"
                + "Colar Sem Codigo,,Desc," + imageUrl("/valid.jpg") + "\n";

        String responseBody = performConfirm(loginResponse, catalogId, csv)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID withCode = UUID.fromString(jsonMapper.readTree(responseBody).at("/rows/0/productId").asText());
        UUID withoutCode = UUID.fromString(jsonMapper.readTree(responseBody).at("/rows/1/productId").asText());
        Assertions.assertEquals("SKU-C6", productRepository.findById(withCode).orElseThrow().getSku());
        Assertions.assertNull(productRepository.findById(withoutCode).orElseThrow().getSku());
    }

    @Test
    void confirmingInCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("csv-confirm-7-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Confirm 7");
        LoginResponse intruder = registerAndLogin("csv-confirm-7-intruder@example.com", "Str0ngP@ssw0rd!");
        String csv = "nome,codigo,descricao,imagem\n" + "Colar,,Desc," + imageUrl("/valid.jpg") + "\n";

        performConfirm(intruder, catalogId, csv).andExpect(status().isNotFound());
    }

    @Test
    void catalogNeverImportedReturnsNoContentOnLatest() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-log-nolog@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Sem Import");

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/imports/latest", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmingImportRecordsLogWithAcceptedAndRejectedCounts() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-log-counts@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Log Counts");
        String csv = "nome,codigo,descricao,imagem\n"
                + ",SKU-LOG-BAD,Sem nome," + imageUrl("/valid.jpg") + "\n"
                + "Colar Log Valido,SKU-LOG-OK,Desc," + imageUrl("/valid.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/imports/latest", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogId").value(catalogId))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.confirmedAt", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void allRowsRejectedStillRecordsALog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-log-allrejected@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Log Tudo Recusado");
        String csv = "nome,codigo,descricao,imagem\n" + ",SKU-LOG-BAD2,Sem nome," + imageUrl("/valid.jpg") + "\n";

        performConfirm(loginResponse, catalogId, csv).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/imports/latest", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.rejectedCount").value(1));
    }

    @Test
    void latestReflectsTheMostRecentOfMultipleConfirmations() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-log-multiple@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Log Multiplas");
        performConfirm(loginResponse, catalogId, csv2("SKU-LOG-M1", imageUrl("/valid.jpg"))).andExpect(status().isOk());
        performConfirm(loginResponse, catalogId, "nome,codigo,descricao,imagem\n" + ",SKU-LOG-M2,Sem nome," + imageUrl("/valid.jpg") + "\n")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/imports/latest", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.rejectedCount").value(1));
    }

    @Test
    void latestImportOfAnotherResellerCatalogIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("csv-log-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Log Alheio");
        LoginResponse intruder = registerAndLogin("csv-log-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/imports/latest", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken()))
                .andExpect(status().isNotFound());
    }

    private String csv2(String sku, String url) {
        return "nome,codigo,descricao,imagem\n" + "Colar Dourado 2," + sku + ",Desc," + url + "\n";
    }

    private String imageUrl(String path) {
        return "http://" + imageServer.getAddress().getHostString() + ":" + imageServer.getAddress().getPort() + path;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private ResultActions performConfirm(LoginResponse loginResponse, String catalogId, String csv) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/products/import/confirm", catalogId)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }

    @TestConfiguration
    static class PermissiveBlocklistConfig {
        @Bean
        @Primary
        SsrfIpBlocklist permissiveSsrfIpBlocklist() {
            return new SsrfIpBlocklist() {
                @Override
                public boolean isBlocked(InetAddress address) {
                    return false;
                }
            };
        }
    }
}
