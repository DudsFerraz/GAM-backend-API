package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Locale;
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
        assertThat(paths).containsKeys("/auth/login", "/auth/csrf", "/accounts/{id}", "/roles/{roleId}");
        List<Map<String, Object>> publicAuthenticationOperations = List.of(
                object(object(paths, "/auth/register"), "post"),
                object(object(paths, "/auth/login"), "post"),
                object(object(paths, "/auth/refresh"), "post"),
                object(object(paths, "/auth/logout"), "post"),
                object(object(paths, "/auth/csrf"), "get")
        );

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
    @DisplayName("REQ-OPENAPI-005 and REQ-BROWSER-AUTH-003/004 - authentication contract -> CSRF bootstrap and browser proof inputs are documented")
    @SuppressWarnings("unchecked")
    void authenticationContractShouldDocumentCsrfBootstrapAndBrowserProof() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> components = object(contract, "components");
        assertThat(paths).containsKey("/auth/csrf");

        Map<String, Object> csrf = object(object(paths, "/auth/csrf"), "get");
        Map<String, Object> csrfResponse = object(object(csrf, "responses"), "200");
        Map<String, Object> csrfJson = object(object(csrfResponse, "content"), "application/json");
        Map<String, Object> csrfSchema = resolveSchema(contract, object(csrfJson, "schema"));
        Map<String, Object> csrfProperties = object(csrfSchema, "properties");

        assertThat(csrf).containsEntry("security", List.of());
        assertThat((List<String>) csrfSchema.get("required"))
                .containsExactlyInAnyOrder("token", "headerName");
        assertThat(csrfProperties).containsKeys("token", "headerName");
        assertThat(object(csrfProperties, "headerName")).containsEntry("example", "X-XSRF-TOKEN");
        assertThat(object(csrfResponse, "headers")).containsKeys("Cache-Control", "Set-Cookie");

        assertHeaderParameter(object(object(paths, "/auth/login"), "post"), "X-XSRF-TOKEN");
        assertHeaderParameter(object(object(paths, "/auth/refresh"), "post"), "X-XSRF-TOKEN");
        assertHeaderParameter(object(object(paths, "/auth/logout"), "post"), "X-XSRF-TOKEN");
        assertCookieParameter(object(object(paths, "/auth/refresh"), "post"), "refreshToken");
        assertCookieParameter(object(object(paths, "/auth/logout"), "post"), "refreshToken");
        assertThat(components).containsKey("schemas");
    }

    @Test
    @DisplayName("REQ-OPENAPI-005 and REQ-BROWSER-AUTH-002 - authentication responses -> refresh cookie lifecycle is documented")
    void authenticationResponsesShouldDocumentRefreshCookieLifecycle() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");

        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/login"), "post"),
                "set", "establish", "issue"
        );
        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/refresh"), "post"),
                "set", "replace", "rotate"
        );
        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/logout"), "post"),
                "expire", "clear", "delete", "max-age=0"
        );
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 and REQ-OPENAPI-003/005 - current Account context -> API-relative protected operation and exact schema")
    void currentAccountContextShouldBeDocumentedAsAnApiRelativeBearerOperation() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths)
                .containsKey("/accounts/me")
                .doesNotContainKey("/api/accounts/me");

        Map<String, Object> operation = object(object(paths, "/accounts/me"), "get");
        assertThat(operation).containsEntry("operationId", "getCurrentAccountContext");
        assertThat(operation.getOrDefault("security", contract.get("security")))
                .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        assertThat(operation.get("summary").toString())
                .containsIgnoringCase("current Account context");
        assertThat(operation.get("description").toString())
                .containsIgnoringCase("authenticated Account")
                .containsIgnoringCase("effective permission")
                .doesNotContain("Performs the documented GAM operation");
        assertThat(operation).doesNotContainKeys("parameters", "requestBody");

        Map<String, Object> responses = object(operation, "responses");
        assertThat(responses).containsOnlyKeys("200", "401");

        Map<String, Object> successResponse = object(responses, "200");
        Map<String, Object> successJson = object(object(successResponse, "content"), "application/json");
        Map<String, Object> currentContextSchema = resolveSchema(contract, object(successJson, "schema"));
        Map<String, Object> properties = object(currentContextSchema, "properties");
        Map<String, Object> example = object(successJson, "example");

        assertThat(example).containsOnlyKeys("id", "email", "displayName", "roles", "permissions");
        assertThat(example.get("email").toString())
                .endsWith("@example.test")
                .isNotEqualTo("Synthetic GAM value");
        assertThat(example.get("displayName").toString())
                .isNotBlank()
                .isNotEqualTo("Synthetic GAM value");
        assertThat(objects(example, "roles"))
                .singleElement()
                .satisfies(role -> assertThat(role)
                        .containsEntry("name", "MEMBER")
                        .containsEntry("description", "Standard authenticated member access")
                        .containsEntry("systemManaged", true));
        assertThat(strings(example, "permissions"))
                .contains("ACCOUNT_GET", "EVENT_SEARCH")
                .doesNotHaveDuplicates();

        assertThat(strings(currentContextSchema, "required"))
                .containsExactlyInAnyOrder("id", "email", "displayName", "roles", "permissions");
        assertThat(properties).containsOnlyKeys("id", "email", "displayName", "roles", "permissions");
        assertThat(object(properties, "id"))
                .containsEntry("type", "string")
                .containsEntry("format", "uuid");
        assertThat(object(properties, "email")).containsEntry("type", "string");
        assertThat(object(properties, "displayName")).containsEntry("type", "string");

        Map<String, Object> rolesSchema = object(properties, "roles");
        Map<String, Object> roleSchema = resolveSchema(contract, object(rolesSchema, "items"));
        assertThat(rolesSchema).containsEntry("type", "array");
        assertThat(strings(roleSchema, "required"))
                .containsExactlyInAnyOrder("id", "name", "description", "systemManaged");
        assertThat(object(roleSchema, "properties"))
                .containsOnlyKeys("id", "name", "description", "systemManaged");

        Map<String, Object> permissionsSchema = object(properties, "permissions");
        assertThat(permissionsSchema)
                .containsEntry("type", "array")
                .containsEntry("uniqueItems", true);
        assertThat(object(permissionsSchema, "items")).containsEntry("type", "string");

        Map<String, Object> unauthorizedResponse = object(responses, "401");
        Map<String, Object> unauthorizedJson = object(object(unauthorizedResponse, "content"), "application/json");
        Map<String, Object> errorSchema = resolveSchema(contract, object(unauthorizedJson, "schema"));
        assertThat(object(errorSchema, "properties"))
                .containsKeys("timestamp", "status", "code", "message", "details")
                .doesNotContainKey("error");
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 - non-development Swagger UI configuration -> every request method is read-only")
    void nonDevelopmentSwaggerUiShouldDisableInteractiveRequestExecution() {
        Map<String, Object> configuration = swaggerUiConfiguration();

        assertThat(strings(configuration, "supportedSubmitMethods")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSchema(Map<String, Object> contract, Map<String, Object> schema) {
        Object reference = schema.get("$ref");
        if (reference == null) {
            return schema;
        }
        String schemaName = reference.toString().substring(reference.toString().lastIndexOf('/') + 1);
        return object(object(contract, "components"), "schemas").get(schemaName) instanceof Map<?, ?> resolved
                ? (Map<String, Object>) resolved
                : Map.of();
    }

    @SuppressWarnings("unchecked")
    private void assertHeaderParameter(Map<String, Object> operation, String name) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters)
                .anySatisfy(parameter -> assertThat(parameter)
                        .containsEntry("name", name)
                        .containsEntry("in", "header"));
    }

    @SuppressWarnings("unchecked")
    private void assertCookieParameter(Map<String, Object> operation, String name) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters)
                .anySatisfy(parameter -> assertThat(parameter)
                        .containsEntry("name", name)
                        .containsEntry("in", "cookie"));
    }

    @SuppressWarnings("unchecked")
    private void assertSetCookieEffect(
            Map<String, Object> contract,
            Map<String, Object> operation,
            String... actionTerms
    ) {
        Map<String, Object> successResponse = object(object(operation, "responses"), "200");
        Object headersValue = successResponse.get("headers");
        assertThat(headersValue)
                .as("%s 200 response headers", operation.get("operationId"))
                .isInstanceOf(Map.class);
        Map<String, Object> headers = (Map<String, Object>) headersValue;
        assertThat(headers)
                .as("%s 200 response headers", operation.get("operationId"))
                .containsKey("Set-Cookie");
        assertThat(headers.get("Set-Cookie"))
                .as("%s Set-Cookie response header", operation.get("operationId"))
                .isInstanceOf(Map.class);
        Map<String, Object> setCookieHeader = resolveHeader(
                contract,
                (Map<String, Object>) headers.get("Set-Cookie")
        );
        String description = setCookieHeader.getOrDefault("description", "")
                .toString()
                .toLowerCase(Locale.ROOT);

        assertThat(description).contains("refreshtoken");
        assertThat(List.of(actionTerms)).anyMatch(description::contains);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveHeader(Map<String, Object> contract, Map<String, Object> header) {
        Object reference = header.get("$ref");
        if (reference == null) {
            return header;
        }
        String headerName = reference.toString().substring(reference.toString().lastIndexOf('/') + 1);
        return object(object(contract, "components"), "headers").get(headerName) instanceof Map<?, ?> resolved
                ? (Map<String, Object>) resolved
                : Map.of();
    }
}
