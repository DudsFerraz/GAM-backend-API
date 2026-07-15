package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - OpenAPI documentation contract")
class OpenApiDocumentationApiIT extends AbstractOpenApiDocumentationApiIT {

    @Test
    @DisplayName("REQ-OPENAPI-002 - anonymous developer -> Swagger UI is available at the public documentation route")
    void swaggerUiShouldBeAvailableWithoutAuthentication() {
        jsonRequest()
                .get("/api/docs")
                .then()
                .statusCode(200)
                .contentType("text/html");
    }

    @Test
    @DisplayName("REQ-OPENAPI-001 and REQ-WEB-004 - generated contract -> OpenAPI 3.1 with /api public server base")
    void generatedContractShouldDeclareOpenApi31AndThePublicApiServerBase() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");

        assertThat(contract).containsEntry("openapi", "3.1.0");
        assertThat(objects(contract, "servers"))
                .extracting(server -> server.get("url"))
                .contains("/api");
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 and REQ-OPENAPI-005 - generated contract -> GAM routes, bearer default, and public authentication overrides")
    void generatedContractShouldIncludeApplicationRoutesAndTheirSecurityBoundary() {
        ExtractableResponse<Response> response = openApiContract();
        Map<String, Object> contract = response.jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> components = object(contract, "components");
        Map<String, Object> securitySchemes = object(components, "securitySchemes");
        Map<String, Object> bearerAuth = object(securitySchemes, "bearerAuth");
        List<Map<String, Object>> publicAuthenticationOperations = List.of(
                object(object(paths, "/auth/register"), "post"),
                object(object(paths, "/auth/login"), "post"),
                object(object(paths, "/auth/refresh"), "post"),
                object(object(paths, "/auth/logout"), "post")
        );

        assertThat(paths).containsKeys("/auth/login", "/accounts/{id}", "/roles/{roleId}");
        assertThat(paths).doesNotContainKeys("/actuator/health", "/actuator/metrics", "/error");
        assertThat(contract.get("security"))
                .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        assertThat(bearerAuth).containsEntry("type", "http")
                .containsEntry("scheme", "bearer")
                .containsEntry("bearerFormat", "JWT");
        assertThat(publicAuthenticationOperations)
                .allSatisfy(operation -> assertThat(operation).containsEntry("security", List.of()));
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 - non-development Swagger UI configuration -> every request method is read-only")
    void nonDevelopmentSwaggerUiShouldDisableInteractiveRequestExecution() {
        Map<String, Object> configuration = swaggerUiConfiguration();

        assertThat(strings(configuration, "supportedSubmitMethods")).isEmpty();
    }
}
