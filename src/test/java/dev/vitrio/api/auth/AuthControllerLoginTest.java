package dev.vitrio.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.user.User;
import dev.vitrio.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code POST /api/v1/auth/login}, cobrindo apenas o caminho
 * feliz — credenciais inválidas e validação de entrada são cobertas por outras classes.
 * Ver {@link AbstractAuthIntegrationTest} para o setup comum.
 */
class AuthControllerLoginTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void logsInWithValidCredentialsAndIssuesTokenPair() throws Exception {
        String email = "jane.doe@example.com";
        String password = "Str0ngP@ssw0rd!";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest(email, password))));

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponse response = jsonMapper.readValue(responseBody, LoginResponse.class);

        User persistedUser = userRepository.findByEmail(email).orElseThrow();
        Claims claims = jwtService.parseClaims(response.accessToken());
        assertThat(claims.getSubject()).isEqualTo(persistedUser.getId().toString());
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactly("RESELLER");

        // Duas linhas existem neste ponto: uma do login (asserida abaixo) e uma do registro
        // automático que o precedeu (spec 001, login automático pós-registro).
        String expectedHash = sha256Hex(response.refreshToken());
        List<RefreshToken> persisted = refreshTokenRepository.findAll();
        assertThat(persisted).hasSize(2);
        RefreshToken storedToken =
                persisted.stream().filter(token -> token.getTokenHash().equals(expectedHash)).findFirst().orElseThrow();
        assertThat(storedToken.getTokenHash()).isNotEqualTo(response.refreshToken());
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes()));
    }
}
