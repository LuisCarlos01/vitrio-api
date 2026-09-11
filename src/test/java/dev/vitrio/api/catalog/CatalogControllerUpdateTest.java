package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Teste de integração ponta a ponta de {@code PATCH /api/v1/catalogs/{id}} (spec 002, US1). Ver
 * {@link AbstractCatalogIntegrationTest}.
 */
class CatalogControllerUpdateTest extends AbstractCatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void catalogWithoutCustomColorsReturnsApplicationDefaults() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-defaults@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Sem Cor");

        performUpdate(owner, id, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColorHex").value("#6D28D9"))
                .andExpect(jsonPath("$.buttonColorHex").value("#059669"));
    }

    @Test
    void updatingOnlyNameLeavesSlugAndOtherFieldsUntouched() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-name@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Original");

        performUpdate(owner, id, "{\"name\":\"Catálogo - Avon\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catálogo - Avon"))
                .andExpect(jsonPath("$.slug").value("catalogo-original"));
    }

    @Test
    void acceptsAnyValidHexColorNotJustAFixedPalette() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-freecolor@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Colorido");

        performUpdate(owner, id, "{\"primaryColorHex\":\"#ABCDEF\",\"buttonColorHex\":\"#123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColorHex").value("#ABCDEF"))
                .andExpect(jsonPath("$.buttonColorHex").value("#123456"));
    }

    @Test
    void rejectsColorNotMatchingHexFormatWithoutChangingAnyField() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-badcolor@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Cor Invalida");

        performUpdate(owner, id, "{\"primaryColorHex\":\"red\"}").andExpect(status().isBadRequest());

        // Confirma que a rejeição não deixou o catálogo em estado parcialmente atualizado.
        mockMvc.perform(get("/api/v1/catalogs/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catalogo Cor Invalida"))
                .andExpect(jsonPath("$.primaryColorHex").value("#6D28D9"))
                .andExpect(jsonPath("$.buttonColorHex").value("#059669"));
    }

    @Test
    void savesInstagramHandleAsFreeText() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-instagram@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Instagram");

        performUpdate(owner, id, "{\"instagramHandle\":\"minha_loja_sem_arroba\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramHandle").value("minha_loja_sem_arroba"));
    }

    @Test
    void updatingCatalogOfAnotherResellerReturnsGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-owner@example.com", "Str0ngP@ssw0rd!");
        LoginResponse intruder = registerAndLogin("update-catalog-intruder@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Alheio");

        performUpdate(intruder, id, "{\"name\":\"Tentativa De Invasao\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    @Test
    void updatingNonExistentCatalogReturnsSameGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("update-catalog-nonexistent@example.com", "Str0ngP@ssw0rd!");

        performUpdate(owner, UUID.randomUUID().toString(), "{\"name\":\"Nao Existe\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    private org.springframework.test.web.servlet.ResultActions performUpdate(
            LoginResponse loginResponse, String catalogId, String jsonBody) throws Exception {
        return mockMvc.perform(patch("/api/v1/catalogs/" + catalogId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
