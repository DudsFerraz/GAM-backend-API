package br.org.gam.api.api;

import br.org.gam.api.health.application.HealthReadiness;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - public health readiness contract")
class HealthApiIT extends BaseApiIntegrationTest {

    @MockitoBean
    private HealthReadiness healthReadiness;

    @Test
    @DisplayName("REQ-OPS-011 - unauthenticated GET health returns the exact ready body and no-store header")
    void unauthenticatedGetHealthShouldExposeOnlyReadyStatus() {
        when(healthReadiness.isReady()).thenReturn(true);

        ExtractableResponse<Response> response = jsonRequest()
                .get("/health")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).containsIgnoringCase("application/json");
        assertThat(response.asString()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("status").containsEntry("status", "UP");
        assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
    }

    @Test
    @DisplayName("REQ-OPS-011 - unavailable database readiness returns the exact down body without diagnostics")
    void databaseDownShouldReturnTheMinimalDownBody() {
        when(healthReadiness.isReady()).thenReturn(false);

        ExtractableResponse<Response> response = jsonRequest()
                .get("/health")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.contentType()).containsIgnoringCase("application/json");
        assertThat(response.asString()).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("status").containsEntry("status", "DOWN");
        assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        assertThat(response.asString()).doesNotContain("database", "postgres", "exception", "stack");
    }

    @Test
    @DisplayName("REQ-OPS-011 - non-GET health methods reach MVC and return 405 without authentication")
    void nonGetHealthMethodsShouldReturnMethodNotAllowed() {
        ExtractableResponse<Response> response = jsonRequest()
                .post("/health")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(405);
    }
}
