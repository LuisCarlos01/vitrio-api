package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.category.AbstractCategoryIntegrationTest;
import dev.vitrio.api.category.CategoryResponse;
import dev.vitrio.api.category.CreateCategoryRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Base comum dos testes de integração de {@code product}: acrescenta à base de category o atalho
 * de criar uma categoria já autenticada ({@code createAssetAndGetId} mora em
 * {@link dev.vitrio.api.catalog.AbstractCatalogIntegrationTest}, herdado por toda a cadeia).
 */
public abstract class AbstractProductIntegrationTest extends AbstractCategoryIntegrationTest {

    protected String createCategoryForCatalog(LoginResponse loginResponse, String catalogId, String name) throws Exception {
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
