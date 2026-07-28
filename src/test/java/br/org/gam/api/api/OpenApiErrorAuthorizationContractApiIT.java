package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI error and authorization contract")
class OpenApiErrorAuthorizationContractApiIT extends AbstractOpenApiDocumentationApiIT {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> ACCEPTED_ERROR_CODES = Set.of(
            "VALIDATION_ERROR",
            "MALFORMED_JSON",
            "INVALID_PARAMETER_TYPE",
            "AUTHENTICATION_REQUIRED",
            "INVALID_CREDENTIALS",
            "INVALID_REFRESH_TOKEN",
            "ACCESS_DENIED",
            "FORBIDDEN_OPERATION",
            "REQUEST_SECURITY_REJECTED",
            "RESOURCE_NOT_FOUND",
            "INVALID_SEARCH_FILTER",
            "CONFLICT",
            "RESOURCE_CONFLICT",
            "EVENT_AUDIENCE_PERMISSION_INVALID",
            "EVENT_HAS_PRESENCES",
            "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
            "EVENT_TYPE_NOT_MANAGEABLE",
            "GAM_LOCATION_ALREADY_EXISTS",
            "GAM_LOCATION_IN_USE",
            "ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED",
            "ORATORIANO_FORM_PROFILE_SOURCE_IS_NEWER",
            "ORATORIO_DATE_ALREADY_EXISTS",
            "PRESENCE_ALREADY_REGISTERED",
            "PRESENCE_EDIT_NOT_ALLOWED",
            "PRESENCE_REGISTRATION_NOT_ALLOWED",
            "PRESENCE_REMOVAL_NOT_ALLOWED"
    );
    private static final List<String> STRUCTURED_SEARCH_PATHS = List.of(
            "/accounts/search",
            "/events/search",
            "/members/search",
            "/membership-solicitations/search",
            "/oratorianos/search"
    );

    @Test
    @DisplayName("REQ-API-ERROR-001/008/012 and REQ-OPENAPI-003/006 - authentication responses -> exact recovery contracts")
    void authenticationResponsesShouldDocumentExactCodesHeadersAndEmptyDetails() {
        Map<String, Object> contract = openApiContract().body();

        Map<String, Object> protectedUnauthorized =
                response(operation(contract, "/accounts/search", "post"), "401");
        assertSingleErrorExample(
                protectedUnauthorized,
                401,
                "AUTHENTICATION_REQUIRED",
                Map.of()
        );
        assertErrorTransport(protectedUnauthorized, true);

        Map<String, Object> invalidCredentials =
                response(operation(contract, "/auth/login", "post"), "401");
        assertSingleErrorExample(invalidCredentials, 401, "INVALID_CREDENTIALS", Map.of());
        assertErrorTransport(invalidCredentials, false);

        Map<String, Object> invalidRefreshToken =
                response(operation(contract, "/auth/refresh", "post"), "401");
        assertSingleErrorExample(invalidRefreshToken, 401, "INVALID_REFRESH_TOKEN", Map.of());
        assertErrorTransport(invalidRefreshToken, false);
    }

