package dev.vitrio.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta do {@link RegisterRateLimitFilter} (Vitrio spec 001). Único
 * teste da suíte que sobrescreve {@code vitrio.rate-limit.register-*} de volta para o valor real
 * de produção (10/60s — mesma janela do login, o dobro da capacidade) — o profile {@code test}
 * usa uma capacidade generosa por padrão (`application-test.yml`) para não afetar outras classes
 * que registram usuários várias vezes sem a intenção de testar rate limiting. Cada método usa um
 * IP exclusivo, e cada tentativa dentro do método usa um e-mail exclusivo (só o bucket de IP
 * existe aqui, mas e-mail repetido seria rejeitado por {@link EmailAlreadyRegisteredException}
 * antes de importar para este teste).
 */
class RegisterRateLimitFilterTest extends AbstractAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("vitrio.rate-limit.register-capacity", () -> 10);
        registry.add("vitrio.rate-limit.register-window-seconds", () -> 60);
    }

    @Test
    void eleventhAttemptInSameWindowFromSameIpIsRateLimited() throws Exception {
        String ip = "10.1.0.1";

        for (int i = 0; i < 10; i++) {
            performRegister(ip, "register-rate-limit-" + i + "@example.com").andExpect(status().isCreated());
        }

        performRegister(ip, "register-rate-limit-overflow@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void attemptsWithinLimitAreNotRateLimited() throws Exception {
        String ip = "10.1.0.2";

        for (int i = 0; i < 3; i++) {
            performRegister(ip, "register-within-limit-" + i + "@example.com").andExpect(status().isCreated());
        }
    }

    @Test
    void bucketIsIsolatedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            performRegister("10.1.1." + i, "register-different-ip-" + i + "@example.com")
                    .andExpect(status().isCreated());
        }

        performRegister("10.1.1.99", "register-different-ip-99@example.com").andExpect(status().isCreated());
    }

    private ResultActions performRegister(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RegisterRequest(email, "Str0ngP@ssw0rd!"))));
    }
}
