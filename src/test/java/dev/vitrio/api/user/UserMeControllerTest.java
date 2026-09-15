package dev.vitrio.api.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import dev.vitrio.api.auth.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code GET}/{@code PATCH /api/v1/users/me} (spec 008). Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class UserMeControllerTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void newAccountHasNullNameUntilSet() throws Exception {
        LoginResponse loginResponse = registerAndLogin("me-newaccount@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me-newaccount@example.com"))
                .andExpect(jsonPath("$.name").isEmpty())
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void settingNameMakesItAppearOnSubsequentGet() throws Exception {
        LoginResponse loginResponse = registerAndLogin("me-setname@example.com", "Str0ngP@ssw0rd!");

        performPatch(loginResponse, "{\"name\":\"Maria Revendedora\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Revendedora"));
    }

    @Test
    void omittingNameLeavesCurrentNameUnchanged() throws Exception {
        LoginResponse loginResponse = registerAndLogin("me-keepname@example.com", "Str0ngP@ssw0rd!");
        performPatch(loginResponse, "{\"name\":\"Maria Original\"}").andExpect(status().isOk());

        performPatch(loginResponse, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Original"));
    }

    @Test
    void emptyNameIsRejectedWithoutChangingCurrentName() throws Exception {
        LoginResponse loginResponse = registerAndLogin("me-emptyname@example.com", "Str0ngP@ssw0rd!");
        performPatch(loginResponse, "{\"name\":\"Maria Valida\"}").andExpect(status().isOk());

        performPatch(loginResponse, "{\"name\":\"\"}").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(jsonPath("$.name").value("Maria Valida"));
    }

    @Test
    void nameLongerThan255CharactersIsRejected() throws Exception {
        LoginResponse loginResponse = registerAndLogin("me-longname@example.com", "Str0ngP@ssw0rd!");

        performPatch(loginResponse, "{\"name\":\"" + "a".repeat(256) + "\"}").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions performPatch(LoginResponse loginResponse, String jsonBody)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
