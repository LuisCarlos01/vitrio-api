package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Prova o critério de aceite pendente da issue #7 (spec 004, US1 cenário 3): excluir uma
 * {@code Category} desvincula (não apaga) os produtos que a referenciavam, via
 * {@code ON DELETE SET NULL} na FK {@code products.category_id} (migration V9).
 */
class CategoryDeletionUnlinksProductTest extends AbstractProductIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    private static final String BUCKET_NAME = "vitrio-test-bucket-category-unlink";

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
    void deletingCategoryUnlinksProductInsteadOfDeletingIt() throws Exception {
        LoginResponse loginResponse = registerAndLogin("category-unlink-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Unlink 1");
        String assetId = createAssetAndGetId(loginResponse, catalogId);
        String categoryId = createCategoryForCatalog(loginResponse, catalogId, "Categoria a Excluir");

        String productBody = mockMvc.perform(post("/api/v1/catalogs/{catalogId}/products", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateProductRequest(
                                "Produto com Categoria", null, null, UUID.fromString(assetId), UUID.fromString(categoryId)))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String productId = jsonMapper.readValue(productBody, ProductResponse.class).id().toString();

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/categories/{id}", catalogId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNoContent());

        // Sem isso, a instância de Product já carregada no persistence context (na criação,
        // acima) seria devolvida do cache de 1º nível pela query do GET a seguir, mascarando
        // o efeito do ON DELETE SET NULL — que é um efeito do banco, não algo que o Hibernate
        // rastreia via a entidade Category. Em produção isso não acontece: cada request HTTP
        // tem sua própria transação/EntityManager, então o GET sempre lê do banco de novo.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/products/{id}", catalogId, productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }
}
