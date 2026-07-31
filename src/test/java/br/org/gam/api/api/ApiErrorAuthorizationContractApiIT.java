package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Common error and authorization contract")
class ApiErrorAuthorizationContractApiIT extends MemberApiTestSupport {

    private static final List<String> ERROR_FIELDS =
            List.of("timestamp", "status", "code", "message", "details");

    @Test
    @DisplayName("REQ-API-ERROR-007/008/009 - protected malformed request -> authentication and coarse authorization precede parsing")
    void authenticationAndCoarseAuthorizationShouldPrecedeRequestParsing() {
        String malformedBody = "{\"filters\":[";
        AuthSession visitor = newSession("VISITOR");
        AuthSession coordinator = newSession("COORD");

        ExtractableResponse<Response> unauthenticated = jsonRequest()
                .body(malformedBody)
                .post("/members/search")
                .then()
                .extract();
        ExtractableResponse<Response> missingCoarsePermission = authenticatedJsonRequest(visitor)
                .body(malformedBody)
                .post("/members/search")
                .then()
                .extract();
        ExtractableResponse<Response> authorized = authenticatedJsonRequest(coordinator)
                .body(malformedBody)
                .post("/members/search")
                .then()
                .extract();

        assertError(unauthenticated, 401, "AUTHENTICATION_REQUIRED", Map.of());
        assertThat(unauthenticated.header("WWW-Authenticate")).isEqualTo("Bearer");

        assertError(missingCoarsePermission, 403, "ACCESS_DENIED", Map.of());
        assertThat(missingCoarsePermission.header("WWW-Authenticate")).isNull();

        assertMalformedJson(authorized, "SYNTAX_ERROR", null);
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-EVENT-005 - static route permission -> coarse denial precedes body parsing")
    void staticRoutePermissionShouldBeEvaluatedBeforeRequestParsing() {
        String malformedBody = "{\"title\":";
        AuthSession visitor = newSession("VISITOR");
        AuthSession eventCreator = newSessionWithPermissions("EVENT_CREATE");

        ExtractableResponse<Response> unauthenticated = jsonRequest()
                .body(malformedBody)
                .post("/events")
                .then()
                .extract();
        ExtractableResponse<Response> missingCoarsePermission = authenticatedJsonRequest(visitor)
                .body(malformedBody)
                .post("/events")
                .then()
                .extract();
        ExtractableResponse<Response> authorized = authenticatedJsonRequest(eventCreator)
                .body(malformedBody)
                .post("/events")
                .then()
                .extract();

        assertError(unauthenticated, 401, "AUTHENTICATION_REQUIRED", Map.of());
        assertError(missingCoarsePermission, 403, "ACCESS_DENIED", Map.of());
        assertMalformedJson(authorized, "SYNTAX_ERROR", null);
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-EVENT-009 - Event search without coarse permission -> denial precedes parsing")
    void eventSearchCoarseAuthorizationShouldPrecedeParsing() {
        AuthSession visitor = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .body("{\"filters\":[")
                .post("/events/search")
                .then()
                .extract();

        assertError(response, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-GAM-LOCATION-011 - GamLocation creation without coarse permission -> denial precedes parsing")
    void gamLocationCreationCoarseAuthorizationShouldPrecedeParsing() {
        AuthSession visitor = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .body("{\"name\":")
                .post("/gam-locations")
                .then()
                .extract();

        assertError(response, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-MEMBER-003 - Member registration without coarse permission -> denial precedes body parsing")
    void memberRegistrationCoarseAuthorizationShouldPrecedeParsing() {
        AuthSession visitor = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .body("{\"accountId\":")
                .post("/members")
                .then()
                .extract();

        assertError(response, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-MEMBER-SOL-008 - solicitation review without coarse permission -> denial precedes path and body parsing")
    void solicitationReviewCoarseAuthorizationShouldPrecedeParsing() {
        AuthSession visitor = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .body("{\"reason\":")
                .patch("/membership-solicitations/{id}/approve", "not-a-uuid-secret")
                .then()
                .extract();

        assertError(response, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 and REQ-ACCOUNT-ROLE-001 - Account-role mutation without coarse permission -> denial precedes path and body parsing")
    void accountRoleMutationCoarseAuthorizationShouldPrecedeParsing() {
        AuthSession visitor = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .body("{\"roleId\":")
                .post("/accounts/{accountId}/roles", "not-a-uuid-secret")
                .then()
                .extract();

        assertError(response, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007 and REQ-ACCOUNT-001/002 - Account self-view alternative -> coarse admission preserves parsing and target authorization")
    void accountSelfViewAlternativeShouldRemainReachableAfterCoarseAuthorization() {
        AuthSession visitor = newSession("VISITOR");
        UUID otherAccountId = newAccount("Other Account self-view target");

        ExtractableResponse<Response> unauthenticatedInvalidPath = jsonRequest()
                .get("/accounts/{accountId}", "not-a-uuid-secret")
                .then()
                .extract();
        ExtractableResponse<Response> authenticatedInvalidPath = authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}", "not-a-uuid-secret")
                .then()
                .extract();
        ExtractableResponse<Response> selfView = authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}", visitor.accountId())
                .then()
                .extract();
        ExtractableResponse<Response> forbiddenOther = authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}", otherAccountId)
                .then()
                .extract();

        assertError(unauthenticatedInvalidPath, 401, "AUTHENTICATION_REQUIRED", Map.of());
        assertError(authenticatedInvalidPath, 400, "INVALID_PARAMETER_TYPE", Map.of(
                "location", "path",
                "field", "accountId",
                "expectedType", "UUID"
        ));
        assertThat(selfView.statusCode()).isEqualTo(200);
        assertThat(selfView.<String>path("id")).isEqualTo(visitor.accountId().toString());
        assertError(forbiddenOther, 403, "ACCESS_DENIED", Map.of());
    }

    @Test
    @DisplayName("REQ-API-ERROR-007/009 - every accepted static-permission route -> coarse denial precedes transport parsing")
    void everyAcceptedStaticPermissionRouteShouldAuthorizeBeforeParsing() {
        AuthSession callerWithoutPermissions = newSessionWithPermissions();
        String invalidUuid = "not-a-uuid-secret";
        String malformedBody = "{\"reason\":";
        List<CoarsePermissionRoute> routes = List.of(
                new CoarsePermissionRoute(
                        "grant Coordinator", "PATCH",
                        "/members/{id}/coordinator/grant", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "revoke Coordinator", "PATCH",
                        "/members/{id}/coordinator/revoke", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "grant Oratorio Coordinator", "PATCH",
                        "/members/{id}/oratorio-coordinator/grant", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "revoke Oratorio Coordinator", "PATCH",
                        "/members/{id}/oratorio-coordinator/revoke", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "activate Member", "PATCH",
                        "/members/{id}/activate", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "deactivate Member", "PATCH",
                        "/members/{id}/deactivate", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get GamLocation", "GET",
                        "/gam-locations/{id}", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list GamLocations", "GET",
                        "/gam-locations?size=not-an-integer-secret", null
                ),
                new CoarsePermissionRoute(
                        "replace GamLocation", "PUT",
                        "/gam-locations/{id}", "{\"name\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "remove GamLocation", "DELETE",
                        "/gam-locations/{id}", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Account roles", "GET",
                        "/accounts/{accountId}/roles", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "drop Account role", "PATCH",
                        "/accounts/{accountId}/roles/{roleId}/drop",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Account role assignment", "GET",
                        "/accounts/{accountId}/role-assignments/{assignmentId}",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Role", "GET",
                        "/roles/{roleId}", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Role permissions", "GET",
                        "/roles/{roleId}/permissions", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Permission", "GET",
                        "/permissions/{permissionId}", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "replace Event", "PUT",
                        "/events/{id}", "{\"title\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "lock Event", "PATCH",
                        "/events/{id}/lock", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "finalize Event", "PATCH",
                        "/events/{id}/finalize", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "reopen Event", "PATCH",
                        "/events/{id}/reopen", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "cancel Event", "PATCH",
                        "/events/{id}/cancel", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "delete Event", "DELETE",
                        "/events/{id}", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "register Event presence", "POST",
                        "/events/{eventId}/presences", "{\"memberId\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Event presences", "GET",
                        "/events/{eventId}/presences?size=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Event presence", "GET",
                        "/events/{eventId}/presences/{memberId}",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "edit Event presence", "PATCH",
                        "/events/{eventId}/presences/{memberId}",
                        "{\"observations\":",
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "remove Event presence", "DELETE",
                        "/events/{eventId}/presences/{memberId}",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "create Oratorio", "POST",
                        "/oratorios", "{\"localDate\":"
                ),
                new CoarsePermissionRoute(
                        "get Oratorio", "GET",
                        "/oratorios/{oratorioId}", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "replace Oratorio planning", "PUT",
                        "/oratorios/{oratorioId}/planning", "{\"temaGeral\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "assign Oratorio team Member", "PUT",
                        "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}",
                        null,
                        invalidUuid,
                        "LANCHE",
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "remove Oratorio team Member", "DELETE",
                        "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}",
                        null,
                        invalidUuid,
                        "LANCHE",
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "lock Oratorio", "PATCH",
                        "/oratorios/{oratorioId}/lock", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "finalize Oratorio", "PATCH",
                        "/oratorios/{oratorioId}/finalize", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "reopen Oratorio", "PATCH",
                        "/oratorios/{oratorioId}/reopen", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "cancel Oratorio", "PATCH",
                        "/oratorios/{oratorioId}/cancel", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "delete Oratorio", "DELETE",
                        "/oratorios/{oratorioId}", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Oratorio Member attendance", "GET",
                        "/oratorios/{oratorioId}/attendance/members?page=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Oratorio Oratoriano attendance", "GET",
                        "/oratorios/{oratorioId}/attendance/oratorianos?page=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Oratorio present summary", "GET",
                        "/oratorios/{oratorioId}/attendance/present", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "mark Oratorio Member present", "PUT",
                        "/oratorios/{oratorioId}/attendance/members/{memberId}",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "uncheck Oratorio Member", "DELETE",
                        "/oratorios/{oratorioId}/attendance/members/{memberId}",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "mark Oratoriano present", "PUT",
                        "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "uncheck Oratoriano", "DELETE",
                        "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "register and mark Oratoriano", "POST",
                        "/oratorios/{oratorioId}/attendance/oratorianos/register-and-mark",
                        "{\"firstName\":",
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "register Oratoriano", "POST",
                        "/oratorianos", "{\"firstName\":"
                ),
                new CoarsePermissionRoute(
                        "get Oratoriano", "GET",
                        "/oratorianos/{oratorianoId}", null, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "replace Oratoriano", "PUT",
                        "/oratorianos/{oratorianoId}", "{\"firstName\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "delete Oratoriano", "DELETE",
                        "/oratorianos/{oratorianoId}", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "restore Oratoriano", "PATCH",
                        "/oratorianos/{oratorianoId}/restore", malformedBody, invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Oratoriano attendance history", "GET",
                        "/oratorianos/{oratorianoId}/attendances?size=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Oratoriano attendance summary", "GET",
                        "/oratorianos/{oratorianoId}/attendance-summary?month=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "search Oratorianos", "POST",
                        "/oratorianos/search", "{\"filters\":"
                ),
                new CoarsePermissionRoute(
                        "create Oratoriano form", "POST",
                        "/oratorianos/{oratorianoId}/forms", "{\"origin\":", invalidUuid
                ),
                new CoarsePermissionRoute(
                        "list Oratoriano form history", "GET",
                        "/oratorianos/{oratorianoId}/forms?page=not-an-integer-secret",
                        null,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "get Oratoriano form", "GET",
                        "/oratorianos/{oratorianoId}/forms/{formId}",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "replace Oratoriano form", "PUT",
                        "/oratorianos/{oratorianoId}/forms/{formId}",
                        "{\"data\":",
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "delete Oratoriano form", "DELETE",
                        "/oratorianos/{oratorianoId}/forms/{formId}",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "complete Oratoriano form", "PATCH",
                        "/oratorianos/{oratorianoId}/forms/{formId}/complete",
                        "{\"printSnapshotId\":",
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "revoke Oratoriano form", "PATCH",
                        "/oratorianos/{oratorianoId}/forms/{formId}/revoke",
                        malformedBody,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "create Oratoriano form print snapshot", "POST",
                        "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "render Oratoriano form PDF", "GET",
                        "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots/{printSnapshotId}/pdf",
                        null,
                        invalidUuid,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "replace Oratoriano form signed attachments", "PUT",
                        "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments",
                        null,
                        invalidUuid,
                        invalidUuid
                ),
                new CoarsePermissionRoute(
                        "download Oratoriano form signed attachment", "GET",
                        "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}",
                        null,
                        invalidUuid,
                        invalidUuid,
                        invalidUuid
                )
        );
        SoftAssertions assertions = new SoftAssertions();

        for (CoarsePermissionRoute route : routes) {
            var request = authenticatedJsonRequest(callerWithoutPermissions);
            if (route.body() != null) {
                request.body(route.body());
            }
            ExtractableResponse<Response> response = request
                    .request(route.method(), route.path(), route.pathParameters())
                    .then()
                    .extract();
            Map<String, Object> error = response.jsonPath().getMap("$");

            assertions.assertThat(response.statusCode())
                    .as(route.label() + " status")
                    .isEqualTo(403);
            assertions.assertThat(error.get("code"))
                    .as(route.label() + " code")
                    .isEqualTo("ACCESS_DENIED");
            assertions.assertThat(error.get("details"))
                    .as(route.label() + " details")
                    .isEqualTo(Map.of());
            assertions.assertThat(response.header("Cache-Control"))
                    .as(route.label() + " Cache-Control")
                    .containsIgnoringCase("no-store");
            assertions.assertThat(response.header("WWW-Authenticate"))
                    .as(route.label() + " bearer challenge")
                    .isNull();
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-API-ERROR-003/004 - independent body constraints -> complete deterministic public violations")
    void validationErrorShouldReturnAllDeterministicPublicViolations() {
        ExtractableResponse<Response> response = jsonRequest()
                .body("""
                        {
                          "email": null,
                          "password": "",
                          "displayName": null
                        }
                        """)
                .post("/auth/register")
                .then()
                .extract();

        assertErrorEnvelope(response, 400, "VALIDATION_ERROR");
        Map<String, Object> details = response.jsonPath().getMap("details");
        assertThat(details).containsOnlyKeys("violations");
        List<Map<String, Object>> violations = response.jsonPath().getList("details.violations");
        assertThat(violations).allSatisfy(violation ->
                assertThat(violation).containsOnlyKeys("location", "field", "code", "message"));
        assertThat(violations)
                .extracting(violation -> List.of(
                        violation.get("location"),
                        violation.get("field"),
                        violation.get("code")
                ))
                .containsExactly(
                        List.of("body", "/displayName", "REQUIRED"),
                        List.of("body", "/email", "REQUIRED"),
                        List.of("body", "/password", "NOT_BLANK"),
                        List.of("body", "/password", "SIZE")
                );
        assertSafe(response, "", "RegisterAccountDTO", "NotNull", "NotBlank", "Size");
    }

    @Test
    @DisplayName("REQ-API-ERROR-003/004 - invalid nonzero filter item -> submitted index in public validation paths")
    void nestedValidationShouldUseSubmittedCollectionIndexes() {
        AuthSession coordinator = newSession("COORD");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body("""
                        {
                          "filters": [
                            {
                              "field": "status",
                              "value": "ACTIVE",
                              "comparisonMethod": "EQUALS"
                            },
                            {
                              "field": null,
                              "value": null,
                              "comparisonMethod": null
                            }
                          ]
                        }
                        """)
                .post("/members/search")
                .then()
                .extract();

        assertErrorEnvelope(response, 400, "VALIDATION_ERROR");
        List<Map<String, Object>> violations = response.jsonPath().getList("details.violations");
        assertThat(violations)
                .extracting(violation -> List.of(
                        violation.get("location"),
                        violation.get("field"),
                        violation.get("code")
                ))
                .containsExactly(
                        List.of("body", "/filters/1/comparisonMethod", "REQUIRED"),
                        List.of("body", "/filters/1/field", "REQUIRED"),
                        List.of("body", "/filters/1/value", "REQUIRED")
                );
        assertThat(violations).allSatisfy(violation ->
                assertThat(violation).containsOnlyKeys("location", "field", "code", "message"));
        assertSafe(response, "", "SearchDTO", "SpecificationFilterDTO", "NotNull");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-AUTH-002 - oversized Account displayName -> public SIZE violation")
    void accountDisplayNameConstraintShouldUseStructuredValidationViolation() {
        String submittedDisplayName = "x".repeat(51);

        ExtractableResponse<Response> response = jsonRequest()
                .body(Map.of(
                        "email", "invalid-display-name-" + UUID.randomUUID() + "@example.com",
                        "password", TEST_PASSWORD,
                        "displayName", submittedDisplayName
                ))
                .post("/auth/register")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/displayName", "SIZE");
        assertSafe(response, "", submittedDisplayName, "IllegalArgumentException", "RegisterAccount");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-AUTH-002 - correctly typed invalid email -> public FORMAT violation")
    void invalidEmailSyntaxShouldUseStructuredValidationViolation() {
        String submittedEmail = "not-an-email-secret";

        ExtractableResponse<Response> response = jsonRequest()
                .body(Map.of(
                        "email", submittedEmail,
                        "password", TEST_PASSWORD,
                        "displayName", "Invalid Email"
                ))
                .post("/auth/register")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/email", "FORMAT");
        assertSafe(response, "", submittedEmail, "GamEmail", "IllegalArgumentException");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-OPENAPI-007 - oversized page -> RANGE query violation")
    void paginationRangeFailureShouldUseStructuredValidationViolation() {
        AuthSession administrator = newSession("SUDO");

        ExtractableResponse<Response> oversizedPage = authenticatedJsonRequest(administrator)
                .get("/gam-locations?size=101")
                .then()
                .extract();

        assertSingleViolation(oversizedPage, "query", "size", "RANGE");
        assertSafe(oversizedPage, "", "101", "IllegalArgumentException");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/006 and REQ-OPENAPI-007 - non-integer page size -> public query transport details")
    void invalidPaginationTransportTypeShouldUseStructuredParameterDetails() {
        AuthSession administrator = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(administrator)
                .get("/gam-locations?size=not-an-integer-secret")
                .then()
                .extract();

        assertError(response, 400, "INVALID_PARAMETER_TYPE", Map.of(
                "location", "query",
                "field", "size",
                "expectedType", "INTEGER"
        ));
        assertSafe(response, "", "not-an-integer-secret", "NumberFormatException", "java.lang.Integer");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/006 and REQ-OPENAPI-007 - non-integer page -> public query transport details")
    void invalidPageTransportTypeShouldUseStructuredParameterDetails() {
        AuthSession administrator = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(administrator)
                .get("/gam-locations?page=not-an-integer-secret")
                .then()
                .extract();

        assertError(response, 400, "INVALID_PARAMETER_TYPE", Map.of(
                "location", "query",
                "field", "page",
                "expectedType", "INTEGER"
        ));
        assertSafe(response, "", "not-an-integer-secret", "NumberFormatException", "java.lang.Integer");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-OPENAPI-007 - negative page -> RANGE query violation")
    void negativePageShouldUseStructuredValidationViolation() {
        AuthSession administrator = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(administrator)
                .get("/gam-locations?page=-1")
                .then()
                .extract();

        assertSingleViolation(response, "query", "page", "RANGE");
        assertSafe(response, "", "IllegalArgumentException", "Pageable");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-OPENAPI-007 - unknown sort -> ALLOWED_VALUE query violation")
    void unknownSortFailureShouldUseStructuredValidationViolation() {
        AuthSession administrator = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(administrator)
                .get("/gam-locations?sort=internalPersistenceField,asc")
                .then()
                .extract();

        assertSingleViolation(response, "query", "sort", "ALLOWED_VALUE");
        assertSafe(response, "", "internalPersistenceField", "IllegalArgumentException");
    }

    @Test
    @DisplayName("REQ-API-ERROR-003/004 and REQ-MEMBER-SOL-002 - prohibited accountId -> INVALID_VALUE body violation")
    void prohibitedRequestMemberShouldNotBeReportedAsRequired() {
        AuthSession applicant = newSession("VISITOR");
        UUID submittedAccountId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "Ana");
        payload.put("surname", "Silva");
        payload.put("birthDate", LocalDate.now().minusYears(20).toString());
        payload.put("phoneNumber", "+5519998877665");
        payload.put("justification", "I want to participate in GAM activities");
        payload.put("accountId", submittedAccountId.toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body(payload)
                .post("/membership-solicitations")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/accountId", "INVALID_VALUE");
        assertSafe(
                response,
                "",
                submittedAccountId.toString(),
                "SubmitMembershipSolicitationDTO",
                "jakarta.validation.constraints.Null"
        );
    }

    @Test
    @DisplayName("REQ-API-ERROR-004 and REQ-MEMBER-SOL-002 - explicit null prohibited accountId -> INVALID_VALUE")
    void explicitNullProhibitedRequestMemberShouldRemainInvalidValue() {
        AuthSession applicant = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body("""
                        {
                          "firstName": "Ana",
                          "surname": "Silva",
                          "birthDate": "%s",
                          "phoneNumber": "+5519998877665",
                          "justification": "I want to participate in GAM activities",
                          "accountId": null
                        }
                        """.formatted(LocalDate.now().minusYears(20)))
                .post("/membership-solicitations")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/accountId", "INVALID_VALUE");
        assertSafe(response, "", "SubmitMembershipSolicitationDTO", "jakarta.validation.constraints.Null");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-MEMBER-SOL-002 - underage solicitation -> public birthDate RANGE violation")
    void solicitationAgeConstraintShouldUseStructuredValidationViolation() {
        AuthSession applicant = newSession("VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body(solicitationPayload(
                        LocalDate.now().minusYears(17).plusDays(1),
                        VALID_JUSTIFICATION
                ))
                .post("/membership-solicitations")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/birthDate", "RANGE");
        assertSafe(response, "", "IllegalArgumentException", "Member");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-GAM-NAME-004 - invalid solicitation name -> public firstName FORMAT violation")
    void solicitationNameFormatConstraintShouldUseStructuredValidationViolation() {
        AuthSession applicant = newSession("VISITOR");
        Map<String, Object> payload = new LinkedHashMap<>(solicitationPayload(
                LocalDate.now().minusYears(20),
                VALID_JUSTIFICATION
        ));
        payload.put("firstName", "Ana1");

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body(payload)
                .post("/membership-solicitations")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/firstName", "FORMAT");
        assertSafe(response, "", "Ana1", "IllegalArgumentException", "GamName");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-MEMBER-002 - underage Member -> public birthDate RANGE violation")
    void memberAgeConstraintShouldUseStructuredValidationViolation() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Underage Member validation target");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(memberPayload(
                        accountId,
                        LocalDate.now().minusYears(17).plusDays(1),
                        "Register eligible Member"
                ))
                .post("/members")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/birthDate", "RANGE");
        assertSafe(response, "", "IllegalArgumentException", "Member");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-GAM-NAME-004 - invalid Member name -> public firstName FORMAT violation")
    void memberNameFormatConstraintShouldUseStructuredValidationViolation() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Invalid Member name target");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(memberPayload(
                        accountId,
                        "Maria1",
                        "Silva",
                        LocalDate.now().minusYears(20),
                        "+5519998877665",
                        "Register Member with valid identity"
                ))
                .post("/members")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/firstName", "FORMAT");
        assertSafe(response, "", "Maria1", "IllegalArgumentException", "GamName");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-GAM-PHONE-004 - invalid Member phone -> public phoneNumber FORMAT violation")
    void memberPhoneFormatConstraintShouldUseStructuredValidationViolation() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Invalid Member phone target");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(memberPayload(
                        accountId,
                        "Ana",
                        "Silva",
                        LocalDate.now().minusYears(20),
                        "abc",
                        "Register Member with valid contact details"
                ))
                .post("/members")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/phoneNumber", "FORMAT");
        assertSafe(response, "", "abc", "IllegalArgumentException", "GamPhoneNumber");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-EVENT-003 - invalid Event date relationship -> request-wide RELATION violation")
    void eventDateRelationshipShouldUseStructuredValidationViolation() {
        AuthSession eventCreator = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID locationId = createGamLocationForResourceSecurity(
                eventCreator,
                "Event date validation location"
        );
        Instant beginDate = Instant.parse("2030-01-01T10:00:00Z");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Invalid date relationship");
        payload.put("description", "The Event range must be ordered");
        payload.put("gamLocationId", locationId.toString());
        payload.put("requiredPermissionId", null);
        payload.put("beginDate", beginDate.toString());
        payload.put("endDate", beginDate.toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(eventCreator)
                .body(payload)
                .post("/events")
                .then()
                .extract();

        assertSingleViolation(response, "body", "$", "RELATION");
        assertSafe(response, "", beginDate.toString(), "IllegalArgumentException", "Event");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-EVENT-003 - oversized Event title -> public title SIZE violation")
    void eventTitleConstraintShouldUseStructuredValidationViolation() {
        AuthSession eventCreator = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID locationId = createGamLocationForResourceSecurity(
                eventCreator,
                "Event title validation location"
        );
        Instant beginDate = Instant.parse("2030-01-01T10:00:00Z");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "t".repeat(256));
        payload.put("description", "The Event title must use the public size contract");
        payload.put("gamLocationId", locationId.toString());
        payload.put("requiredPermissionId", null);
        payload.put("beginDate", beginDate.toString());
        payload.put("endDate", beginDate.plusSeconds(3_600).toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(eventCreator)
                .body(payload)
                .post("/events")
                .then()
                .extract();

        assertSingleViolation(response, "body", "/title", "SIZE");
        assertSafe(response, "", "t".repeat(256), "IllegalArgumentException", "Event");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-EVENT-014 - invalid replacement date relationship -> request-wide RELATION violation")
    void eventReplacementDateRelationshipShouldUseStructuredValidationViolation() {
        AuthSession eventManager = newSessionWithPermissions(
                "GAM_LOCATION_CREATE",
                "EVENT_CREATE",
                "EVENT_MANAGE"
        );
        UUID locationId = createGamLocationForResourceSecurity(
                eventManager,
                "Event replacement validation location"
        );
        Instant beginDate = Instant.parse("2030-01-01T10:00:00Z");
        Map<String, Object> validEvent = new LinkedHashMap<>();
        validEvent.put("title", "Valid Event before replacement");
        validEvent.put("description", "The original Event has an ordered date range");
        validEvent.put("gamLocationId", locationId.toString());
        validEvent.put("requiredPermissionId", null);
        validEvent.put("beginDate", beginDate.toString());
        validEvent.put("endDate", beginDate.plusSeconds(3_600).toString());

        ExtractableResponse<Response> created = authenticatedJsonRequest(eventManager)
                .body(validEvent)
                .post("/events")
                .then()
                .statusCode(201)
                .extract();
        UUID eventId = UUID.fromString(created.path("id"));
        trackEvent(eventId);

        Map<String, Object> invalidReplacement = new LinkedHashMap<>(validEvent);
        invalidReplacement.put("title", "Invalid Event replacement");
        invalidReplacement.put("endDate", beginDate.toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(eventManager)
                .body(invalidReplacement)
                .put("/events/{id}", eventId)
                .then()
                .extract();

        assertSingleViolation(response, "body", "$", "RELATION");
        assertSafe(response, "", beginDate.toString(), "IllegalArgumentException", "Event");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-EVENT-014 - oversized replacement description -> public description SIZE violation")
    void eventReplacementDescriptionConstraintShouldUseStructuredValidationViolation() {
        AuthSession eventManager = newSessionWithPermissions(
                "GAM_LOCATION_CREATE",
                "EVENT_CREATE",
                "EVENT_MANAGE"
        );
        UUID locationId = createGamLocationForResourceSecurity(
                eventManager,
                "Event description validation location"
        );
        Instant beginDate = Instant.parse("2030-01-01T10:00:00Z");
        Map<String, Object> validEvent = new LinkedHashMap<>();
        validEvent.put("title", "Valid Event before description replacement");
        validEvent.put("description", "The original Event has a valid description");
        validEvent.put("gamLocationId", locationId.toString());
        validEvent.put("requiredPermissionId", null);
        validEvent.put("beginDate", beginDate.toString());
        validEvent.put("endDate", beginDate.plusSeconds(3_600).toString());

        ExtractableResponse<Response> created = authenticatedJsonRequest(eventManager)
                .body(validEvent)
                .post("/events")
                .then()
                .statusCode(201)
                .extract();
        UUID eventId = UUID.fromString(created.path("id"));
        trackEvent(eventId);

        Map<String, Object> invalidReplacement = new LinkedHashMap<>(validEvent);
        invalidReplacement.put("description", "d".repeat(10_001));

        ExtractableResponse<Response> response = authenticatedJsonRequest(eventManager)
                .body(invalidReplacement)
                .put("/events/{id}", eventId)
                .then()
                .extract();

        assertSingleViolation(response, "body", "/description", "SIZE");
        assertSafe(response, "", "d".repeat(10_001), "IllegalArgumentException", "Event");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/005 - malformed JSON categories -> safe reason, location, and public pointer")
    void malformedJsonShouldExposeOnlySafeStructuredDetails() {
        AuthSession coordinator = newSession("COORD");

        ExtractableResponse<Response> syntaxError = authenticatedJsonRequest(coordinator)
                .body("{\"filters\":[")
                .post("/members/search")
                .then()
                .extract();
        ExtractableResponse<Response> unknownField = authenticatedJsonRequest(coordinator)
                .body("""
                        {
                          "filters": [],
                          "account.passwordHash": "do-not-echo"
                        }
                        """)
                .post("/members/search")
                .then()
                .extract();
        ExtractableResponse<Response> typeMismatch = authenticatedJsonRequest(coordinator)
                .body("""
                        {
                          "filters": "not-an-array"
                        }
                        """)
                .post("/members/search")
                .then()
                .extract();

        assertMalformedJson(syntaxError, "SYNTAX_ERROR", null);
        assertMalformedJson(unknownField, "UNKNOWN_FIELD", null);
        assertMalformedJson(typeMismatch, "TYPE_MISMATCH", "/filters");
        assertSafe(
                syntaxError,
                unknownField.asString() + typeMismatch.asString(),
                "account.passwordHash",
                "do-not-echo",
                "ArrayList",
                "SearchDTO",
                "SpecificationFilterDTO",
                "com.fasterxml.jackson"
        );
    }

    @Test
    @DisplayName("REQ-API-ERROR-012 and REQ-SEARCH-009/010 - semantic search error -> no-store JSON transport")
    void invalidSearchFilterShouldUseTheSharedNonCacheableErrorTransport() {
        AuthSession coordinator = newSession("COORD");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter(
                        "account.passwordHash",
                        "do-not-echo",
                        "EQUALS"
                )))
                .post("/members/search")
                .then()
                .extract();

        assertError(response, 400, "INVALID_SEARCH_FILTER", Map.of("filterIndex", 0));
        assertSafe(response, "", "account.passwordHash", "do-not-echo");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/006 - invalid path transport value -> documented external parameter and type")
    void invalidParameterTypeShouldExposeThePublicTransportContract() {
        AuthSession coordinator = newSession("COORD");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}", "not-a-uuid-secret")
                .then()
                .extract();

        assertError(response, 400, "INVALID_PARAMETER_TYPE", Map.of(
                "location", "path",
                "field", "roleId",
                "expectedType", "UUID"
        ));
        assertSafe(response, "", "not-a-uuid-secret", "java.util.UUID", "MethodArgumentTypeMismatchException");
    }

    @Test
    @DisplayName("REQ-API-ERROR-008 and REQ-AUTH-006 - unknown email and wrong password -> indistinguishable INVALID_CREDENTIALS")
    void invalidCredentialsShouldNotRevealWhetherTheAccountExists() {
        AuthSession knownAccount = newSession("VISITOR");
        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> wrongPassword = csrfRequest(csrfToken)
                .body(loginPayload(knownAccount.email(), "incorrect-password"))
                .post("/auth/login")
                .then()
                .extract();
        ExtractableResponse<Response> unknownEmail = csrfRequest(csrfToken)
                .body(loginPayload("missing-" + UUID.randomUUID() + "@example.com", "incorrect-password"))
                .post("/auth/login")
                .then()
                .extract();

        assertError(wrongPassword, 401, "INVALID_CREDENTIALS", Map.of());
        assertError(unknownEmail, 401, "INVALID_CREDENTIALS", Map.of());
        assertThat(stableError(unknownEmail)).isEqualTo(stableError(wrongPassword));
        assertThat(wrongPassword.header("WWW-Authenticate")).isNull();
        assertThat(unknownEmail.header("WWW-Authenticate")).isNull();
    }

    @Test
    @DisplayName("REQ-API-ERROR-008 and REQ-AUTH-016 - missing and malformed refresh cookie -> indistinguishable INVALID_REFRESH_TOKEN")
    void invalidRefreshTokensShouldRequireSignInWithoutBearerChallenge() {
        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> missing = csrfRequest(csrfToken)
                .post("/auth/refresh")
                .then()
                .extract();
        ExtractableResponse<Response> malformed = csrfRequest(csrfToken)
                .cookie("refreshToken", "not-a-refresh-token-secret")
                .post("/auth/refresh")
                .then()
                .extract();

        assertError(missing, 401, "INVALID_REFRESH_TOKEN", Map.of());
        assertError(malformed, 401, "INVALID_REFRESH_TOKEN", Map.of());
        assertThat(stableError(malformed)).isEqualTo(stableError(missing));
        assertThat(missing.header("WWW-Authenticate")).isNull();
        assertThat(malformed.header("WWW-Authenticate")).isNull();
        assertSafe(missing, malformed.asString(), "not-a-refresh-token-secret");
    }

    @Test
    @DisplayName("REQ-API-ERROR-009 and REQ-BROWSER-AUTH-004 - CSRF and origin rejection -> indistinguishable REQUEST_SECURITY_REJECTED")
    void requestSecurityFailuresShouldNotIdentifyWhichProofFailed() {
        AuthSession account = newSession("VISITOR");
        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> csrfMismatch = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken + "-mismatch")
                .body(loginPayload(account.email(), account.password()))
                .post("/auth/login")
                .then()
                .extract();
        ExtractableResponse<Response> originMismatch = csrfRequest(csrfToken, UNTRUSTED_ORIGIN, null)
                .body(loginPayload(account.email(), account.password()))
                .post("/auth/login")
                .then()
                .extract();

        assertError(csrfMismatch, 403, "REQUEST_SECURITY_REJECTED", Map.of());
        assertError(originMismatch, 403, "REQUEST_SECURITY_REJECTED", Map.of());
        assertThat(stableError(originMismatch)).isEqualTo(stableError(csrfMismatch));
        assertThat(csrfMismatch.header("WWW-Authenticate")).isNull();
        assertThat(originMismatch.header("WWW-Authenticate")).isNull();
    }

    @Test
    @DisplayName("REQ-API-ERROR-009 - missing authority and prohibited transition -> distinct stable forbidden codes")
    void authorizationAndInvariantFailuresShouldUseDistinctForbiddenCodes() {
        AuthSession visitor = newSession("VISITOR");
        AuthSession coordinator = newSession("COORD");
        UUID targetAccountId = newAccount("Forbidden contract target");
        UUID systemRoleId = roleId("MEMBER");

        ExtractableResponse<Response> accessDenied = authenticatedJsonRequest(visitor)
                .body(Map.of("filters", List.of()))
                .post("/accounts/search")
                .then()
                .extract();
        ExtractableResponse<Response> forbiddenOperation = authenticatedJsonRequest(coordinator)
                .body(Map.of(
                        "roleId", systemRoleId.toString(),
                        "reason", "Attempt lifecycle-owned role administration"
                ))
                .post("/accounts/{accountId}/roles", targetAccountId)
                .then()
                .extract();

        assertError(accessDenied, 403, "ACCESS_DENIED", Map.of());
        assertError(forbiddenOperation, 403, "FORBIDDEN_OPERATION", Map.of());
        assertThat(accessDenied.header("WWW-Authenticate")).isNull();
        assertThat(forbiddenOperation.header("WWW-Authenticate")).isNull();
    }

    @Test
    @DisplayName("REQ-API-ERROR-010 and REQ-MEMBER-011/013 - absent, soft-deleted, and status-hidden Member -> indistinguishable 404")
    void absentDeletedAndHiddenMembersShouldBeIndistinguishable() {
        AuthSession coordinator = newSession("COORD");
        AuthSession activeOnlyReader = newSessionWithPermissions("MEMBER_GET");

        UUID deletedAccountId = newAccount("Deleted Member error target");
        UUID deletedMemberId = registerMember(coordinator, deletedAccountId);
        softDeleteMember(deletedMemberId);

        UUID hiddenAccountId = newAccount("Hidden Member error target");
        UUID hiddenMemberId = registerMember(coordinator, hiddenAccountId);
        forceMemberState(hiddenMemberId, hiddenAccountId, "INACTIVE", "VISITOR");

        UUID absentMemberId = UUID.randomUUID();
        ExtractableResponse<Response> absent = memberLookup(activeOnlyReader, absentMemberId);
        ExtractableResponse<Response> deleted = memberLookup(activeOnlyReader, deletedMemberId);
        ExtractableResponse<Response> hidden = memberLookup(activeOnlyReader, hiddenMemberId);

        assertNotFound(absent, absentMemberId);
        assertNotFound(deleted, deletedMemberId);
        assertNotFound(hidden, hiddenMemberId);

        assertThat(List.of(
                absent.path("message"),
                deleted.path("message"),
                hidden.path("message")
        )).containsOnly((Object) absent.path("message"));
        assertThat(responseTopology(absent))
                .isEqualTo(responseTopology(deleted))
                .isEqualTo(responseTopology(hidden));
    }

    private ExtractableResponse<Response> memberLookup(AuthSession session, UUID memberId) {
        return authenticatedJsonRequest(session)
                .get("/members/{memberId}", memberId)
                .then()
                .extract();
    }

    private void assertNotFound(ExtractableResponse<Response> response, UUID memberId) {
        assertError(response, 404, "RESOURCE_NOT_FOUND", Map.of(
                "resource", "Member",
                "identifier", memberId.toString()
        ));
    }

    private record CoarsePermissionRoute(
            String label,
            String method,
            String path,
            String body,
            Object... pathParameters
    ) {
    }

    private void assertMalformedJson(
            ExtractableResponse<Response> response,
            String reason,
            String field
    ) {
        Map<String, Object> expectedDetails = new LinkedHashMap<>();
        expectedDetails.put("reason", reason);
        expectedDetails.put("location", "body");
        if (field != null) {
            expectedDetails.put("field", field);
        }
        assertError(response, 400, "MALFORMED_JSON", expectedDetails);
    }

    private void assertSingleViolation(
            ExtractableResponse<Response> response,
            String location,
            String field,
            String code
    ) {
        assertErrorEnvelope(response, 400, "VALIDATION_ERROR");
        Map<String, Object> details = response.jsonPath().getMap("details");
        assertThat(details).containsOnlyKeys("violations");
        List<Map<String, Object>> violations = response.jsonPath().getList("details.violations");
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation)
                    .containsOnlyKeys("location", "field", "code", "message")
                    .containsEntry("location", location)
                    .containsEntry("field", field)
                    .containsEntry("code", code);
            assertThat(violation.get("message")).isInstanceOf(String.class);
            assertThat(String.valueOf(violation.get("message"))).isNotBlank();
        });
    }

    private void assertError(
            ExtractableResponse<Response> response,
            int status,
            String code,
            Map<String, ?> expectedDetails
    ) {
        Map<String, Object> error = response.jsonPath().getMap("$");
        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(response.statusCode()).isEqualTo(status);
        assertions.assertThat(response.contentType()).startsWith("application/json");
        assertions.assertThat(response.contentType()).doesNotContain("application/problem+json");
        assertions.assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        assertions.assertThat(error).containsOnlyKeys(ERROR_FIELDS.toArray(String[]::new));
        assertions.assertThat(error).containsEntry("status", status).containsEntry("code", code);
        assertions.assertThat(error.get("timestamp")).isInstanceOf(String.class);
        assertions.assertThat(String.valueOf(error.get("timestamp"))).endsWith("Z");
        assertions.assertThat(error.get("message")).isInstanceOf(String.class);
        assertions.assertThat(String.valueOf(error.get("message"))).isNotBlank();
        assertions.assertThat(error.get("details")).isEqualTo(expectedDetails);
        assertions.assertAll();
    }

    private void assertErrorEnvelope(
            ExtractableResponse<Response> response,
            int status,
            String code
    ) {
        Map<String, Object> error = response.jsonPath().getMap("$");
        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(response.statusCode()).isEqualTo(status);
        assertions.assertThat(response.contentType()).startsWith("application/json");
        assertions.assertThat(response.contentType()).doesNotContain("application/problem+json");
        assertions.assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        assertions.assertThat(error).containsOnlyKeys(ERROR_FIELDS.toArray(String[]::new));
        assertions.assertThat(error).containsEntry("status", status).containsEntry("code", code);
        assertions.assertThat(error.get("timestamp")).isInstanceOf(String.class);
        assertions.assertThat(String.valueOf(error.get("timestamp"))).endsWith("Z");
        assertions.assertThat(error.get("message")).isInstanceOf(String.class);
        assertions.assertThat(String.valueOf(error.get("message"))).isNotBlank();
        assertions.assertThat(error.get("details")).isInstanceOf(Map.class);
        assertions.assertAll();
    }

    private Map<String, Object> stableError(ExtractableResponse<Response> response) {
        Map<String, Object> stable = new LinkedHashMap<>(response.jsonPath().getMap("$"));
        stable.remove("timestamp");
        return stable;
    }

    private Map<String, Object> responseTopology(ExtractableResponse<Response> response) {
        Map<String, Object> topology = stableError(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = new LinkedHashMap<>((Map<String, Object>) topology.get("details"));
        details.put("identifier", "<supplied identifier>");
        topology.put("details", details);
        return topology;
    }

    private void assertSafe(
            ExtractableResponse<Response> first,
            String additionalPayload,
            String... forbiddenFragments
    ) {
        List<String> payloads = new ArrayList<>();
        payloads.add(first.asString());
        payloads.add(additionalPayload);
        String combined = String.join("\n", payloads);
        assertThat(combined).doesNotContain(forbiddenFragments);
    }
}
