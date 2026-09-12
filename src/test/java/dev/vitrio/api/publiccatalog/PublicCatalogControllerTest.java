package dev.vitrio.api.publiccatalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.catalog.CatalogResponse;
import dev.vitrio.api.catalog.UpdateCatalogRequest;
import dev.vitrio.api.catalog.UpdateWhatsappRequest;
import dev.vitrio.api.product.AbstractProductIntegrationTest;
import dev.vitrio.api.product.CreateProductRequest;
import dev.vitrio.api.product.ProductResponse;
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
 * Teste de integração ponta a ponta de {@code GET /api/v1/public/catalogs/{slug}} (spec 005,
 * US1) — sem Access token, mesmo padrão de Testcontainers (Postgres + LocalStack) do módulo
 * {@code product}.
 */
class PublicCatalogControllerTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-public-catalog";

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
    void returnsCatalogIdentityCategoriesAndVisibleProductsWithoutAuthentication() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 1");
        mockMvc.perform(patch("/api/v1/catalogs/{id}", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(
                        new UpdateCatalogRequest(null, "#123456", "#654321", "boutique.publica"))));
        String slug = fetchSlug(loginResponse, catalogId);
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String categoryId = createCategoryForCatalog(loginResponse, catalogId, "Semijoias");
        String productId = createProduct(loginResponse, catalogId, "Colar Dourado", assetId, categoryId, "SKU-COL", "Colar banhado a ouro");
        makeVisible(loginResponse, catalogId, productId);
        patchProduct(loginResponse, catalogId, productId, "{\"quantityAvailable\": 5}");

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catalogo Publico 1"))
                .andExpect(jsonPath("$.primaryColorHex").value("#123456"))
                .andExpect(jsonPath("$.buttonColorHex").value("#654321"))
                .andExpect(jsonPath("$.instagramHandle").value("boutique.publica"))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Semijoias"))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value(productId))
                .andExpect(jsonPath("$.products[0].sku").value("SKU-COL"))
                .andExpect(jsonPath("$.products[0].description").value("Colar banhado a ouro"))
                .andExpect(jsonPath("$.products[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.products[0].imageUrl", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.products[0].isOrderable").value(true))
                .andExpect(jsonPath("$.products[0].quantityAvailable").value(5));
    }

    @Test
    void inactiveProductDoesNotAppearInPublicListing() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 2");
        String slug = fetchSlug(loginResponse, catalogId);
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar Inativo", assetId, null);
        makeVisible(loginResponse, catalogId, productId);
        patchProduct(loginResponse, catalogId, productId, "{\"isActive\": false}");

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0));
    }

    @Test
    void hiddenProductDoesNotAppearInPublicListing() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 3");
        String slug = fetchSlug(loginResponse, catalogId);
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        // Não chama makeVisible: produto nasce isVisible=false (spec 004, US2).
        createProduct(loginResponse, catalogId, "Colar Oculto", assetId, null);

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0));
    }

    @Test
    void visibleButNotOrderableProductStillAppears() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 4");
        String slug = fetchSlug(loginResponse, catalogId);
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Colar Sem Estoque", assetId, null);
        patchProduct(loginResponse, catalogId, productId, "{\"isVisible\": true, \"isOrderable\": false}");

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].isOrderable").value(false));
    }

    @Test
    void unverifiedWhatsappNumberIsNeverExposed() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 5");
        String slug = fetchSlug(loginResponse, catalogId);
        mockMvc.perform(put("/api/v1/catalogs/{id}/whatsapp", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateWhatsappRequest("11987654321"))));

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").doesNotExist());
    }

    @Test
    void verifiedWhatsappNumberIsExposed() throws Exception {
        LoginResponse loginResponse = registerAndLogin("public-catalog-6@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Publico 6");
        String slug = fetchSlug(loginResponse, catalogId);
        mockMvc.perform(put("/api/v1/catalogs/{id}/whatsapp", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateWhatsappRequest("11987654321"))));
        mockMvc.perform(post("/api/v1/catalogs/{id}/whatsapp/verify", catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));

        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("5511987654321"));
    }

    @Test
    void nonexistentSlugReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/public/catalogs/{slug}", "slug-que-nao-existe"))
                .andExpect(status().isNotFound());
    }

    private String fetchSlug(LoginResponse loginResponse, String catalogId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/catalogs/{id}", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, CatalogResponse.class).slug();
    }

    private String createProduct(
            LoginResponse loginResponse, String catalogId, String name, String assetId, String categoryId)
            throws Exception {
        return createProduct(loginResponse, catalogId, name, assetId, categoryId, null, null);
    }

    private String createProduct(
            LoginResponse loginResponse,
            String catalogId,
            String name,
            String assetId,
            String categoryId,
            String sku,
            String description)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateProductRequest(
                                name,
                                sku,
                                description,
                                UUID.fromString(assetId),
                                categoryId != null ? UUID.fromString(categoryId) : null))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, ProductResponse.class).id().toString();
    }

    private void makeVisible(LoginResponse loginResponse, String catalogId, String productId) throws Exception {
        patchProduct(loginResponse, catalogId, productId, "{\"isVisible\": true, \"isOrderable\": true}");
    }

    private void patchProduct(LoginResponse loginResponse, String catalogId, String productId, String json) throws Exception {
        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
