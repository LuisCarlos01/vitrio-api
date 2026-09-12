package dev.vitrio.api.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code DELETE .../categories/{id}} (spec 004, US1). Ver
 * {@link AbstractCategoryIntegrationTest} para o setup comum.
 */
class CategoryControllerDeleteTest extends AbstractCategoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void deletesCategoryAndRemovesItFromListing() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-category-1@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Delete 1");
        String categoryId = createCategoryAndGetId(loginResponse, catalogId, "Categoria a Excluir");

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/categories/{id}", catalogId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingCategoryFromAnotherCatalogIsNotFound() throws Exception {
        LoginResponse loginResponse = registerAndLogin("delete-category-3@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(loginResponse, "Catalogo Delete A");
        String catalogB = createCatalogAndGetId(loginResponse, "Catalogo Delete B");
        String categoryOfA = createCategoryAndGetId(loginResponse, catalogA, "Categoria de A");

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/categories/{id}", catalogB, categoryOfA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingCategoryOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("delete-category-2-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Delete 2");
        String categoryId = createCategoryAndGetId(owner, catalogId, "Categoria");
        LoginResponse intruder = registerAndLogin("delete-category-2-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(delete("/api/v1/catalogs/{catalogId}/categories/{id}", catalogId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken()))
                .andExpect(status().isNotFound());
    }
}
