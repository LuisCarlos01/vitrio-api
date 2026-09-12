package dev.vitrio.api.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.vitrio.api.asset.AssetResponse;
import dev.vitrio.api.auth.LoginResponse;
import dev.vitrio.api.category.AbstractCategoryIntegrationTest;
import dev.vitrio.api.category.CategoryResponse;
import dev.vitrio.api.category.CreateCategoryRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Base comum dos testes de integração de {@code product}: acrescenta à base de category os
 * atalhos de criar um asset e uma categoria já autenticados, reaproveitados por toda a suíte
 * deste módulo.
 */
public abstract class AbstractProductIntegrationTest extends AbstractCategoryIntegrationTest {

    private static final byte[] JPEG_BYTES =
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0};

    protected String createAssetAndGetId(LoginResponse loginResponse, String catalogId) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/assets", catalogId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, AssetResponse.class).id().toString();
    }

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
