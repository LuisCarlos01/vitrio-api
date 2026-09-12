package dev.vitrio.api.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.catalog.AbstractCatalogIntegrationTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Base comum dos testes de integração de {@code category}: acrescenta à base de catalog o
 * atalho de criar uma categoria já autenticada, reaproveitado por toda a suíte deste módulo.
 */
public abstract class AbstractCategoryIntegrationTest extends AbstractCatalogIntegrationTest {

    protected String createCategoryAndGetId(LoginResponse loginResponse, String catalogId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/catalogs/{catalogId}/categories", catalogId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateCategoryRequest(name))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, CategoryResponse.class).id().toString();
    }
}