    @Test
    @DisplayName("REQ-API-ERROR-009/012 and REQ-OPENAPI-003/006 - forbidden responses -> distinct authority, invariant, and request-security codes")
    void forbiddenResponsesShouldDocumentDistinctStableCodes() {
        Map<String, Object> contract = openApiContract().body();

        Map<String, Object> accessDenied =
                response(operation(contract, "/accounts/search", "post"), "403");
        assertSingleErrorExample(accessDenied, 403, "ACCESS_DENIED", Map.of());
        assertErrorTransport(accessDenied, false);

        for (String operationPath : List.of("/auth/login", "/auth/refresh", "/auth/logout")) {
            Map<String, Object> requestSecurityRejected =
                    response(operation(contract, operationPath, "post"), "403");
            assertSingleErrorExample(
                    requestSecurityRejected,
                    403,
                    "REQUEST_SECURITY_REJECTED",
                    Map.of()
            );
            assertErrorTransport(requestSecurityRejected, false);
        }

        Map<String, Object> roleMutation =
                response(operation(contract, "/accounts/{accountId}/roles", "post"), "403");
        assertThat(errorExamples(roleMutation))
                .extracting(example -> example.get("code"))
                .containsExactlyInAnyOrder("ACCESS_DENIED", "FORBIDDEN_OPERATION");
        assertThat(errorExamples(roleMutation))
                .allSatisfy(example -> assertThat(example.get("details")).isEqualTo(Map.of()));
        assertErrorTransport(roleMutation, false);
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004/005 and REQ-SEARCH-009 - structured-search 400 -> safe malformed, validation, and semantic examples")
    void structuredSearchBadRequestShouldDocumentEverySafeFailureTopology() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> badRequest =
                response(operation(contract, "/members/search", "post"), "400");
        Map<String, Map<String, Object>> examplesByCode = examplesByCode(badRequest);

        assertThat(examplesByCode.keySet()).containsExactlyInAnyOrder(
                "MALFORMED_JSON",
                "VALIDATION_ERROR",
                "INVALID_SEARCH_FILTER",
                "INVALID_PARAMETER_TYPE"
        );

        Map<String, Object> malformed = examplesByCode.get("MALFORMED_JSON");
        assertErrorExampleEnvelope(malformed, 400, "MALFORMED_JSON");
        assertThat(details(malformed)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "reason", "UNKNOWN_FIELD",
                "location", "body"
        ));

        Map<String, Object> validation = examplesByCode.get("VALIDATION_ERROR");
        assertErrorExampleEnvelope(validation, 400, "VALIDATION_ERROR");
        assertThat(details(validation)).containsOnlyKeys("violations");
        assertThat(violations(validation)).isNotEmpty().allSatisfy(violation -> {
            assertThat(violation).containsOnlyKeys("location", "field", "code", "message");
            assertThat(violation.get("location")).isIn("body", "path", "query", "header", "cookie");
            assertThat(violation.get("field")).isInstanceOf(String.class);
            assertThat(violation.get("code")).isIn(
                    "REQUIRED",
                    "NOT_BLANK",
                    "SIZE",
                    "RANGE",
                    "FORMAT",
                    "ALLOWED_VALUE",
                    "RELATION",
                    "INVALID_VALUE"
            );
        });

        Map<String, Object> invalidSearch = examplesByCode.get("INVALID_SEARCH_FILTER");
        assertErrorExampleEnvelope(invalidSearch, 400, "INVALID_SEARCH_FILTER");
        assertThat(details(invalidSearch)).containsEntry("filterIndex", 0);

