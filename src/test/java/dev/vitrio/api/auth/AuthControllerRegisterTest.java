package dev.vitrio.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.user.User;
import dev.vitrio.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/auth/register}. Ver
 * {@link AbstractAuthIntegrationTest} para o setup comum (contexto Spring + Testcontainers).
 */
class AuthControllerRegisterTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void registersUserWithDefaultRoleAndAuthenticatesImmediately() throws Exception {
        RegisterRequest request = new RegisterRequest("jane.doe@example.com", "Str0ngP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                // Registro também autentica (spec 001, US1 cenário 1): mesmo shape de token do
                // login, inclusive o cookie httpOnly do Refresh token.
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));

        Optional<User> persisted = userRepository.findByEmail("jane.doe@example.com");
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getRoles()).extracting("name").containsExactly("RESELLER");
        assertThat(persisted.get().getPasswordHash()).isNotEqualTo("Str0ngP@ssw0rd!");
        assertThat(persisted.get().isEmailVerified()).isFalse();
    }
}
