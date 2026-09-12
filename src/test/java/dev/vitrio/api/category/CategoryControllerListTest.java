package dev.vitrio.api.category;

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
 * Teste de integração ponta a ponta de {@code GET /api/v1/catalogs/{catalogId}/categories}
 * (spec 004, US1). Ver {@link AbstractCategoryIntegrationTest} para o setup comum.
 */
class CategoryControllerListTest extends AbstractCategoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void listsOnlyCategoriesOfTheGivenCatalog() throws Exception {
        LoginResponse owner = registerAndLogin("list-categories-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogA = createCatalogAndGetId(owner, "Catalogo A");
        String catalogB = createCatalogAndGetId(owner, "Catalogo B");

        createCategoryAndGetId(owner, catalogA, "Categoria A1");
        createCategoryAndGetId(owner, catalogA, "Categoria A2");
        createCategoryAndGetId(owner, catalogB, "Categoria B1");

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/categories", catalogA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[*].name", org.hamcrest.Matchers.containsInAnyOrder("Categoria A1", "Categoria A2")));
    }

    @Test
    void listingCategoriesOfCatalogOwnedByAnotherResellerIsNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("list-categories-2-owner@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(owner, "Catalogo Isolado");
        LoginResponse intruder = registerAndLogin("list-categories-2-intruder@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.accessToken()))
                .andExpect(status().isNotFound());
    }
}
