package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vitrio.api.auth.LoginResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Teste de integração ponta a ponta de {@code PUT /api/v1/catalogs/{id}/whatsapp} e
 * {@code POST /api/v1/catalogs/{id}/whatsapp/verify} (spec 002, US2 e US3). Ver
 * {@link AbstractCatalogIntegrationTest}.
 */
class CatalogControllerWhatsappTest extends AbstractCatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void normalizesBrazilianNumberBeforeSaving() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-normalize@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp");

        performUpdateWhatsapp(owner, id, "(11) 91234-5678")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("5511912345678"))
                .andExpect(jsonPath("$.whatsappVerificationStatus").value("UNVERIFIED"));
    }

    @Test
    void rejectsNumberThatDoesNotLookLikeAValidBrazilianWhatsapp() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-invalid@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp Invalido");

        performUpdateWhatsapp(owner, id, "123").andExpect(status().isBadRequest());
    }

    @Test
    void savingNewNumberResetsExistingVerification() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-reset@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp Verificado");
        performUpdateWhatsapp(owner, id, "(11) 91234-5678");
        performVerify(owner, id).andExpect(jsonPath("$.whatsappVerificationStatus").value("VERIFIED"));

        performUpdateWhatsapp(owner, id, "(21) 98888-7777")
                .andExpect(jsonPath("$.whatsappVerificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.whatsappVerifiedAt").doesNotExist());
    }

    @Test
    void savingTheIdenticalNumberAgainAlsoResetsVerification() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-same-number@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp Mesmo Numero");
        performUpdateWhatsapp(owner, id, "(11) 91234-5678");
        performVerify(owner, id).andExpect(jsonPath("$.whatsappVerificationStatus").value("VERIFIED"));

        performUpdateWhatsapp(owner, id, "(11) 91234-5678")
                .andExpect(jsonPath("$.whatsappVerificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.whatsappVerifiedAt").doesNotExist());
    }

    @Test
    void verifyingWithoutAnyNumberConfiguredIsRejected() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-no-number@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Sem WhatsApp");

        performVerify(owner, id).andExpect(status().isConflict());
    }

    @Test
    void verifyingSucceedsAndStampsVerifiedAt() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-verify-ok@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp OK");
        performUpdateWhatsapp(owner, id, "(11) 91234-5678");

        performVerify(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappVerificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.whatsappVerifiedAt", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void updatingWhatsappOfAnotherResellerCatalogReturnsGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-owner@example.com", "Str0ngP@ssw0rd!");
        LoginResponse intruder = registerAndLogin("whatsapp-intruder@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo WhatsApp Alheio");

        performUpdateWhatsapp(intruder, id, "(11) 91234-5678")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    @Test
    void verifyingWhatsappOfAnotherResellerCatalogReturnsGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-verify-owner@example.com", "Str0ngP@ssw0rd!");
        LoginResponse intruder = registerAndLogin("whatsapp-verify-intruder@example.com", "Str0ngP@ssw0rd!");
        String id = createCatalogAndGetId(owner, "Catalogo Verify Alheio");
        performUpdateWhatsapp(owner, id, "(11) 91234-5678");

        performVerify(intruder, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Catalog not found"));
    }

    @Test
    void nonExistentCatalogIdReturnsSameGenericNotFound() throws Exception {
        LoginResponse owner = registerAndLogin("whatsapp-nonexistent@example.com", "Str0ngP@ssw0rd!");

        performUpdateWhatsapp(owner, UUID.randomUUID().toString(), "(11) 91234-5678")
                .andExpect(status().isNotFound());
    }

    private ResultActions performUpdateWhatsapp(LoginResponse loginResponse, String catalogId, String number)
            throws Exception {
        return mockMvc.perform(put("/api/v1/catalogs/" + catalogId + "/whatsapp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UpdateWhatsappRequest(number))));
    }

    private ResultActions performVerify(LoginResponse loginResponse, String catalogId) throws Exception {
        return mockMvc.perform(post("/api/v1/catalogs/" + catalogId + "/whatsapp/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()));
    }
}
