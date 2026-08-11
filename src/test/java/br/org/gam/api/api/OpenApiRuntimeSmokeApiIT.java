package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI runtime smoke")
class OpenApiRuntimeSmokeApiIT extends BaseApiIntegrationTest {

    @Test
    @DisplayName("REQ-OPENAPI-001/013 - real backend -> live OpenAPI contract is available at /openapi.json")
    void realApplicationShouldExposeTheLiveOpenApiContract() {
        Map<String, Object> contract = jsonRequest()
                .get("/openapi.json")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(contract)
                .containsEntry("openapi", "3.1.0")
                .containsKey("paths");
    }
}
