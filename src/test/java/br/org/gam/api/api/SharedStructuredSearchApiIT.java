package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - Shared structured-search grammar")
class SharedStructuredSearchApiIT extends MemberApiTestSupport {

    private static final List<String> SEARCH_ROUTES = List.of(
            "/accounts/search",
            "/events/search",
            "/members/search",
            "/membership-solicitations/search",
            "/oratorianos/search"
    );

    @Test
    @DisplayName("REQ-SEARCH-001/002 - canonical comparisonMethod -> accepted across every structured-search endpoint")
    void canonicalComparisonMethodShouldBeAcceptedAcrossEveryStructuredSearchEndpoint() {
        AuthSession caller = newSession("SUDO");
        Map<String, Object> request = request(List.of(filter(
                "id",
                UUID.randomUUID().toString(),
                "EQUALS"
        )));

        for (String route : SEARCH_ROUTES) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(request)
                    .post(route)
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("%s canonical comparisonMethod response: %s", route, response.asString())
                    .isEqualTo(200);
            assertThat(response.<List<?>>path("items")).as(route).isEmpty();
        }
    }

    @Test
    @DisplayName("REQ-SEARCH-006 - trimmed uppercase canonical UUID -> accepted across every endpoint")
    void trimmedUppercaseCanonicalUuidShouldBeAcceptedAcrossEveryStructuredSearchEndpoint() {
        AuthSession caller = newSession("SUDO");
        String canonicalUuid = "  " + UUID.randomUUID().toString().toUpperCase() + "  ";
        Map<String, Object> request = request(List.of(filter("id", canonicalUuid, "EQUALS")));

        for (String route : SEARCH_ROUTES) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(request)
                    .post(route)
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("%s trimmed uppercase UUID response: %s", route, response.asString())
                    .isEqualTo(200);
            assertThat(response.<List<?>>path("items")).as(route).isEmpty();
        }
    }

    @Test
    @DisplayName("REQ-SEARCH-002/009 - legacy comparationMethod -> MALFORMED_JSON across every endpoint")
    void legacyComparationMethodShouldBeRejectedAcrossEveryStructuredSearchEndpoint() {
        AuthSession caller = newSession("SUDO");
        Map<String, Object> legacyFilter = new LinkedHashMap<>();
        legacyFilter.put("field", "id");
        legacyFilter.put("value", UUID.randomUUID().toString());
        legacyFilter.put("comparationMethod", "EQUALS");

        for (String route : SEARCH_ROUTES) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(request(List.of(legacyFilter)))
                    .post(route)
                    .then()
                    .extract();

            assertError(response, 400, "MALFORMED_JSON");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRequestCases")
    @DisplayName("REQ-SEARCH-002/009 - invalid JSON shape or unknown property -> MALFORMED_JSON")
    void malformedSharedRequestShouldUseMalformedJson(String scenario, Object body) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body)
                .post("/accounts/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(400);
        assertThat(response.<String>path("code")).as(scenario).isEqualTo("MALFORMED_JSON");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongRequiredStringTypeCases")
    @DisplayName("REQ-SEARCH-002/009/011 - wrong field or comparisonMethod JSON type -> MALFORMED_JSON before semantics")
    void wrongRequiredStringTypeShouldBeMalformedBeforeSemanticValidation(
            String scenario,
            String property,
            Object wrongType
    ) {
        AuthSession caller = newSession("SUDO");
        Map<String, Object> malformedFilter = new LinkedHashMap<>(
                filter("id", UUID.randomUUID().toString(), "EQUALS")
        );
        malformedFilter.put(property, wrongType);
        String sensitiveField = "semantic-trap.internalPath";
        String sensitiveValue = "do-not-disclose";
        Map<String, Object> body = request(List.of(
                filter(sensitiveField, sensitiveValue, "UNKNOWN_METHOD"),
                malformedFilter
        ));

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body)
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "MALFORMED_JSON");
        Map<String, Object> details = response.path("details");
        String message = response.path("message");
        boolean identifiesFilterIndex = Integer.valueOf(1).equals(
                details == null ? null : details.get("filterIndex")
        )
                || message.contains("filters[1]");
        assertThat(identifiesFilterIndex)
                .as("%s must safely identify malformed filter index 1: %s", scenario, response.asString())
                .isTrue();
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain(
                        sensitiveField,
                        sensitiveValue,
                        "UNKNOWN_METHOD",
                        String.valueOf(wrongType),
                        "JsonToken",
                        "StrictStringDeserializer",
                        "com.fasterxml",
                        "java.lang"
                );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFilterIndexBoundaryCases")
    @DisplayName("REQ-SEARCH-002/009 - malformed required strings preserve boundary filter indexes safely")
    void malformedRequiredStringShouldPreserveBoundaryFilterIndex(
            String scenario,
            int malformedIndex,
            String property,
            Object wrongType
    ) {
        AuthSession caller = newSession("SUDO");
        List<Map<String, Object>> filters = new ArrayList<>();
        for (int index = 0; index < malformedIndex; index++) {
            filters.add(filter("id", UUID.randomUUID().toString(), "EQUALS"));
        }
        Map<String, Object> malformedFilter = new LinkedHashMap<>(
                filter("id", UUID.randomUUID().toString(), "EQUALS")
        );
        malformedFilter.put(property, wrongType);
        filters.add(malformedFilter);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(filters))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "MALFORMED_JSON");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsExactly(Map.entry("filterIndex", malformedIndex));
        assertThat(response.<String>path("message"))
                .contains("filters[" + malformedIndex + "]." + property);
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain(
                        "nested-secret",
                        "submitted-secret",
                        "JsonToken",
                        "StrictStringDeserializer",
                        "com.fasterxml",
                        "java.lang"
                );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullRequiredStringMemberCases")
    @DisplayName("REQ-SEARCH-002/009/011 - null required string member -> VALIDATION_ERROR before semantics")
    void nullRequiredStringMemberShouldBeValidatedBeforeSemanticFilters(
            String scenario,
            String property
    ) {
        AuthSession caller = newSession("SUDO");
        Map<String, Object> nullMemberFilter = new LinkedHashMap<>(
                filter("id", UUID.randomUUID().toString(), "EQUALS")
        );
        nullMemberFilter.put(property, null);
        String sensitiveField = "semantic-trap.internalPath";
        String sensitiveValue = "do-not-disclose";

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(
                        filter(sensitiveField, sensitiveValue, "UNKNOWN_METHOD"),
                        nullMemberFilter
                )))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain(sensitiveField, sensitiveValue, "UNKNOWN_METHOD", "Jackson", "JsonToken");
    }

    @Test
    @DisplayName("REQ-SEARCH-009/010/011 - actual JSON strings retain semantic filter handling")
    void numericLookingStringsShouldRetainSemanticFilterHandling() {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> numericField = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("7", UUID.randomUUID().toString(), "EQUALS"))))
                .post("/accounts/search")
                .then()
                .extract();
        assertError(numericField, 400, "INVALID_SEARCH_FILTER");
        assertThat(numericField.<Map<String, Object>>path("details"))
                .containsExactly(Map.entry("filterIndex", 0));

        ExtractableResponse<Response> booleanMethod = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", UUID.randomUUID().toString(), "true"))))
                .post("/accounts/search")
                .then()
                .extract();
        assertError(booleanMethod, 400, "INVALID_SEARCH_FILTER");
        assertThat(booleanMethod.<Map<String, Object>>path("details"))
                .containsEntry("filterIndex", 0)
                .containsEntry("field", "id")
                .doesNotContainValue("true");
    }

    @Test
    @DisplayName("REQ-SEARCH-002/009 - omitted request body -> MALFORMED_JSON")
    void omittedRequestBodyShouldUseMalformedJson() {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "MALFORMED_JSON");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("structuralValidationCases")
    @DisplayName("REQ-SEARCH-002/009 - missing, null, blank, or oversized structure -> VALIDATION_ERROR")
    void invalidSharedStructureShouldUseValidationError(String scenario, Map<String, Object> body) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body)
                .post("/accounts/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(400);
        assertThat(response.<String>path("code")).as(scenario).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("REQ-SEARCH-002/003 - zero and twenty filters -> accepted; twenty-one -> VALIDATION_ERROR")
    void filterCollectionBoundariesShouldBeEnforced() {
        AuthSession caller = newSession("SUDO");
        UUID missingId = UUID.randomUUID();
        List<Map<String, Object>> twenty = repeatedFilters(20, missingId);

        assertThat(authenticatedJsonRequest(caller)
                .body(request(List.of()))
                .post("/accounts/search")
                .statusCode()).isEqualTo(200);
        assertThat(authenticatedJsonRequest(caller)
                .body(request(twenty))
                .post("/accounts/search")
                .statusCode()).isEqualTo(200);

        ExtractableResponse<Response> oversized = authenticatedJsonRequest(caller)
                .body(request(repeatedFilters(21, missingId)))
                .post("/accounts/search")
                .then()
                .extract();
        assertError(oversized, 400, "VALIDATION_ERROR");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValueShapeCases")
    @DisplayName("REQ-SEARCH-004/009/010 - invalid scalar or IN shape -> safe INVALID_SEARCH_FILTER")
    void invalidValueShapeShouldUseSafeSemanticError(String scenario, Object value, String comparisonMethod) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", value, comparisonMethod))))
                .post("/accounts/search")
                .then()
                .extract();

        assertSemanticError(response, 0, "id", comparisonMethod);
        assertThat(response.asString()).as(scenario).doesNotContain(String.valueOf(value));
    }

    @Test
    @DisplayName("REQ-SEARCH-004/005 - IN accepts duplicates, preserves scalar UUID parsing, and matches any element")
    void validInShouldApplyScalarParsingAndAnyOfSemantics() {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("Structured IN target");
        UUID missing = UUID.randomUUID();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter(
                        "id",
                        List.of(missing.toString(), target.toString(), target.toString()),
                        "IN"
                ))))
                .post("/accounts/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @Test
    @DisplayName("REQ-SEARCH-004/005 - IN accepts exactly one hundred elements")
    void inShouldAcceptExactlyOneHundredElements() {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("Structured IN upper-bound target");
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 99; index++) {
            values.add(UUID.randomUUID().toString());
        }
        values.add(target.toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", values, "IN"))))
                .post("/accounts/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonCanonicalUuidCases")
    @DisplayName("REQ-SEARCH-004/006/010 - abbreviated UUID text -> safe INVALID_SEARCH_FILTER")
    void abbreviatedUuidTextShouldBeRejectedWithoutDisclosure(
            String scenario,
            Object value,
            String comparisonMethod
    ) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", value, comparisonMethod))))
                .post("/accounts/search")
                .then()
                .extract();

        assertSemanticError(response, 0, "id", comparisonMethod);
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain("1-1-1-1-1", "UUID", "java.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonCanonicalTemporalWireFormatCases")
    @DisplayName("REQ-SEARCH-006/009/010 - expanded or signed year -> safe indexed INVALID_SEARCH_FILTER")
    void nonCanonicalTemporalWireFormatShouldBeRejectedWithoutDisclosure(
            String scenario,
            String route,
            String field,
            String comparisonMethod,
            String submittedValue
    ) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(
                        filter("id", UUID.randomUUID().toString(), "EQUALS"),
                        filter(field, submittedValue, comparisonMethod)
                )))
                .post(route)
                .then()
                .extract();

        assertSemanticError(response, 1, field, comparisonMethod);
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain(submittedValue, "DateTime", "LocalDate", "Instant", "java.");
    }

    @Test
    @DisplayName("REQ-SEARCH-003/006/012 - AND plus inclusive equal bounds -> target; contradictory valid filters -> empty page")
    void andCompositionShouldSupportInclusiveBoundsAndContradictoryEmptyResults() {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("Inclusive bound target");
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM accounts WHERE id = ?",
                Timestamp.class,
                target
        ).toInstant();

        List<Map<String, Object>> inclusive = List.of(
                filter("id", target.toString(), "EQUALS"),
                filter("createdAt", createdAt.toString(), "GREATER_THAN_OR_EQUAL"),
                filter("createdAt", createdAt.toString(), "LESS_THAN_OR_EQUAL")
        );
        ExtractableResponse<Response> inclusiveResponse = authenticatedJsonRequest(caller)
                .body(request(inclusive))
                .post("/accounts/search?size=100")
                .then()
                .extract();
        assertThat(inclusiveResponse.statusCode()).as(inclusiveResponse.asString()).isEqualTo(200);
        assertThat(inclusiveResponse.<List<String>>path("items.id")).containsExactly(target.toString());

        ExtractableResponse<Response> contradictory = authenticatedJsonRequest(caller)
                .body(request(List.of(
                        filter("id", target.toString(), "EQUALS"),
                        filter("id", UUID.randomUUID().toString(), "EQUALS")
                )))
                .post("/accounts/search")
                .then()
                .extract();
        assertThat(contradictory.statusCode()).as(contradictory.asString()).isEqualTo(200);
        assertThat(contradictory.<List<?>>path("items")).isEmpty();
    }

    @Test
    @DisplayName("REQ-SEARCH-007 - LIKE treats percent, underscore, and backslash as literal characters")
    void likeShouldTreatPatternCharactersLiterally() {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("Literal 50%_\\ marker");
        newAccount("Literal 50AA marker");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("displayName", "50%_\\", "LIKE"))))
                .post("/accounts/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    @DisplayName("REQ-SEARCH-007 - case-insensitive LIKE is independent of the JVM default locale")
    void likeShouldBeIndependentOfTheJvmDefaultLocale() {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("INDIGO Locale Target");
        Locale originalLocale = Locale.getDefault();
        ExtractableResponse<Response> response;

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            response = authenticatedJsonRequest(caller)
                    .body(request(List.of(filter("displayName", "INDIGO", "LIKE"))))
                    .post("/accounts/search?size=100")
                    .then()
                    .extract();
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(Locale.getDefault()).isEqualTo(originalLocale);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @Test
    @DisplayName("REQ-SEARCH-009/010/011 - semantic validation is deterministic, fail-fast, indexed, and non-disclosing")
    void semanticValidationShouldFailFastInDocumentedOrderWithoutDisclosure() {
        AuthSession caller = newSession("SUDO");
        String unknownField = "accountRoles.role.internalSecret";
        String unknownMethod = "SECRET_MATCH";
        String sensitiveValue = "do-not-echo@example.com";
        List<Map<String, Object>> filters = List.of(
                filter("id", UUID.randomUUID().toString(), "EQUALS"),
                filter(unknownField, sensitiveValue, unknownMethod),
                filter("id", sensitiveValue, "LIKE")
        );

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(filters))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "INVALID_SEARCH_FILTER");
        assertThat(response.<String>path("message")).isEqualTo("Unknown filter field.");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsExactly(Map.entry("filterIndex", 1));
        assertThat(response.asString())
                .doesNotContain(unknownField, unknownMethod, sensitiveValue, "accountRoles", "String");
    }

    @Test
    @DisplayName("REQ-SEARCH-009/011 - complete structural validation precedes semantic validation")
    void structuralValidationShouldPrecedeEarlierSemanticFailure() {
        AuthSession caller = newSession("SUDO");
        List<Map<String, Object>> filters = List.of(
                filter("unknownField", "sensitive-value", "UNKNOWN_METHOD"),
                filter("id", " ", "EQUALS")
        );

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(filters))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.asString())
                .doesNotContain("unknownField", "UNKNOWN_METHOD", "sensitive-value");
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("nonBreakingWhitespaceStructuralCases")
    @DisplayName("REQ-SEARCH-002/009/011 - non-breaking Unicode-only required strings are structurally blank")
    void nonBreakingUnicodeWhitespaceShouldBeStructurallyBlankBeforeSemantics(
            String scenario,
            String property,
            String whitespace
    ) {
        AuthSession caller = newSession("SUDO");
        Map<String, Object> structurallyBlankFilter = new LinkedHashMap<>(
                filter("id", UUID.randomUUID().toString(), "EQUALS")
        );
        structurallyBlankFilter.put(property, whitespace + whitespace);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(
                        filter("unknownField", "sensitive-value", "UNKNOWN_METHOD"),
                        structurallyBlankFilter
                )))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.asString())
                .as(scenario + " in " + property)
                .doesNotContain("unknownField", "UNKNOWN_METHOD", "sensitive-value");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBreakingWhitespaceCases")
    @DisplayName("REQ-SEARCH-005 - EQUALS trims non-breaking Unicode at scalar string boundaries")
    void equalsShouldTrimNonBreakingUnicodeAtStringBoundaries(String scenario, String whitespace) {
        AuthSession caller = newSession("SUDO");
        String displayName = "Uni " + UUID.randomUUID();
        UUID target = newAccount(displayName);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter(
                        "displayName",
                        whitespace + displayName + whitespace,
                        "EQUALS"
                ))))
                .post("/accounts/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBreakingWhitespaceCases")
    @DisplayName("REQ-SEARCH-004/005 - IN applies scalar Unicode boundary trimming to every item")
    void inShouldTrimNonBreakingUnicodeAtEveryScalarItemBoundary(String scenario, String whitespace) {
        AuthSession caller = newSession("SUDO");
        UUID target = newAccount("IN Unicode " + UUID.randomUUID());

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter(
                        "id",
                        List.of(
                                whitespace + UUID.randomUUID() + whitespace,
                                whitespace + target + whitespace
                        ),
                        "IN"
                ))))
                .post("/accounts/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactly(target.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBreakingWhitespaceCases")
    @DisplayName("REQ-SEARCH-004/009/010 - Unicode-blank IN item -> safe indexed semantic error")
    void unicodeBlankInItemShouldUseSafeIndexedSemanticError(String scenario, String whitespace) {
        AuthSession caller = newSession("SUDO");
        String sensitiveField = "semantic-trap.internalPath";
        String sensitiveValue = "do-not-disclose";

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(
                        filter("id", UUID.randomUUID().toString(), "EQUALS"),
                        filter("id", List.of(UUID.randomUUID().toString(), whitespace), "IN"),
                        filter(sensitiveField, sensitiveValue, "UNKNOWN_METHOD")
                )))
                .post("/accounts/search")
                .then()
                .extract();

        assertSemanticError(response, 1, "id", "IN");
        assertThat(response.asString())
                .as(scenario)
                .doesNotContain(sensitiveField, sensitiveValue, "UNKNOWN_METHOD");
    }

    @Test
    @DisplayName("REQ-SEARCH-009/010/011 - known field validation order -> unknown method before value parsing")
    void knownFieldShouldRejectUnknownMethodBeforeInspectingValue() {
        AuthSession caller = newSession("SUDO");
        String unknownMethod = "NOT_EQUALS";
        String sensitiveValue = "not-a-uuid-secret";

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", sensitiveValue, unknownMethod))))
                .post("/accounts/search")
                .then()
                .extract();

        assertError(response, 400, "INVALID_SEARCH_FILTER");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsEntry("filterIndex", 0)
                .containsEntry("field", "id")
                .doesNotContainValue(unknownMethod);
        assertThat(response.asString()).doesNotContain(unknownMethod, sensitiveValue, "UUID", "java.");
    }

    @Test
    @DisplayName("REQ-SEARCH-010/011 - recognized but unsupported method -> canonical field and method details")
    void unsupportedRecognizedMethodShouldExposeOnlyKnownIdentifiers() {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter("id", UUID.randomUUID().toString(), "LIKE"))))
                .post("/accounts/search")
                .then()
                .extract();

        assertSemanticError(response, 0, "id", "LIKE");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foreignResourceFieldCases")
    @DisplayName("REQ-SEARCH-003/008/010 - resource catalogs reject fields exposed only by another resource")
    void resourceCatalogShouldRejectForeignFieldWithoutDisclosure(
            String scenario,
            String route,
            String foreignField
    ) {
        AuthSession caller = newSession("SUDO");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(request(List.of(filter(
                        foreignField,
                        UUID.randomUUID().toString(),
                        "EQUALS"
                ))))
                .post(route)
                .then()
                .extract();

        assertError(response, 400, "INVALID_SEARCH_FILTER");
        assertThat(response.<String>path("message")).as(scenario).isEqualTo("Unknown filter field.");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsExactly(Map.entry("filterIndex", 0));
        assertThat(response.asString()).doesNotContain(foreignField, "internal", "java.");
    }

    private static Stream<Arguments> malformedRequestCases() {
        Map<String, Object> unknownRequestProperty = new LinkedHashMap<>();
        unknownRequestProperty.put("filters", List.of());
        unknownRequestProperty.put("predicate", "OR");

        Map<String, Object> filterWithUnknownProperty = new LinkedHashMap<>(
                filter("id", UUID.randomUUID().toString(), "EQUALS")
        );
        filterWithUnknownProperty.put("internalPath", "accounts.id");

        return Stream.of(
                Arguments.of("invalid JSON syntax", "{\"filters\":["),
                Arguments.of("request is an array", List.of()),
                Arguments.of("filters has the wrong JSON type", Map.of("filters", "all")),
                Arguments.of("unknown request property", unknownRequestProperty),
                Arguments.of("unknown filter property", request(List.of(filterWithUnknownProperty)))
        );
    }

    private static Stream<Arguments> wrongRequiredStringTypeCases() {
        return Stream.of(
                Arguments.of("object field", "field", Map.of("nested", "value")),
                Arguments.of("array field", "field", List.of("id")),
                Arguments.of("numeric field", "field", 8675309123456L),
                Arguments.of("boolean field", "field", true),
                Arguments.of("object comparisonMethod", "comparisonMethod", Map.of("nested", "value")),
                Arguments.of("array comparisonMethod", "comparisonMethod", List.of("EQUALS")),
                Arguments.of("numeric comparisonMethod", "comparisonMethod", 9753108642L),
                Arguments.of("boolean comparisonMethod", "comparisonMethod", false)
        );
    }

    private static Stream<Arguments> nullRequiredStringMemberCases() {
        return Stream.of(
                Arguments.of("null field", "field"),
                Arguments.of("null comparisonMethod", "comparisonMethod")
        );
    }

    private static Stream<Arguments> malformedFilterIndexBoundaryCases() {
        return Stream.of(
                Arguments.of(
                        "first filter with array comparisonMethod",
                        0,
                        "comparisonMethod",
                        List.of("submitted-secret")
                ),
                Arguments.of(
                        "twentieth filter with object field",
                        19,
                        "field",
                        Map.of("nested-secret", "submitted-secret")
                )
        );
    }

    private static Stream<Arguments> structuralValidationCases() {
        Map<String, Object> nullFilters = new HashMap<>();
        nullFilters.put("filters", null);

        Map<String, Object> nullValue = mutableFilter("id", null, "EQUALS");
        Map<String, Object> nullMethod = mutableFilter("id", UUID.randomUUID().toString(), null);

        return Stream.of(
                Arguments.of("filters omitted", Map.of()),
                Arguments.of("filters null", nullFilters),
                Arguments.of("null filter element", request(listContainingNull())),
                Arguments.of("field omitted", request(List.of(Map.of(
                        "value", UUID.randomUUID().toString(),
                        "comparisonMethod", "EQUALS"
                )))),
                Arguments.of("field blank", request(List.of(filter(" ", UUID.randomUUID().toString(), "EQUALS")))),
                Arguments.of("value omitted", request(List.of(Map.of(
                        "field", "id",
                        "comparisonMethod", "EQUALS"
                )))),
                Arguments.of("value null", request(List.of(nullValue))),
                Arguments.of("textual value blank", request(List.of(filter("id", " ", "EQUALS")))),
                Arguments.of("comparisonMethod omitted", request(List.of(Map.of(
                        "field", "id",
                        "value", UUID.randomUUID().toString()
                )))),
                Arguments.of("comparisonMethod null", request(List.of(nullMethod))),
                Arguments.of("comparisonMethod blank", request(List.of(filter(
                        "id",
                        UUID.randomUUID().toString(),
                        " "
                )))),
                Arguments.of("more than twenty filters", request(repeatedFilters(21, UUID.randomUUID())))
        );
    }

    private static Stream<Arguments> invalidValueShapeCases() {
        List<String> overLimit = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            overLimit.add(UUID.randomUUID().toString());
        }
        List<Object> nullElement = new ArrayList<>();
        nullElement.add(UUID.randomUUID().toString());
        nullElement.add(null);

        return Stream.of(
                Arguments.of("object supplied to scalar", Map.of("id", UUID.randomUUID()), "EQUALS"),
                Arguments.of("array supplied to scalar", List.of(UUID.randomUUID().toString()), "EQUALS"),
                Arguments.of("number supplied to scalar", 8675309123456L, "EQUALS"),
                Arguments.of("boolean supplied to scalar", true, "EQUALS"),
                Arguments.of("scalar supplied to IN", UUID.randomUUID().toString(), "IN"),
                Arguments.of("empty IN", List.of(), "IN"),
                Arguments.of("null IN element", nullElement, "IN"),
                Arguments.of("heterogeneous IN", List.of(UUID.randomUUID().toString(), 7), "IN"),
                Arguments.of("over one hundred IN elements", overLimit, "IN")
        );
    }

    private static Stream<Arguments> nonCanonicalUuidCases() {
        return Stream.of(
                Arguments.of("abbreviated UUID supplied to EQUALS", "1-1-1-1-1", "EQUALS"),
                Arguments.of(
                        "abbreviated UUID supplied inside IN",
                        List.of(UUID.randomUUID().toString(), "1-1-1-1-1"),
                        "IN"
                )
        );
    }

    private static Stream<Arguments> nonCanonicalTemporalWireFormatCases() {
        return Stream.of(
                Arguments.of(
                        "Member birthDate rejects expanded signed year",
                        "/members/search",
                        "birthDate",
                        "EQUALS",
                        "+12345-01-01"
                ),
                Arguments.of(
                        "Account createdAt rejects Instant.parse expanded signed year",
                        "/accounts/search",
                        "createdAt",
                        "GREATER_THAN_OR_EQUAL",
                        "+12345-01-01T00:00:00Z"
                )
        );
    }

    private static Stream<Arguments> foreignResourceFieldCases() {
        return Stream.of(
                Arguments.of("Account rejects Event title", "/accounts/search", "title"),
                Arguments.of("Event rejects Account displayName", "/events/search", "displayName"),
                Arguments.of("Member rejects Event beginDate", "/members/search", "beginDate"),
                Arguments.of(
                        "Membership Solicitation rejects Event requiredPermissionId",
                        "/membership-solicitations/search",
                        "requiredPermissionId"
                ),
                Arguments.of("Oratoriano rejects Account role", "/oratorianos/search", "role")
        );
    }

    private static Stream<Arguments> nonBreakingWhitespaceCases() {
        return Stream.of(
                Arguments.of("NO-BREAK SPACE U+00A0", "\u00A0"),
                Arguments.of("FIGURE SPACE U+2007", "\u2007"),
                Arguments.of("NARROW NO-BREAK SPACE U+202F", "\u202F")
        );
    }

    private static Stream<Arguments> nonBreakingWhitespaceStructuralCases() {
        return nonBreakingWhitespaceCases().flatMap(arguments -> {
            Object[] values = arguments.get();
            return Stream.of(
                    Arguments.of(values[0], "field", values[1]),
                    Arguments.of(values[0], "comparisonMethod", values[1]),
                    Arguments.of(values[0], "value", values[1])
            );
        });
    }

    private static Map<String, Object> request(List<?> filters) {
        return Map.of("filters", filters);
    }

    private static Map<String, Object> mutableFilter(String field, Object value, String comparisonMethod) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("value", value);
        result.put("comparisonMethod", comparisonMethod);
        return result;
    }

    private static List<Object> listContainingNull() {
        List<Object> filters = new ArrayList<>();
        filters.add(null);
        return filters;
    }

    private static List<Map<String, Object>> repeatedFilters(int count, UUID id) {
        List<Map<String, Object>> filters = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            filters.add(filter("id", id.toString(), "EQUALS"));
        }
        return filters;
    }

    private static void assertError(
            ExtractableResponse<Response> response,
            int expectedStatus,
            String expectedCode
    ) {
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(expectedStatus);
        assertThat(response.<String>path("code")).as(response.asString()).isEqualTo(expectedCode);
    }

    private static void assertSemanticError(
            ExtractableResponse<Response> response,
            int filterIndex,
            String field,
            String comparisonMethod
    ) {
        assertError(response, 400, "INVALID_SEARCH_FILTER");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsEntry("filterIndex", filterIndex)
                .containsEntry("field", field)
                .containsEntry("comparisonMethod", comparisonMethod);
    }
}
