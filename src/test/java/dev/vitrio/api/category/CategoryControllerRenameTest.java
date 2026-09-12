package dev.vitrio.api.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Teste de integração ponta a ponta de {@code PATCH .../categories/{id}} (spec 004, US1). Ver
 * {@link AbstractCategoryIntegrationTest} para o setup comum.
 */
class CategoryControllerRenameTest extends AbstractCategoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void renamesCategory() throws Exception {
        LoginResponse loginResponse = registerAndLogin("rename-category-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Rename 1");
        String categoryId = createCategoryAndGetId(loginResponse, catalogId, "Nome Antigo");

        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/categories/{id}", catalogId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UpdateCategoryRequest("Nome Novo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Novo"));
    }

    @Test
    void renamingCategoryFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("rename-category-2@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Rename A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Rename B");
        String categoryOfA = createCategoryAndGetId(loginResponse, catalogA, "Categoria de A");

        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/categories/{id}", catalogB, categoryOfA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UpdateCategoryRequest("Nome Novo"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void renamingCategoryOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("rename-category-3-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Rename 3");
        String categoryId = createCategoryAndGetId(owner, catalogId, "Categoria");
        LoginResponse intruder = registerAndLogin("rename-category-3-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(patch("/api/v1/catalogs/{catalogId}/categories/{id}", catalogId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UpdateCategoryRequest("Nome Novo"))))
                .andExpect(status().isNotFound());
    }
}
