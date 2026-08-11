package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - public prefix composition")
class PublicApiPrefixRoutingApiIT extends AbstractOpenApiDocumentationApiIT {

    @Test
    @DisplayName("REQ-OPENAPI-013 - backend-local documentation -> /docs and /openapi.json")
    void backendShouldExposeDocumentationAtRelativePaths() {
        assertHtmlEndpointAvailable("/docs");
        assertThat(openApiContract().body()).containsEntry("openapi", "3.1.0");
    }

    @Test
    @DisplayName("REQ-OPENAPI-002/013 - generated contract -> one /api server and only relative application paths")
    void generatedContractShouldComposeThePublicPrefixExactlyOnceAndExcludeHealth() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(objects(contract, "servers"))
                .extracting(server -> server.get("url"))
                .containsExactly("/api");
        assertThat(paths.keySet())
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).doesNotMatch("^/api(?:/|$).*$"));
        assertThat(paths).doesNotContainKeys("/health", "/api/health");
    }
}
