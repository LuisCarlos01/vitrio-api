package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Teste de integração ponta a ponta de {@code GET .../products} e {@code GET .../products/{id}}
 * (spec 004, US4). Ver {@link AbstractProductIntegrationTest} para o setup comum.
 */
class ProductControllerReadTest extends AbstractProductIntegrationTest {

    private static final String BUCKET_NAME = "vitrio-test-bucket-product-read";

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
    void listsAllProductsOfTheCatalogRegardlessOfState() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Leitura 1");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        createProduct(loginResponse, catalogId, "Produto A", assetId);
        createProduct(loginResponse, catalogId, "Produto B", assetId);

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listingReturnsProductsRegardlessOfState() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-5@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Leitura 5");
        String assetId = createAssetAndGetId(loginResponse, catalogId);

        String defaultStateId = createProduct(loginResponse, catalogId, "Produto Padrao", assetId);
        String visibleOrderableId = createProduct(loginResponse, catalogId, "Produto Visivel", assetId);
        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, visibleOrderableId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isVisible\": true, \"isOrderable\": true}"));
        String inactiveId = createProduct(loginResponse, catalogId, "Produto Inativo", assetId);
        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, inactiveId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"));

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder(
                        defaultStateId, visibleOrderableId, inactiveId)));
    }

    @Test
    void listingProductsOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("read-product-2-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Leitura 2");
        LoginResponse intruder = registerAndLogin("read-product-2-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getsSingleProductByIdWithinCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-3@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Leitura 3");
        AssetResponse asset = createAsset(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Produto Unico", asset.id().toString());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Produto Unico"))
                .andExpect(jsonPath("$.imageUrl").value(asset.publicUrl()));
    }

    // Prova a resolução em lote na listagem (spec 011, US1 cenário 1): cada produto retorna a
    // imageUrl do seu próprio asset, não a de outro nem null por engano num agrupamento errado.
    @Test
    void listingResolvesImageUrlPerProduct() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-6@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Leitura 6");
        AssetResponse assetA = createAsset(loginResponse, catalogId);
        AssetResponse assetB = createAsset(loginResponse, catalogId);
        String productA = createProduct(loginResponse, catalogId, "Produto A", assetA.id().toString());
        String productB = createProduct(loginResponse, catalogId, "Produto B", assetB.id().toString());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + productA + "')].imageUrl").value(assetA.publicUrl()))
                .andExpect(jsonPath("$[?(@.id=='" + productB + "')].imageUrl").value(assetB.publicUrl()));
    }

    // Prova que a resposta reflete o asset atual, não o anterior, depois de um PATCH de imagem
    // (spec 011, US1 cenário 3).
    @Test
    void updatingImageAssetIdChangesTheResolvedImageUrl() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-7@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Leitura 7");
        AssetResponse originalAsset = createAsset(loginResponse, catalogId);
        AssetResponse newAsset = createAsset(loginResponse, catalogId);
        String productId = createProduct(loginResponse, catalogId, "Produto Trocado", originalAsset.id().toString());

        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageAssetId\": \"" + newAsset.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(newAsset.publicUrl()));
    }

    @Test
    void gettingProductFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("read-product-4@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Leitura 4A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Leitura 4B");
        String assetOfA = createAssetAndGetId(loginResponse, catalogA);
        String productOfA = createProduct(loginResponse, catalogA, "Produto de A", assetOfA);

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products/{id}", catalogB, productOfA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNotFound());
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
