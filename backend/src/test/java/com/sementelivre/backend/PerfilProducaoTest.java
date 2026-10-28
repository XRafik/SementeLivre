package com.sementelivre.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** O que o application-prod.yml precisa garantir. */
class PerfilProducaoTest {

    private static PropertySource<?> prod() throws Exception {
        List<PropertySource<?>> fontes = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));
        return fontes.get(0);
    }

    // O Swagger publicava o mapa completo da API (#203)
    @Test
    void producaoNaoDevePublicarODocumentoDaApi() throws Exception {
        PropertySource<?> prod = prod();

        assertEquals("false", String.valueOf(prod.getProperty("springdoc.api-docs.enabled")));
        assertEquals("false", String.valueOf(prod.getProperty("springdoc.swagger-ui.enabled")));
    }
}
