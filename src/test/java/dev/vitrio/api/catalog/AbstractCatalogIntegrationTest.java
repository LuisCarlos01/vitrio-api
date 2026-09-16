package dev.vitrio.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.vitrio.api.asset.AssetResponse;
import dev.vitrio.api.auth.AbstractAuthIntegrationTest;
import dev.vitrio.api.auth.LoginResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Base comum dos testes de integração de {@code catalog}: acrescenta à base de auth os atalhos de
 * criar um catálogo e um asset já autenticados, reaproveitados por toda a suíte deste módulo e
 * pelas suítes de {@code product}/{@code category} (spec 007 subiu {@code createAssetAndGetId}
 * pra cá — antes só existia em {@code AbstractProductIntegrationTest}, mais fundo na hierarquia
 * do que os testes de logo do catálogo precisam).
 */
public abstract class AbstractCatalogIntegrationTest extends AbstractAuthIntegrationTest {

    private static final byte[] JPEG_BYTES =
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0};

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

    /** Envia uma imagem JPEG mínima válida pro catálogo e devolve o id do {@code Asset} gerado. */
    protected String createAssetAndGetId(LoginResponse loginResponse, String catalogId) throws Exception {
        return createAsset(loginResponse, catalogId).id().toString();
    }

    /** Mesmo que {@link #createAssetAndGetId}, para testes que também precisam do {@code publicUrl}. */
    protected AssetResponse createAsset(LoginResponse loginResponse, String catalogId) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/catalogs/{catalogId}/assets", catalogId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, AssetResponse.class);
    }
}
