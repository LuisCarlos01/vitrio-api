package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import dev.vitrio.api.auth.LoginResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Base comum dos testes de integração de {@code catalog}: acrescenta à base de auth o
 * atalho de criar um catálogo já autenticado, reaproveitado por toda a suíte de controller
 * deste módulo.
 */
public abstract class AbstractCatalogIntegrationTest extends AbstractAuthIntegrationTest {

    /** Cria um catálogo via API e devolve o id gerado, pra testes que precisam de um catálogo existente. */
    protected String createCatalogAndGetId(LoginResponse loginResponse, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCatalogRequest(name))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, CatalogResponse.class).id().toString();
    }

    /** Mesmo que {@link #createCatalogAndGetId}, para testes que não precisam do id gerado. */
    protected void createCatalog(LoginResponse loginResponse, String name) throws Exception {
        createCatalogAndGetId(loginResponse, name);
    }
}
