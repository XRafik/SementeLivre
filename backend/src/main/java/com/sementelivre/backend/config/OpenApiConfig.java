package com.sementelivre.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documento OpenAPI (Swagger UI em /swagger-ui.html, JSON em /v3/api-docs).
 *
 * Os schemas saem dos DTOs reais que os controllers usam; não registre schema
 * escrito à mão aqui, porque ele substitui o gerado e fica desatualizado.
 * Os grupos vêm do @Tag de cada controller.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI sementeLivreOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("accessToken devolvido por POST /auth/login (sem o prefixo \"Bearer\").")))
                // Vale para todas as rotas; as públicas (catálogo, login, cadastro) ignoram o token
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                        .title("Semente Livre - API")
                        .description("""
                                API do banco comunitário de sementes do IF Sudeste MG, Campus Rio Pomba.

                                Para testar rotas protegidas: faça POST /auth/login, copie o accessToken \
                                e cole em "Authorize". Rotas de administração exigem a conta de admin.""")
                        .version("0.0.1")
                        .contact(new Contact()
                                .name("Equipe Semente Livre")
                                .url("https://github.com/wellmor/SementeLivre")));
    }
}
