package dev.vitrio.api.catalog;

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
 * Teste de integração ponta a ponta de {@code GET /api/v1/catalogs} (spec 001, US3). Ver
 * {@link AbstractCatalogIntegrationTest} para o setup comum.
 */
class CatalogControllerListTest extends AbstractCatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void listsOnlyCatalogsOwnedByAuthenticatedReseller() throws Exception {
        LoginResponse owner = registerAndLogin("list-catalogs-owner@example.com", "Str0ngP@ssw0rd!");
        LoginResponse other = registerAndLogin("list-catalogs-other@example.com", "Str0ngP@ssw0rd!");

        createCatalog(owner, "Catalogo A");
        createCatalog(owner, "Catalogo B");
        createCatalog(other, "Catalogo De Outra Revendedora");

        mockMvc.perform(get("/api/v1/catalogs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.containsInAnyOrder("Catalogo A", "Catalogo B")));
    }

    @Test
    void listIsEmptyForResellerWithNoCatalogs() throws Exception {
        LoginResponse loginResponse = registerAndLogin("list-catalogs-empty@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/catalogs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs")).andExpect(status().isUnauthorized());
    }
}
