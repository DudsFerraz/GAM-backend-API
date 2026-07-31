package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
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
    @DisplayName("REQ-EVENT-007, REQ-EVENT-012, REQ-EVENT-019 and REQ-PRESENCE-006 - Event mutation operations -> exact success statuses and Location headers")
    void eventMutationOperationsShouldDocumentExactSuccessContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> createResponses = object(object(object(paths, "/events"), "post"), "responses");
        Map<String, Object> deleteResponses = object(object(object(paths, "/events/{id}"), "delete"), "responses");
        Map<String, Object> registrationResponses = object(
                object(object(paths, "/events/{eventId}/presences"), "post"), "responses"
        );
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(createResponses).as("create Event responses").containsKey("201");
        assertLocationHeader(softly, createResponses, "201", "create Event");

        softly.assertThat(deleteResponses).as("delete Event responses").containsKey("204");
        Map<String, Object> deleted = object(deleteResponses, "204");
        if (deleted != null) {
            softly.assertThat(deleted).as("delete Event 204 response").doesNotContainKey("content");
        }

        softly.assertThat(registrationResponses).as("register Presence responses").containsKey("201");
        assertLocationHeader(softly, registrationResponses, "201", "register Presence");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-PRESENCE-006 and REQ-PRESENCE-007 - registration success schema -> complete compact Presence")
    void presenceRegistrationShouldDocumentCompleteResponseSchema() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> registration = object(
                object(paths, "/events/{eventId}/presences"), "post"
        );
        Map<String, Object> created = object(object(registration, "responses"), "201");
        Map<String, Object> content = object(created, "content");
        assertThat(content).as("Presence registration 201 content").isNotNull();
        Map<String, Object> json = content.containsKey("application/json")
                ? object(content, "application/json")
                : object(content, "*/*");
        assertThat(json).as("Presence registration 201 response schema").isNotNull();
        Map<String, Object> presenceSchema = resolveSchema(contract, object(json, "schema"));
        Map<String, Object> presenceProperties = object(presenceSchema, "properties");

        assertThat(strings(presenceSchema, "required"))
                .containsExactlyInAnyOrder("id", "member", "event", "observations", "registeredAt");
        assertThat(presenceProperties)
                .containsOnlyKeys("id", "member", "event", "observations", "registeredAt");
        Map<String, Object> memberSchema = resolveSchema(contract, object(presenceProperties, "member"));
        Map<String, Object> eventSchema = resolveSchema(contract, object(presenceProperties, "event"));
        assertThat(object(memberSchema, "properties"))
                .containsOnlyKeys("id", "firstName", "surname", "status");
        assertThat(object(eventSchema, "properties"))
                .containsOnlyKeys("id", "title", "beginDate", "endDate", "type", "status");
        assertThat(object(presenceProperties, "registeredAt"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-002 and REQ-OPENAPI-004 - solicitation submission -> prohibited accountId is absent from schema and example")
    void membershipSolicitationSubmissionShouldOmitProhibitedAccountId() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/membership-solicitations"),
                "post"
        );
        Map<String, Object> requestBody = object(operation, "requestBody");
        Map<String, Object> json = object(object(requestBody, "content"), "application/json");
        Map<String, Object> schema = resolveSchema(contract, object(json, "schema"));
        Map<String, Object> properties = object(schema, "properties");
        Map<String, Object> example = object(json, "example");
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(properties)
                .as("solicitation submission request properties")
                .containsOnlyKeys("firstName", "surname", "birthDate", "phoneNumber", "justification");
        softly.assertThat(strings(schema, "required"))
                .as("solicitation submission required properties")
                .containsExactlyInAnyOrder("firstName", "surname", "birthDate", "phoneNumber", "justification");
        softly.assertThat(example)
                .as("solicitation submission example")
                .containsOnlyKeys("firstName", "surname", "birthDate", "phoneNumber", "justification");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-PRESENCE-002/004/011/013 and REQ-OPENAPI-003/004 - Presence request schemas -> exact normalized text contracts")
    void presenceRequestsShouldDocumentExactNormalizedTextContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> registrationSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences"), "post")
        );
        Map<String, Object> editSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences/{memberId}"), "patch")
        );
        Map<String, Object> removalSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences/{memberId}"), "delete")
        );

        assertThat(strings(registrationSchema, "required")).containsExactly("memberId");
        assertThat(object(registrationSchema, "properties"))
                .containsOnlyKeys("memberId", "observations");
        assertNormalizedObservationsSchema(
                object(object(registrationSchema, "properties"), "observations")
        );

        assertThat(strings(editSchema, "required")).containsExactly("observations");
        assertThat(object(editSchema, "properties")).containsOnlyKeys("observations");
        assertNormalizedObservationsSchema(
                object(object(editSchema, "properties"), "observations")
        );

        assertThat(strings(removalSchema, "required")).containsExactly("reason");
        assertThat(object(removalSchema, "properties")).containsOnlyKeys("reason");
        Map<String, Object> reason = object(object(removalSchema, "properties"), "reason");
        assertThat(reason)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .doesNotContainKey("maxLength");
        assertThat(reason.get("description")).asString()
                .containsIgnoringCase("trim")
                .containsIgnoringCase("1")
                .containsIgnoringCase("2,000")
                .containsIgnoringCase("code point")
                .containsIgnoringCase("blank");
    }

    @Test
    @DisplayName("REQ-PRESENCE-009/010 and REQ-OPENAPI-003 - Presence collection inputs -> exact filter and deterministic ordering semantics")
    void presenceCollectionsShouldDocumentFilteringAndDeterministicOrdering() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> eventRoster = object(
                object(paths, "/events/{eventId}/presences"), "get"
        );
        Map<String, Object> memberHistory = object(
                object(paths, "/members/{memberId}/presences"), "get"
        );
        Map<String, Object> name = objects(eventRoster, "parameters").stream()
                .filter(parameter -> "name".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> eventSort = objects(eventRoster, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> memberSort = objects(memberHistory, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(String.valueOf(name.get("description"))).as("Event roster name filter")
                .containsIgnoringCase("trim")
                .containsIgnoringCase("blank")
                .containsIgnoringCase("case-insensitive")
                .containsIgnoringCase("accent-sensitive")
                .contains("firstName", "surname")
                .containsIgnoringCase("separately");
        softly.assertThat(object(eventSort, "schema").get("default"))
                .as("Event roster default sort")
                .isEqualTo(List.of(
                        "memberFirstName,asc",
                        "memberSurname,asc"
                ));
        softly.assertThat(eventSort.get("description")).as("Event roster sort semantics")
                .asString()
                .contains("memberFirstName", "memberSurname", "registeredAt")
                .containsIgnoringCase("default")
                .containsIgnoringCase("Presence UUID ascending")
                .containsIgnoringCase("requested sort");
        softly.assertThat(object(memberSort, "schema").get("default"))
                .as("Member history default sort")
                .isEqualTo(List.of("eventBeginDate,desc"));
        softly.assertThat(memberSort.get("description")).as("Member history sort semantics")
                .asString()
                .contains("eventBeginDate", "eventTitle", "registeredAt")
                .containsIgnoringCase("default")
                .containsIgnoringCase("Event UUID descending")
                .containsIgnoringCase("Presence UUID ascending")
                .containsIgnoringCase("requested sort");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-EVENT-010 - Event search OpenAPI contract -> effective-status sort and beginDate/id default")
    void eventSearchShouldDocumentExactSortingContract() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> searchEvents = object(object(paths, "/events/search"), "post");
        Map<String, Object> sort = objects(searchEvents, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(object(sort, "schema").get("default"))
                .isEqualTo(List.of("beginDate,asc", "id,asc"));
        assertThat(sort.get("description").toString())
                .contains("title", "beginDate", "endDate", "type", "status")
                .containsIgnoringCase("effective status")
                .containsIgnoringCase("default")
                .contains("beginDate", "id");
    }

    @Test
    @DisplayName("REQ-EVENT-011/012/017/019 and REQ-PRESENCE-003/005 - conflict responses -> operation-specific codes and details")
    void eventAndPresenceConflictsShouldBeDocumentedPerOperation() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> lockConflict = object(
                object(object(object(paths, "/events/{id}/lock"), "patch"), "responses"), "409"
        );
        Map<String, Object> deletionConflict = object(
                object(object(object(paths, "/events/{id}"), "delete"), "responses"), "409"
        );
        Map<String, Object> registrationConflict = object(
                object(object(object(paths, "/events/{eventId}/presences"), "post"), "responses"), "409"
        );
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(lockConflict.toString()).as("lock Event 409 documentation")
                .contains(
                        "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                        "EVENT_TYPE_NOT_MANAGEABLE",
                        "eventId",
                        "currentStatus",
                        "requestedStatus"
                );
        softly.assertThat(deletionConflict.toString()).as("delete Event 409 documentation")
                .contains(
                        "EVENT_HAS_PRESENCES",
                        "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                        "EVENT_TYPE_NOT_MANAGEABLE",
                        "eventId",
                        "activePresenceCount"
                );
        softly.assertThat(registrationConflict.toString()).as("register Presence 409 documentation")
                .contains(
                        "PRESENCE_ALREADY_REGISTERED",
                        "PRESENCE_REGISTRATION_NOT_ALLOWED",
                        "eventId",
                        "memberId",
                        "presenceId",
                        "status",
                        "beginDate",
                        "evaluationInstant"
                );
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 - anonymous developer -> Swagger UI is available at the public documentation route")
    void swaggerUiShouldBeAvailableWithoutAuthentication() {
        assertHtmlEndpointAvailable("/api/docs");
    }

    @Test
    @DisplayName("REQ-OPENAPI-001 and REQ-WEB-004 - generated contract -> OpenAPI 3.1 with /api public server base")
    void generatedContractShouldDeclareOpenApi31AndThePublicApiServerBase() {
        Map<String, Object> contract = openApiContract().body();

        assertThat(contract).containsEntry("openapi", "3.1.0");
        assertThat(objects(contract, "servers"))
                .extracting(server -> server.get("url"))
                .contains("/api");
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 and REQ-OPENAPI-005 - generated contract -> GAM routes, bearer default, and public authentication overrides")
    void generatedContractShouldIncludeApplicationRoutesAndTheirSecurityBoundary() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> components = object(contract, "components");
        Map<String, Object> securitySchemes = object(components, "securitySchemes");
        Map<String, Object> bearerAuth = object(securitySchemes, "bearerAuth");
        assertThat(paths).containsKeys(
                "/auth/login",
                "/auth/csrf",
                "/accounts/{accountId}",
                "/roles/{roleId}"
        );
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
        Map<String, Object> contract = openApiContract().body();
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
        Map<String, Object> contract = openApiContract().body();
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
        Map<String, Object> contract = openApiContract().body();
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
    @DisplayName("REQ-RBAC-012/013 - Role collection OpenAPI contract documents complete name-filter semantics")
    void roleCollectionShouldBeDocumented() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths).containsKey("/roles");

        Map<String, Object> listRoles = object(object(paths, "/roles"), "get");
        assertThat(listRoles.get("operationId")).asString().isNotBlank();
        assertThat(objects(listRoles, "parameters"))
                .singleElement()
                .satisfies(parameter -> {
                    assertThat(parameter)
                            .containsEntry("name", "name")
                            .containsEntry("in", "query")
                            .containsEntry("required", false);
                    assertThat(object(parameter, "schema")).containsEntry("type", "string");
                    assertThat(parameter.get("description").toString())
                            .containsIgnoringCase("trim")
                            .containsIgnoringCase("case-insensitive")
                            .containsIgnoringCase("accent-sensitive")
                            .containsIgnoringCase("blank")
                            .contains("400");
                });
        assertThat(objects(listRoles, "parameters"))
                .extracting(parameter -> parameter.get("name"))
                .doesNotContain("page", "size", "sort");
        assertThat(object(listRoles, "responses")).containsOnlyKeys("200", "400", "401", "403");
    }

    @Test
    @DisplayName("REQ-MEMBER-017/018 - Coordinator lifecycle OpenAPI contract describes normalized reason validation")
    void coordinatorLifecycleShouldBeDocumented() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths).containsKeys(
                "/members/{memberId}/coordinator/grant",
                "/members/{memberId}/coordinator/revoke"
        );

        for (String transition : List.of("grant", "revoke")) {
            Map<String, Object> operation = object(
                    object(paths, "/members/{memberId}/coordinator/" + transition),
                    "patch"
            );
            assertThat(operation.get("operationId").toString()).containsIgnoringCase(transition).contains("Coordinator");
            assertThat(object(operation, "responses"))
                    .containsOnlyKeys("204", "400", "401", "403", "404", "409");

            Map<String, Object> requestBody = object(operation, "requestBody");
            assertThat(requestBody).containsEntry("required", true);
            Map<String, Object> json = object(object(requestBody, "content"), "application/json");
            Map<String, Object> reasonSchema = resolveSchema(contract, object(json, "schema"));
            assertThat(strings(reasonSchema, "required")).contains("reason");
            Map<String, Object> reasonProperty = object(object(reasonSchema, "properties"), "reason");
            assertThat(reasonProperty)
                    .containsEntry("type", "string")
                    .containsEntry("minLength", 1)
                    .doesNotContainKey("maxLength");
            assertThat(reasonProperty.get("description")).asString()
                    .containsIgnoringCase("trim")
                    .containsIgnoringCase("2,000")
                    .containsIgnoringCase("code point");
        }
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

    private void assertLocationHeader(
            SoftAssertions softly,
            Map<String, Object> responses,
            String status,
            String operation
    ) {
        Map<String, Object> response = object(responses, status);
        if (response == null) {
            return;
        }
        Map<String, Object> headers = object(response, "headers");
        softly.assertThat(headers).as("%s %s response headers", operation, status).isNotNull();
        if (headers != null) {
            softly.assertThat(headers).as("%s %s response headers", operation, status).containsKey("Location");
        }
    }

    private Map<String, Object> requestSchema(
            Map<String, Object> contract,
            Map<String, Object> operation
    ) {
        Map<String, Object> requestBody = object(operation, "requestBody");
        assertThat(requestBody).containsEntry("required", true);
        Map<String, Object> json = object(object(requestBody, "content"), "application/json");
        return resolveSchema(contract, object(json, "schema"));
    }

    private void assertNormalizedObservationsSchema(Map<String, Object> observations) {
        assertThat(observations.get("type")).isEqualTo(List.of("string", "null"));
        assertThat(observations).doesNotContainKey("maxLength");
        assertThat(observations.get("description")).asString()
                .containsIgnoringCase("trim")
                .containsIgnoringCase("2,000")
                .containsIgnoringCase("code point")
                .containsIgnoringCase("blank")
                .containsIgnoringCase("null");
    }
}
