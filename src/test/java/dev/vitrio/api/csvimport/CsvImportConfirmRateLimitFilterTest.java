package dev.vitrio.api.csvimport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.product.AbstractProductIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@link CsvImportConfirmRateLimitFilter} (spec 006, US2
 * cenário 9). Único teste da suíte que sobrescreve {@code vitrio.rate-limit.csv-import-confirm-*}
 * de volta para o valor real de produção (10/3600s) — o profile {@code test} usa uma capacidade
 * generosa por padrão (`application-test.yml`), mesmo motivo de {@code LoginRateLimitFilterTest}/
 * {@code RegisterRateLimitFilterTest}.
 *
 * <p>O CSV enviado tem só cabeçalho (nenhuma linha) — o bucket é consumido pelo filtro antes do
 * controller rodar, independente do conteúdo do arquivo; usar um CSV vazio evita qualquer download
 * de imagem/upload no S3 nas 10 tentativas dentro do limite, mantendo o teste rápido e sem
 * depender de LocalStack.
 */
class CsvImportConfirmRateLimitFilterTest extends AbstractProductIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("vitrio.rate-limit.csv-import-confirm-capacity", () -> 10);
        registry.add("vitrio.rate-limit.csv-import-confirm-window-seconds", () -> 3600);
    }

    @Test
    void eleventhConfirmationInSameWindowFromSameResellerIsRateLimited() throws Exception {
        LoginResponse loginResponse = registerAndLogin("csv-confirm-rate-limit@example.com", "Str0ngP@ssw0rd!");
        String catalogId = createCatalogAndGetId(loginResponse, "Catalogo Rate Limit");

        for (int i = 0; i < 10; i++) {
            performConfirm(loginResponse, catalogId).andExpect(status().isOk());
        }

        performConfirm(loginResponse, catalogId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void bucketIsIsolatedPerReseller() throws Exception {
        LoginResponse first = registerAndLogin("csv-confirm-rate-limit-a@example.com", "Str0ngP@ssw0rd!");
        String firstCatalogId = createCatalogAndGetId(first, "Catalogo Rate Limit A");
        for (int i = 0; i < 10; i++) {
            performConfirm(first, firstCatalogId).andExpect(status().isOk());
        }

        LoginResponse second = registerAndLogin("csv-confirm-rate-limit-b@example.com", "Str0ngP@ssw0rd!");
        String secondCatalogId = createCatalogAndGetId(second, "Catalogo Rate Limit B");
        performConfirm(second, secondCatalogId).andExpect(status().isOk());
    }

    private ResultActions performConfirm(LoginResponse loginResponse, String catalogId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "produtos.csv", "text/csv", "nome,codigo,descricao,imagem\n".getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/products/import/confirm", catalogId)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
