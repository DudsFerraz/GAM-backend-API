package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Public production health")
class ProductionHealthApiIT extends BaseApiIntegrationTest {

    @MockitoSpyBean
    private DataSource dataSource;

    @Test
    @DisplayName("REQ-OPS-011/ADR-0028 - ready unauthenticated public health -> non-cacheable minimal UP")
    void readyHealthShouldReturnOnlyUpStatusWithoutAuthentication() {
        ExtractableResponse<Response> response = jsonRequest()
                .get("/api/health")
                .then()
                .statusCode(200)
                .extract();

        assertThat(response.header("Content-Type")).startsWith("application/json");
        assertThat(response.header("Cache-Control")).contains("no-store");
        assertThat(response.jsonPath().getMap("$"))
                .containsOnlyKeys("status")
                .containsEntry("status", "UP");
    }

    @Test
    @DisplayName("REQ-OPS-011 - non-GET health request without credentials -> 405")
    void nonGetHealthRequestShouldReturnMethodNotAllowedWithoutAuthentication() {
        assertMethodNotAllowedResponse(jsonRequest()
                .post("/api/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPS-011 - HEAD health request without credentials -> 405")
    void headHealthRequestShouldReturnMethodNotAllowedWithoutAuthentication() {
        assertMethodNotAllowedResponse(jsonRequest()
                .head("/api/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPS-011 - OPTIONS health request without credentials -> 405")
    void optionsHealthRequestShouldReturnMethodNotAllowedWithoutAuthentication() {
        assertMethodNotAllowedResponse(jsonRequest()
                .options("/api/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPS-011/REQ-OPENAPI-003/005/012 - public /api/health operation -> UP and DOWN JSON contract")
    void openApiShouldDocumentThePublicHealthContract() {
        Map<String, Object> contract = jsonRequest()
                .get("/api/openapi.json")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        Map<String, Object> paths = object(contract, "paths");
        assertThat(paths).containsKey("/api/health");

        Map<String, Object> healthPath = object(paths, "/api/health");
        Map<String, Object> operation = object(healthPath, "get");
        assertThat(operation)
                .containsEntry("security", List.of())
                .containsKey("responses");

        Map<String, Object> responses = object(operation, "responses");
        assertThat(responses).containsOnlyKeys("200", "503");
        assertHealthResponse(contract, responses, "200", "UP");
        assertHealthResponse(contract, responses, "503", "DOWN");
    }

    @Test
    @DisplayName("REQ-OPENAPI-003 - public health operation -> stable id and semantic Health tag")
    void openApiShouldDocumentPublicHealthOperationMetadata() {
        Map<String, Object> contract = jsonRequest()
                .get("/api/openapi.json")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/api/health"),
                "get"
        );

        assertThat(operation)
                .containsEntry("operationId", "getProductionHealth")
                .containsEntry("tags", List.of("Health"));
    }

    @Test
    @DisplayName("REQ-OPS-011/ADR-0028 - unavailable required database -> non-cacheable minimal DOWN")
    void unavailableDatabaseShouldReturnOnlyDownStatus() {
        withDatabaseConnectionUnavailable(() -> {
            ExtractableResponse<Response> response = jsonRequest()
                    .get("/api/health")
                    .then()
                    .statusCode(503)
                    .extract();

            assertThat(response.header("Content-Type")).startsWith("application/json");
            assertThat(response.header("Cache-Control")).contains("no-store");
            assertThat(response.jsonPath().getMap("$"))
                    .containsOnlyKeys("status")
                    .containsEntry("status", "DOWN");
        });
    }

    private void assertMethodNotAllowedResponse(ExtractableResponse<Response> response) {
        assertThat(response.statusCode()).isEqualTo(405);
    }

    private void assertHealthResponse(
            Map<String, Object> contract,
            Map<String, Object> responses,
            String statusCode,
            String expectedStatus
    ) {
        Map<String, Object> response = object(responses, statusCode);
        assertThat(response).isNotNull();
        Map<String, Object> headers = object(response, "headers");
        assertThat(headers).containsKey("Cache-Control");
        Map<String, Object> cacheControl = object(headers, "Cache-Control");
        assertThat(object(cacheControl, "schema"))
                .containsEntry("type", "string")
                .containsEntry("example", "no-store");

        Map<String, Object> content = object(response, "content");
        assertThat(content).containsOnlyKeys("application/json");

        Map<String, Object> mediaType = object(content, "application/json");
        Map<String, Object> schema = resolveSchema(contract, object(mediaType, "schema"));
        assertThat(schema).containsEntry("type", "object");
        assertThat(strings(schema, "required")).containsExactly("status");

        Map<String, Object> properties = object(schema, "properties");
        assertThat(properties).containsOnlyKeys("status");
        Map<String, Object> statusSchema = resolveSchema(contract, object(properties, "status"));
        assertThat(statusSchema)
                .as("health status schema for %s", statusCode)
                .containsEntry("type", "string");
        assertThat(statusSchema.get("enum")).as("health status enum for %s", statusCode)
                .asList()
                .containsExactly(expectedStatus);

        assertThat(jsonExample(mediaType))
                .containsOnlyKeys("status")
                .containsEntry("status", expectedStatus);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonExample(Map<String, Object> mediaType) {
        return (Map<String, Object>) mediaType.get("example");
    }

    private void withDatabaseConnectionUnavailable(Runnable assertion) {
        try {
            doThrow(new SQLException("test database is unavailable"))
                    .when(dataSource)
                    .getConnection();
            assertion.run();
        } catch (SQLException exception) {
            throw new AssertionError("Could not stub the unavailable database dependency", exception);
        } finally {
            reset(dataSource);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> source, String property) {
        return (Map<String, Object>) source.get(property);
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Map<String, Object> source, String property) {
        return (List<String>) source.get(property);
    }

    private Map<String, Object> resolveSchema(Map<String, Object> contract, Map<String, Object> schema) {
        assertThat(schema).isNotNull();
        Object reference = schema.get("$ref");
        if (!(reference instanceof String referenceValue)
                || !referenceValue.startsWith("#/components/schemas/")) {
            return schema;
        }

        Map<String, Object> components = object(contract, "components");
        Map<String, Object> schemas = object(components, "schemas");
        return object(schemas, referenceValue.substring("#/components/schemas/".length()));
    }

}
