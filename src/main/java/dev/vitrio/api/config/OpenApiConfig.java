package dev.vitrio.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI exportado por {@code springdoc-openapi} ({@code /v3/api-docs},
 * {@code /swagger-ui.html}) — sem isto, o título/versão ficam no default genérico "OpenAPI
 * definition"/"v0", inútil como contrato consumido pelo {@code vitrio-web} (ver
 * {@code docs/api/openapi.yaml}, exportado a partir deste endpoint).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vitrioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vitrio API")
                        .version("v1")
                        .description("Backend do Vitrio — catálogo digital multi-tenant para revendedoras."));
    }
}