        Map<String, Object> invalidParameter = examplesByCode.get("INVALID_PARAMETER_TYPE");
        assertThat(invalidParameter).as("structured-search query conversion example").isNotNull();
        if (invalidParameter != null) {
            assertErrorExampleEnvelope(invalidParameter, 400, "INVALID_PARAMETER_TYPE");
            assertThat(details(invalidParameter)).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "location", "query",
                    "field", "page",
                    "expectedType", "INTEGER"
            ));
        }
        assertErrorTransport(badRequest, false);
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/006/008/009/012, REQ-SEARCH-009, and REQ-OPENAPI-003/006 - structured searches -> exact response, code, and header catalog")
    void structuredSearchOperationsShouldDocumentOnlyAcceptedErrorContracts() {
        Map<String, Object> contract = openApiContract().body();
        SoftAssertions responseSets = new SoftAssertions();

        for (String path : STRUCTURED_SEARCH_PATHS) {
            Map<String, Object> responses = object(operation(contract, path, "post"), "responses");
            responseSets.assertThat(responses.keySet())
                    .as("POST %s response statuses", path)
                    .containsExactlyInAnyOrder("200", "400", "401", "403");

            Map<String, Object> badRequest = object(responses, "400");
            responseSets.assertThat(examplesByCode(badRequest).keySet())
                    .as("POST %s 400 codes", path)
                    .containsExactlyInAnyOrder(
                            "MALFORMED_JSON",
                            "VALIDATION_ERROR",
                            "INVALID_SEARCH_FILTER",
                            "INVALID_PARAMETER_TYPE"
                    );
            assertErrorTransport(badRequest, false);

            Map<String, Object> unauthorized = object(responses, "401");
            assertSingleErrorExample(unauthorized, 401, "AUTHENTICATION_REQUIRED", Map.of());
            assertErrorTransport(unauthorized, true);

            Map<String, Object> forbidden = object(responses, "403");
            assertSingleErrorExample(forbidden, 403, "ACCESS_DENIED", Map.of());
            assertErrorTransport(forbidden, false);
        }

        responseSets.assertAll();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-008/011, REQ-ORATORIANO-012, REQ-API-ERROR-002/006/008/009/010/012, and REQ-OPENAPI-003/006/007 - query-driven reads -> exact response, code, and header catalogs")
    void queryDrivenReadOperationsShouldDocumentOnlyAcceptedErrorContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Set<String>> operations = Map.of(
                "/gam-locations", Set.of("200", "400", "401", "403"),
                "/oratorianos/{oratorianoId}/attendance-summary",
                Set.of("200", "400", "401", "403", "404")
        );
        SoftAssertions responseSets = new SoftAssertions();

        operations.forEach((path, expectedStatuses) -> {
            Map<String, Object> responses = object(operation(contract, path, "get"), "responses");
            responseSets.assertThat(responses.keySet())
                    .as("GET %s response statuses", path)
                    .containsExactlyInAnyOrderElementsOf(expectedStatuses);

            Map<String, Object> badRequest = object(responses, "400");
            responseSets.assertThat(examplesByCode(badRequest).keySet())
                    .as("GET %s 400 codes", path)
                    .containsExactlyInAnyOrder("INVALID_PARAMETER_TYPE", "VALIDATION_ERROR");
            assertErrorTransport(badRequest, false);

            assertErrorTransport(object(responses, "401"), true);
            assertErrorTransport(object(responses, "403"), false);
            if (expectedStatuses.contains("404")) {
                assertErrorTransport(object(responses, "404"), false);
            }
        });

        responseSets.assertAll();
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/006 and REQ-OPENAPI-003 - invalid path type -> public parameter details")
    void invalidParameterTypeShouldDocumentThePublicTransportType() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> responses =
                object(operation(contract, "/roles/{roleId}", "get"), "responses");
        Map<String, Object> badRequest =
                object(responses, "400");
        Map<String, Map<String, Object>> examplesByCode = examplesByCode(badRequest);
        SoftAssertions catalog = new SoftAssertions();

        catalog.assertThat(responses.keySet())
                .containsExactlyInAnyOrder("200", "400", "401", "403", "404");
        catalog.assertThat(examplesByCode.keySet()).containsExactly("INVALID_PARAMETER_TYPE");
        Map<String, Object> example = examplesByCode.get("INVALID_PARAMETER_TYPE");
        assertErrorExampleEnvelope(example, 400, "INVALID_PARAMETER_TYPE");
        catalog.assertThat(details(example)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "location", "path",
                "field", "roleId",
                "expectedType", "UUID"
        ));
        assertErrorTransport(badRequest, false);
        catalog.assertAll();
    }

    @Test
    @DisplayName("REQ-API-ERROR-010/012 and REQ-OPENAPI-003/006 - protected resource 404 -> generic public resource details")
    void notFoundResponseShouldDocumentOnlyPublicResourceAndIdentifier() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> notFound =
                response(operation(contract, "/roles/{roleId}", "get"), "404");
        Map<String, Map<String, Object>> examplesByCode = examplesByCode(notFound);

        assertThat(examplesByCode.keySet()).containsExactly("RESOURCE_NOT_FOUND");
        Map<String, Object> example = examplesByCode.get("RESOURCE_NOT_FOUND");
        assertErrorExampleEnvelope(example, 404, "RESOURCE_NOT_FOUND");
        assertThat(details(example)).containsOnlyKeys("resource", "identifier");
        assertThat(details(example)).containsEntry("resource", "Role");
        assertErrorTransport(notFound, false);
    }

    @Test
    @DisplayName("REQ-OPENAPI-004/006 - common error envelope -> closed top-level schema")
    void commonErrorSchemaShouldRejectUndocumentedTopLevelProperties() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> errorSchema = schema(contract, "ApiErrorDTO");

        assertThat(errorSchema.get("additionalProperties"))
                .as("ApiErrorDTO top-level properties")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("REQ-OPENAPI-004/006 and REQ-API-ERROR-002/008/009/010 - common error code -> complete accepted consumer catalog")
    void commonErrorSchemaShouldEnumerateStableDiscriminators() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> errorSchema = schema(contract, "ApiErrorDTO");
        Map<String, Object> codeSchema = object(object(errorSchema, "properties"), "code");

        assertThat(codeSchema.get("enum")).as("ApiErrorDTO.code enum").isInstanceOf(List.class);
        assertThat(strings(codeSchema, "enum")).containsAll(ACCEPTED_ERROR_CODES);
    }

    @Test
    @DisplayName("REQ-OPENAPI-004/006/011 - documented error examples -> admitted by the common code schema")
    void commonErrorSchemaShouldAdmitEveryDocumentedExampleCode() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> errorSchema = schema(contract, "ApiErrorDTO");
        Map<String, Object> codeSchema = object(object(errorSchema, "properties"), "code");

        Set<String> documentedCodes = documentedErrorCodes(contract);

        assertThat(documentedCodes).as("operation error example codes").isNotEmpty();
        assertThat(strings(codeSchema, "enum"))
                .as("ApiErrorDTO.code enum must validate every documented operation example")
                .containsAll(documentedCodes);
    }

    @Test
    @DisplayName("REQ-OPENAPI-004/006, REQ-API-ERROR-003/005/006/010, and REQ-SEARCH-010 - details -> typed consumer variants")
    void commonErrorDetailsShouldDescribeStructuredVariants() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> errorSchema = object(schemas, "ApiErrorDTO");
        Map<String, Object> detailsSchema = object(object(errorSchema, "properties"), "details");

        List<Map<String, Object>> variants = detailsComposition(detailsSchema).stream()
                .map(variant -> resolveSchema(schemas, variant))
                .toList();

        Map<String, Object> validation = variantRequiring(variants, "violations");
        assertClosedDetailsVariant(validation);
        Map<String, Object> violations = object(object(validation, "properties"), "violations");
        Map<String, Object> violation = resolveSchema(
                schemas,
                castObject(violations.get("items"), "validation violation items")
        );
        assertThat(required(violation))
                .containsExactlyInAnyOrder("location", "field", "code", "message");
        assertThat(object(violation, "properties").keySet())
                .containsExactlyInAnyOrder("location", "field", "code", "message");
        assertThat(strings(object(object(violation, "properties"), "code"), "enum"))
                .containsExactlyInAnyOrder(
                        "REQUIRED",
                        "NOT_BLANK",
                        "SIZE",
                        "RANGE",
                        "FORMAT",
                        "ALLOWED_VALUE",
                        "RELATION",
                        "INVALID_VALUE"
                );

        Map<String, Object> malformed = variantRequiring(variants, "reason", "location");
        assertClosedDetailsVariant(malformed);
        Map<String, Object> malformedProperties = object(malformed, "properties");
        assertThat(malformedProperties.keySet()).containsExactlyInAnyOrder("reason", "location", "field");
        assertThat(strings(object(malformedProperties, "reason"), "enum"))
                .containsExactlyInAnyOrder("SYNTAX_ERROR", "UNKNOWN_FIELD", "TYPE_MISMATCH");
        assertThat(strings(object(malformedProperties, "location"), "enum")).containsExactly("body");

        Map<String, Object> invalidParameter =
                variantRequiring(variants, "location", "field", "expectedType");
        assertClosedDetailsVariant(invalidParameter);
        assertThat(strings(
                object(object(invalidParameter, "properties"), "expectedType"),
                "enum"
        )).containsExactlyInAnyOrder(
                "UUID",
                "INTEGER",
                "DECIMAL",
                "BOOLEAN",
                "DATE",
                "DATE_TIME",
                "ENUM"
        );

        Map<String, Object> notFound = variantRequiring(variants, "resource", "identifier");
        assertClosedDetailsVariant(notFound);
        assertThat(object(notFound, "properties").keySet())
                .containsExactlyInAnyOrder("resource", "identifier");

        Map<String, Object> invalidSearch = variantRequiring(variants, "filterIndex");
        assertClosedDetailsVariant(invalidSearch);
        Map<String, Object> invalidSearchProperties = object(invalidSearch, "properties");
        assertThat(invalidSearchProperties.keySet())
                .containsExactlyInAnyOrder("filterIndex", "field", "comparisonMethod");
        assertThat(object(invalidSearchProperties, "filterIndex"))
                .containsEntry("type", "integer")
                .containsEntry("minimum", 0);
        assertThat(object(invalidSearchProperties, "field"))
                .containsEntry("type", "string");
        assertThat(strings(object(invalidSearchProperties, "comparisonMethod"), "enum"))
                .containsExactlyInAnyOrder(
                        "EQUALS",
                        "LIKE",
                        "IN",
                        "GREATER_THAN_OR_EQUAL",
                        "LESS_THAN_OR_EQUAL"
                );

        Map<String, Object> empty = variants.stream()
                .filter(variant -> Integer.valueOf(0).equals(variant.get("maxProperties")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No closed empty error-details variant"));
        assertClosedDetailsVariant(empty);
    }

    @Test
    @DisplayName("Developer resolution and REQ-OPENAPI-004/006/011 - details composition -> typed common variants plus one property-agnostic feature fallback")
    void commonErrorDetailsShouldComposeTypedCommonVariantsWithOpenFeatureFallback() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> errorSchema = object(schemas, "ApiErrorDTO");
        Map<String, Object> detailsSchema = object(object(errorSchema, "properties"), "details");

        List<Map<String, Object>> variants = detailsComposition(detailsSchema).stream()
                .map(variant -> resolveSchema(schemas, variant))
                .toList();
        List<Map<String, Object>> openFallbacks = variants.stream()
                .filter(variant -> Boolean.TRUE.equals(variant.get("additionalProperties")))
                .toList();

        assertThat(openFallbacks)
                .as("one open fallback for existing out-of-scope feature details")
                .singleElement()
                .satisfies(fallback -> {
                    assertThat(fallback).containsEntry("type", "object");
                    assertThat(fallback).doesNotContainKeys("properties", "required", "oneOf", "anyOf");
                });
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-003 and REQ-OPENAPI-003 - CSRF bootstrap -> only accepted success response")
    void csrfBootstrapShouldNotDocumentImpossibleGenericErrors() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> responses =
                object(operation(contract, "/auth/csrf", "get"), "responses");

        assertThat(responses.keySet()).containsExactly("200");
    }

    @Test
    @DisplayName("REQ-AUTH-006/007, REQ-BROWSER-AUTH-004, REQ-API-ERROR-002/008/009, and REQ-OPENAPI-003 - login -> exact accepted response set")
    void loginShouldDocumentOnlyAcceptedResponses() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> responses =
                object(operation(contract, "/auth/login", "post"), "responses");

        assertThat(responses.keySet()).containsExactlyInAnyOrder("200", "400", "401", "403");
    }

    @Test
    @DisplayName("REQ-EVENT-006/014/021 and REQ-OPENAPI-003 - public Event lookup path -> mutations remain bearer protected")
    void eventPathSecurityShouldBeDefinedPerOperation() {
        Map<String, Object> contract = openApiContract().body();
        SoftAssertions protectedOperations = new SoftAssertions();

        Map<String, Object> publicLookup = operation(contract, "/events/{id}", "get");
        assertThat(publicLookup.get("security"))
                .as("GET /events/{id} must explicitly override global bearer security")
                .isEqualTo(List.of());

        assertBearerProtected(
                protectedOperations,
                operation(contract, "/events/{id}", "put"),
                "PUT /events/{id}"
        );
        assertBearerProtected(
                protectedOperations,
                operation(contract, "/events/{id}", "delete"),
                "DELETE /events/{id}"
        );
        protectedOperations.assertAll();
    }

    @Test
    @DisplayName("REQ-EVENT-008, REQ-API-ERROR-002/006/010/012, and REQ-OPENAPI-003/006 - public Event lookup -> exact operation errors")
    void publicEventLookupShouldDocumentOnlyItsAcceptedErrorContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> responses =
                object(operation(contract, "/events/{id}", "get"), "responses");
        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(responses.keySet())
                .as("GET /events/{id} response statuses")
                .containsExactlyInAnyOrder("200", "400", "404");

        Map<String, Object> badRequest = object(responses, "400");
        Map<String, Map<String, Object>> badRequestExamples = examplesByCode(badRequest);
        assertions.assertThat(badRequestExamples.keySet())
                .as("GET /events/{id} 400 example codes")
                .containsExactly("INVALID_PARAMETER_TYPE");
        Map<String, Object> invalidIdentifier = badRequestExamples.get("INVALID_PARAMETER_TYPE");
        assertErrorExampleEnvelope(invalidIdentifier, 400, "INVALID_PARAMETER_TYPE");
        assertions.assertThat(details(invalidIdentifier))
                .as("GET /events/{id} invalid identifier details")
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "location", "path",
                        "field", "id",
                        "expectedType", "UUID"
                ));
        assertErrorTransport(badRequest, false);

        Map<String, Object> notFound = object(responses, "404");
        Map<String, Map<String, Object>> notFoundExamples = examplesByCode(notFound);
        assertions.assertThat(notFoundExamples.keySet())
                .as("GET /events/{id} 404 example codes")
                .containsExactly("RESOURCE_NOT_FOUND");
        Map<String, Object> missingEvent = notFoundExamples.get("RESOURCE_NOT_FOUND");
        assertErrorExampleEnvelope(missingEvent, 404, "RESOURCE_NOT_FOUND");
        assertions.assertThat(details(missingEvent))
                .as("GET /events/{id} not-found details")
                .containsOnlyKeys("resource", "identifier")
                .containsEntry("resource", "Event");
        assertions.assertThat(details(missingEvent).get("identifier"))
                .as("GET /events/{id} not-found example identifier")
                .isInstanceOf(String.class);
        assertErrorTransport(notFound, false);

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-AUTH-002/014/015/016/017/018 and REQ-OPENAPI-003/006 - auth operations -> exact accepted status, code, and header matrix")
    void authOperationsShouldDocumentOnlyAcceptedResponseContracts() {
        Map<String, Object> contract = openApiContract().body();
        SoftAssertions responseSets = new SoftAssertions();

        Map<String, Object> registration =
                object(operation(contract, "/auth/register", "post"), "responses");
        responseSets.assertThat(registration.keySet())
                .as("POST /auth/register response statuses")
                .containsExactlyInAnyOrder("201", "400", "409");
        Map<String, Map<String, Object>> registrationBadRequest =
                examplesByCode(object(registration, "400"));
        assertThat(registrationBadRequest.keySet())
                .containsExactlyInAnyOrder("MALFORMED_JSON", "VALIDATION_ERROR");
        assertErrorTransport(object(registration, "400"), false);
        Map<String, Object> registrationConflict = object(registration, "409");
        assertSingleErrorExample(registrationConflict, 409, "CONFLICT", Map.of());
        assertErrorTransport(registrationConflict, false);

        Map<String, Object> refresh =
                object(operation(contract, "/auth/refresh", "post"), "responses");
        responseSets.assertThat(refresh.keySet())
                .as("POST /auth/refresh response statuses")
                .containsExactlyInAnyOrder("200", "401", "403");
        assertSingleErrorExample(object(refresh, "401"), 401, "INVALID_REFRESH_TOKEN", Map.of());
        assertErrorTransport(object(refresh, "401"), false);
        assertSingleErrorExample(
                object(refresh, "403"),
                403,
                "REQUEST_SECURITY_REJECTED",
                Map.of()
        );
        assertErrorTransport(object(refresh, "403"), false);

        Map<String, Object> logout =
                object(operation(contract, "/auth/logout", "post"), "responses");
        responseSets.assertThat(logout.keySet())
                .as("POST /auth/logout response statuses")
                .containsExactlyInAnyOrder("200", "403");
        assertSingleErrorExample(
                object(logout, "403"),
                403,
                "REQUEST_SECURITY_REJECTED",
                Map.of()
        );
        assertErrorTransport(object(logout, "403"), false);

        responseSets.assertAll();
    }

    private Map<String, Object> operation(
            Map<String, Object> contract,
            String path,
            String method
    ) {
        return object(object(object(contract, "paths"), path), method);
    }

    private void assertBearerProtected(
            SoftAssertions assertions,
            Map<String, Object> operation,
            String label
    ) {
        Object security = operation.get("security");
        if (security == null) {
            return;
        }

        assertions.assertThat(security).as(label + " security").isInstanceOf(List.class);
        if (!(security instanceof List<?> securityRequirements)) {
            return;
        }
        assertions.assertThat(securityRequirements)
                .as(label + " security must not override bearer authentication with public access")
                .isNotEmpty()
                .allSatisfy(requirement -> {
                    assertions.assertThat(requirement).isInstanceOf(Map.class);
                    if (requirement instanceof Map<?, ?> requirementMap) {
                        assertions.assertThat(requirementMap.containsKey("bearerAuth")).isTrue();
                    }
                });
    }

    private Map<String, Object> response(Map<String, Object> operation, String status) {
        return object(object(operation, "responses"), status);
    }

    private Map<String, Object> schema(Map<String, Object> contract, String name) {
        return object(object(object(contract, "components"), "schemas"), name);
    }

    private void assertSingleErrorExample(
            Map<String, Object> response,
            int status,
            String code,
            Map<String, ?> expectedDetails
    ) {
        assertThat(errorExamples(response)).singleElement().satisfies(example -> {
            assertErrorExampleEnvelope(example, status, code);
            assertThat(example.get("details")).isEqualTo(expectedDetails);
        });
    }

    private void assertErrorExampleEnvelope(Map<String, Object> example, int status, String code) {
        assertThat(example)
                .containsOnlyKeys("timestamp", "status", "code", "message", "details")
                .containsEntry("status", status)
                .containsEntry("code", code);
        assertThat(example.get("timestamp")).isInstanceOf(String.class);
        assertThat(String.valueOf(example.get("timestamp"))).endsWith("Z");
        assertThat(example.get("message")).isInstanceOf(String.class);
        assertThat(String.valueOf(example.get("message"))).isNotBlank();
        assertThat(example.get("details")).isInstanceOf(Map.class);
    }

    private void assertErrorTransport(Map<String, Object> response, boolean bearerChallenge) {
        Map<String, Object> content = object(response, "content");
        assertThat(content).containsOnlyKeys("application/json").doesNotContainKey("application/problem+json");

        Map<String, Object> headers = object(response, "headers");
        assertThat(headers).containsKey("Cache-Control");
        if (bearerChallenge) {
            assertThat(headers).containsKey("WWW-Authenticate");
            assertThat(String.valueOf(headers.get("WWW-Authenticate"))).contains("Bearer");
        } else {
            assertThat(headers).doesNotContainKey("WWW-Authenticate");
        }
    }

    private Map<String, Map<String, Object>> examplesByCode(Map<String, Object> response) {
        Map<String, Map<String, Object>> byCode = new java.util.LinkedHashMap<>();
        for (Map<String, Object> example : errorExamples(response)) {
            assertThat(example.get("code")).isInstanceOf(String.class);
            byCode.put(String.valueOf(example.get("code")), example);
        }
        return byCode;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorExamples(Map<String, Object> response) {
        Map<String, Object> mediaType = object(object(response, "content"), "application/json");
        List<Map<String, Object>> examples = new ArrayList<>();

        if (mediaType.get("example") instanceof Map<?, ?> example) {
            examples.add((Map<String, Object>) example);
        }
        if (mediaType.get("examples") instanceof Map<?, ?> namedExamples) {
            for (Object wrapper : namedExamples.values()) {
                Map<String, Object> wrapperMap = (Map<String, Object>) wrapper;
                examples.add(object(wrapperMap, "value"));
            }
        }

        assertThat(examples).as("documented error examples").isNotEmpty();
        return examples;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(Map<String, Object> error) {
        return (Map<String, Object>) error.get("details");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> violations(Map<String, Object> error) {
        Object violations = details(error).get("violations");
        assertThat(violations).isInstanceOf(Collection.class);
        return (List<Map<String, Object>>) violations;
    }

    private Map<String, Object> resolveSchema(
            Map<String, Object> schemas,
            Map<String, Object> candidate
    ) {
        Object reference = candidate.get("$ref");
        if (!(reference instanceof String ref)) {
            return candidate;
        }
        String prefix = "#/components/schemas/";
        assertThat(ref).startsWith(prefix);
        return object(schemas, ref.substring(prefix.length()));
    }

    private List<Map<String, Object>> detailsComposition(Map<String, Object> detailsSchema) {
        boolean hasOneOf = detailsSchema.get("oneOf") instanceof List<?>;
        boolean hasAnyOf = detailsSchema.get("anyOf") instanceof List<?>;
        assertThat(List.of(hasOneOf, hasAnyOf))
                .as("ApiErrorDTO.details must use exactly one composition keyword")
                .containsExactlyInAnyOrder(true, false);
        return objects(detailsSchema, hasAnyOf ? "anyOf" : "oneOf");
    }

    private void assertClosedDetailsVariant(Map<String, Object> variant) {
        assertThat(variant.get("additionalProperties"))
                .as("typed common-error details must remain closed")
                .isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    private Set<String> documentedErrorCodes(Map<String, Object> contract) {
        Set<String> codes = new TreeSet<>();
        Map<String, Object> paths = object(contract, "paths");

        for (Object pathItemValue : paths.values()) {
            Map<String, Object> pathItem = (Map<String, Object>) pathItemValue;
            for (String method : HTTP_METHODS) {
                if (!(pathItem.get(method) instanceof Map<?, ?> operationValue)) {
                    continue;
                }
                Map<String, Object> operation = (Map<String, Object>) operationValue;
                Map<String, Object> responses = object(operation, "responses");
                for (Object responseValue : responses.values()) {
                    Map<String, Object> response = (Map<String, Object>) responseValue;
                    Map<String, Object> content = object(response, "content");
                    if (content == null
                            || !(content.get("application/json") instanceof Map<?, ?> mediaTypeValue)) {
                        continue;
                    }
                    Map<String, Object> mediaType = (Map<String, Object>) mediaTypeValue;
                    addExampleCode(codes, mediaType.get("example"));
                    if (mediaType.get("examples") instanceof Map<?, ?> examples) {
                        for (Object wrapperValue : examples.values()) {
                            Map<String, Object> wrapper = (Map<String, Object>) wrapperValue;
                            addExampleCode(codes, wrapper.get("value"));
                        }
                    }
                }
            }
        }
        return codes;
    }

    private void addExampleCode(Set<String> codes, Object exampleValue) {
        if (exampleValue instanceof Map<?, ?> example
                && example.get("status") instanceof Number
                && example.get("code") instanceof String code) {
            codes.add(code);
        }
    }

    private Map<String, Object> variantRequiring(
            List<Map<String, Object>> variants,
            String... fields
    ) {
        Set<String> expected = Set.of(fields);
        return variants.stream()
                .filter(variant -> Set.copyOf(required(variant)).equals(expected))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No error details variant requires exactly " + expected
                ));
    }

    private List<String> required(Map<String, Object> schema) {
        Object required = schema.get("required");
        if (required == null) {
            return List.of();
        }
        assertThat(required).isInstanceOf(List.class);
        return strings(schema, "required");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castObject(Object value, String description) {
        assertThat(value).as(description).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
