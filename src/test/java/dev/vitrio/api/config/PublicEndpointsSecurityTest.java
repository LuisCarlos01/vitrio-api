package dev.vitrio.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Cobre a exceção deliberada em {@code SecurityConfig}: sem liberar {@code /actuator/health} e o
 * Swagger UI explicitamente, ambos cairiam no {@code anyRequest().authenticated()} e responderiam
 * {@code 401} sem um Access token. Ver {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class PublicEndpointsSecurityTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void actuatorHealthIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void swaggerUiIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void apiDocsArePubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void apiDocsYamlVariantIsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }
}
