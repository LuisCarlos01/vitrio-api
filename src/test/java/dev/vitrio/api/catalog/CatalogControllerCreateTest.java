package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import dev.vitrio.api.auth.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/catalogs} (spec 001, US2). Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class CatalogControllerCreateTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void createsCatalogWithKebabCaseSlugDerivedFromName() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-catalog-1@example.com", "Str0ngP@ssw0rd!");

        performCreate(loginResponse, "Catálogo - Boticário")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Catálogo - Boticário"))
                .andExpect(jsonPath("$.slug").value("catalogo-boticario"))
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.createdAt", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void slugCollisionGetsNumericSuffix() throws Exception {
        LoginResponse first = registerAndLogin("create-catalog-2@example.com", "Str0ngP@ssw0rd!");
        LoginResponse second = registerAndLogin("create-catalog-3@example.com", "Str0ngP@ssw0rd!");

        performCreate(first, "Semijoias").andExpect(jsonPath("$.slug").value("semijoias"));
        performCreate(second, "Semijoias").andExpect(jsonPath("$.slug").value("semijoias-2"));
        performCreate(second, "Semijoias").andExpect(jsonPath("$.slug").value("semijoias-3"));
    }

    @Test
    void sameResellerCanCreateMultipleCatalogsWithNoLimit() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-catalog-4@example.com", "Str0ngP@ssw0rd!");

        for (int i = 0; i < 6; i++) {
            performCreate(loginResponse, "Catalogo " + i).andExpect(status().isCreated());
        }
    }

    @Test
    void blankNameIsRejected() throws Exception {
        LoginResponse loginResponse = registerAndLogin("create-catalog-5@example.com", "Str0ngP@ssw0rd!");

        performCreate(loginResponse, " ").andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/catalogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCatalogRequest("Catalogo"))))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions performCreate(LoginResponse loginResponse, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/catalogs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new CreateCatalogRequest(name))));
    }
}
