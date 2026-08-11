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
@DisplayName("API - Backend production readiness")
class ProductionHealthApiIT extends BaseApiIntegrationTest {

    @MockitoSpyBean
    private DataSource dataSource;

    @Test
    @DisplayName("REQ-OPS-011/015 - ready unauthenticated backend health -> non-cacheable minimal UP")
    void readyHealthShouldReturnOnlyUpStatusWithoutAuthentication() {
        ExtractableResponse<Response> response = jsonRequest()
                .get("/health")
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
                .post("/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPS-011 - HEAD health request without credentials -> 405")
    void headHealthRequestShouldReturnMethodNotAllowedWithoutAuthentication() {
        assertMethodNotAllowedResponse(jsonRequest()
                .head("/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPS-011 - OPTIONS health request without credentials -> 405")
    void optionsHealthRequestShouldReturnMethodNotAllowedWithoutAuthentication() {
        assertMethodNotAllowedResponse(jsonRequest()
                .options("/health")
                .then()
                .extract());
    }

    @Test
    @DisplayName("REQ-OPENAPI-002/013 - readiness routes -> excluded from the OpenAPI Paths Object")
    void openApiShouldExcludeHealthRoutes() {
        Map<String, Object> contract = jsonRequest()
                .get("/openapi.json")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        Map<String, Object> paths = object(contract, "paths");
        assertThat(paths).doesNotContainKeys("/health", "/api/health");
    }

    @Test
    @DisplayName("REQ-OPENAPI-013 - OpenAPI server and paths -> compose the public prefix exactly once")
    void openApiShouldComposeEveryApplicationPathWithOnePublicPrefix() {
        Map<String, Object> contract = jsonRequest()
                .get("/openapi.json")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(objects(contract, "servers"))
                .extracting(server -> server.get("url"))
                .containsExactly("/api");
        assertThat(object(contract, "paths").keySet())
                .allSatisfy(path -> assertThat(path).doesNotMatch("^/api(?:/|$).*$"));
    }

    @Test
    @DisplayName("REQ-OPS-011/ADR-0028 - unavailable required database -> non-cacheable minimal DOWN")
    void unavailableDatabaseShouldReturnOnlyDownStatus() {
        withDatabaseConnectionUnavailable(() -> {
            ExtractableResponse<Response> response = jsonRequest()
                    .get("/health")
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
    private List<Map<String, Object>> objects(Map<String, Object> source, String property) {
        return (List<Map<String, Object>>) source.get(property);
    }

}
