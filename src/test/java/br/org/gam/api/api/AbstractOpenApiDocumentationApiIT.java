package br.org.gam.api.api;

import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

abstract class AbstractOpenApiDocumentationApiIT extends BaseApiIntegrationTest {

    protected Map<String, Object> swaggerUiConfiguration() {
        return jsonRequest()
                .get("/api/openapi.json/swagger-config")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    protected ExtractableResponse<Response> openApiContract() {
        return jsonRequest()
                .get("/api/openapi.json")
                .then()
                .statusCode(200)
                .extract();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> object(Map<String, Object> source, String property) {
        return (Map<String, Object>) source.get(property);
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> objects(Map<String, Object> source, String property) {
        return (List<Map<String, Object>>) source.get(property);
    }

    @SuppressWarnings("unchecked")
    protected List<String> strings(Map<String, Object> source, String property) {
        return (List<String>) source.get(property);
    }
}
