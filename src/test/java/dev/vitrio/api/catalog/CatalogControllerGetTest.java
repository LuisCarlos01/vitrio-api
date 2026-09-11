package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import dev.vitrio.api.auth.LoginResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code GET /api/v1/catalogs/{id}} (spec 001, US4 —
 * isolamento entre revendedoras, ADR-0003). Ver {@link AbstractAuthIntegrationTest}.
 */
class CatalogControllerGetTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void ownerCanReadOwnCatalogDetail() throws Exception {
        LoginResponse owner = registerAndLogin("get-catalog-owner@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Proprio");

        mockMvc.perform(get("/api/v1/catalogs/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catalogo Proprio"));
    }

    @Test
    void catalogOfAnotherResellerReturnsGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("get-catalog-owner-2@example.com", "Str0ngP@ssw0rd!");
        LoginResponse other = registerAndLogin("get-catalog-intruder@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo De Outra Pessoa");

        mockMvc.perform(get("/api/v1/catalogs/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    @Test
    void nonExistentIdReturnsSameGenericNotFoundAsForeignCatalog() throws Exception {
        LoginResponse loginResponse = registerAndLogin("get-catalog-nonexistent@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/catalogs/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private String createCatalogAndGetId(LoginResponse loginResponse, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCatalogRequest(name))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, CatalogResponse.class).id().toString();
    }
}
