package dev.vitrio.api.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/catalogs/{catalogId}/categories}
 * (spec 004, US1). Ver {@link AbstractCategoryIntegrationTest} para o setup comum.
 */
class CategoryControllerCreateTest extends AbstractCategoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void createsCategoryVinculatedToCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-category-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Categoria 1");

        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCategoryRequest("Semijoias"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Semijoias"))
                .andExpect(jsonPath("$.catalogId").value(catalogId))
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-category-2@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Categoria 2");

        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCategoryRequest(" "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingCategoryInCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("create-category-3-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Categoria 3");
        LoginResponse intruder = registerAndLogin("create-category-3-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCategoryRequest("Semijoias"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-category-4@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Categoria 4");

        mockMvc.perform(post("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCategoryRequest("Semijoias"))))
                .andExpect(status().isUnauthorized());
    }
}
