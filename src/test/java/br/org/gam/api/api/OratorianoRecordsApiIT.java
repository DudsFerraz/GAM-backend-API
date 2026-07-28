package br.org.gam.api.api;

import br.org.gam.api.oratoriano.application.OratorianoDomainLoader;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Oratoriano records")
class OratorianoRecordsApiIT extends OratorioModuleApiTestSupport {

    private static final String ORATORIANOS = "/oratorianos";
    private static final String VALID_REASON = "  Correcting an identity record  ";

    @Autowired
    private OratorianoDomainLoader oratorianoDomainLoader;

    @Test
    @DisplayName("REQ-ORATORIANO-001 and REQ-ORATORIANO-003 - name-only registration -> UUID v7 ordinary profile")
    void nameOnlyRegistrationShouldCreateMinimalOrdinaryProfile() {
        AuthSession caller = sudoSession();
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload("Erik", "Garcia"))
                .post(ORATORIANOS)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(response.path("id"));
        trackOratoriano(id);
        assertPublicApiLocation(response, ORATORIANOS + "/" + id);
        assertUuidV7(id);
        assertThat(response.<String>path("firstName")).isEqualTo("Erik");
        assertThat(response.<String>path("surname")).isEqualTo("Garcia");
        assertThat(response.<Object>path("birthDate")).isNull();
        assertThat(response.<Object>path("phoneNumber")).isNull();
        assertThat(response.jsonPath().getMap("$"))
                .doesNotContainKeys(
                        "cpf", "rg", "address", "family", "health", "consent",
                        "attachments", "status", "active"
                );
        assertThat(activityCountForTarget(id)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("humanEquivalentNameCases")
    @DisplayName("REQ-ORATORIANO-002 - human-equivalent full names -> HTTP 409 across flattened boundaries")
    void humanEquivalentNamesShouldConflict(
            String scenario,
            String existingFirstName,
            String existingSurname,
            String submittedFirstName,
            String submittedSurname
    ) {
        AuthSession caller = sudoSession();
        UUID existingId = createOratoriano(caller, existingFirstName, existingSurname);
        long activitiesBeforeConflict = activityCountForTarget(existingId);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload(submittedFirstName, submittedSurname))
                .post(ORATORIANOS)
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(409);
        assertThat(oratorianoCount()).isEqualTo(1);
        assertThat(activityCountForTarget(existingId)).isEqualTo(activitiesBeforeConflict);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("meaningfullyDifferentNameCases")
    @DisplayName("REQ-ORATORIANO-002 - punctuation or letter differences -> distinct registrations")
    void meaningfullyDifferentNamesShouldRemainDistinct(
            String scenario,
            String firstFirstName,
            String firstSurname,
            String secondFirstName,
            String secondSurname
    ) {
        AuthSession caller = sudoSession();
        UUID firstId = createOratoriano(caller, firstFirstName, firstSurname);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload(secondFirstName, secondSurname))
                .post(ORATORIANOS)
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(201);
        UUID secondId = UUID.fromString(response.path("id"));
        trackOratoriano(secondId);
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(oratorianoCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-005 - changed name without reason -> HTTP 400 and unchanged profile")
    void changedNameWithoutReasonShouldBeRejected() {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "Marina", "Souza");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload("Marina", "Sousa", null, null, null))
                .put(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(activityCountForTarget(id)).isZero();
        authenticatedJsonRequest(caller)
                .get(ORATORIANOS + "/{id}", id)
                .then()
                .statusCode(200)
                .body("surname", org.hamcrest.Matchers.equalTo("Souza"));
    }

    @Test
    @DisplayName("REQ-ORATORIANO-005 and REQ-ORATORIANO-011 - normalized correction -> one value-free activity")
    void normalizedCorrectionShouldChangeProfileAndEmitOneValueFreeActivity() {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "Marina", "Souza");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload(
                        "Marina", "Sousa", "2005-04-03", "+5519998877665", VALID_REASON
                ))
                .put(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("surname")).isEqualTo("Sousa");
        assertThat(response.<String>path("birthDate")).isEqualTo("2005-04-03");
        assertThat(response.<String>path("phoneNumber")).isEqualTo("+5519998877665");
        assertThat(activityCountForTarget(id)).isEqualTo(1);
        Map<String, Object> activity = jdbcTemplate.queryForMap(
                "SELECT reason, metadata FROM activity_logs WHERE target_id = ?",
                id
        );
        assertThat(activity.get("reason")).isEqualTo("Correcting an identity record");
        assertThat(activity.get("metadata").toString())
                .contains("name", "birthDate", "phoneNumber")
                .doesNotContain("Marina", "Sousa", "2005-04-03", "+5519998877665");
    }

    @Test
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-GAM-PHONE-004 - invalid Oratoriano phone -> public phoneNumber FORMAT violation")
    void invalidPhoneShouldUseStructuredValidationViolation() {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "Marina", "Souza");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload("Marina", "Souza", null, "abc", null))
                .put(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertSingleValidationViolation(response, "body", "/phoneNumber", "FORMAT", "abc");
        assertThat(activityCountForTarget(id)).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-005 and REQ-ORATORIANO-011 - normalized no-op -> no mutation activity")
    void normalizedNoOpShouldNotEmitActivity() {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "Marina", "Souza");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload("Marina", "Souza", null, null, null))
                .put(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(activityCountForTarget(id)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReasonCases")
    @DisplayName("REQ-API-ERROR-002/003/004 and REQ-ORATORIANO-009/010 - invalid bounded deletion reason -> structured validation")
    void invalidBoundedReasonsShouldBeRejected(
            String scenario,
            Map<String, Object> body,
            String expectedViolationCode,
            String rejectedValue
    ) {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "Paulo", "Mendes");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body)
                .delete(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertSingleValidationViolation(
                response,
                "body",
                "/reason",
                expectedViolationCode,
                rejectedValue
        );
        assertThat(activityCountForTarget(id)).isZero();
        authenticatedJsonRequest(caller).get(ORATORIANOS + "/{id}", id).then().statusCode(200);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-009 and REQ-ORATORIANO-010 - deletion reserves name and restoration reuses identity")
    void deletionShouldReserveNameAndRestorationShouldReuseIdentity() {
        AuthSession caller = sudoSession();
        UUID id = createOratoriano(caller, "João", "Silva");
        clearActivities();

        ExtractableResponse<Response> deletion = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete(ORATORIANOS + "/{id}", id)
                .then()
                .extract();

        assertThat(deletion.statusCode()).isEqualTo(204);
        assertThat(activityCountForTarget(id)).isEqualTo(1);
        authenticatedJsonRequest(caller).get(ORATORIANOS + "/{id}", id).then().statusCode(404);

        ExtractableResponse<Response> conflictingRegistration = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload("JOAO", "SILVA"))
                .post(ORATORIANOS)
                .then()
                .extract();
        assertThat(conflictingRegistration.statusCode()).isEqualTo(409);

        ExtractableResponse<Response> restoration = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Restoring the original identity  "))
                .patch(ORATORIANOS + "/{id}/restore", id)
                .then()
                .extract();

        assertThat(restoration.statusCode()).isEqualTo(204);
        assertThat(activityCountForTarget(id)).isEqualTo(2);
        authenticatedJsonRequest(caller)
                .get(ORATORIANOS + "/{id}", id)
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(id.toString()));
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 - ordinary search is human-equivalent, excludes deleted records, and exposes no ranking")
    void ordinarySearchShouldUseHumanEquivalentNameAndExposeNoRanking() {
        AuthSession caller = sudoSession();
        UUID visibleId = createOratoriano(caller, "João", "Silva");
        UUID deletedId = createOratoriano(caller, "Ana", "Souza");
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete(ORATORIANOS + "/{id}", deletedId)
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("name", "JOAO SILVA", "EQUALS")))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = response.path("items");
        assertThat(resourceIds(items)).containsExactly(visibleId);
        assertThat(items).allSatisfy(item ->
                assertThat(item).doesNotContainKeys(
                        "rank", "score", "mostFrequent", "threshold", "ranking",
                        "cpf", "rg", "address", "health", "forms"
                )
        );
        assertThat(activityCountForTarget(visibleId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("humanEquivalentSearchCases")
    @DisplayName("REQ-ORATORIANO-002 - comparison input normalizes whitespace, field boundaries, accents, and typographic separators")
    void ordinarySearchShouldNormalizeTheCompleteHumanEquivalentComparisonInput(
            String scenario,
            String firstName,
            String surname,
            String submittedFullName
    ) {
        AuthSession caller = sudoSession();
        UUID expectedId = createOratoriano(caller, firstName, surname);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("name", submittedFullName, "EQUALS")))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.path("items"))).as(scenario).containsExactly(expectedId);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"EQUALS", "LIKE"})
    @DisplayName("REQ-ORATORIANO-002/013 - name EQUALS and LIKE trim and collapse Unicode whitespace")
    void ordinarySearchShouldNormalizeUnicodeWhitespaceForEveryNameComparison(String comparisonMethod) {
        AuthSession caller = sudoSession();
        UUID expectedId = createOratoriano(caller, "Ana", "Silva");
        List<String> submittedNames = List.of(
                "\u2003ANA\u2003\u2003SILVA\u2003",
                "\u2002ANA\u2003\u205FSILVA\u2002"
        );

        for (String submittedName : submittedNames) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(searchPayload(filter("name", submittedName, comparisonMethod)))
                    .post(ORATORIANOS + "/search")
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as(comparisonMethod + ": " + response.asString())
                    .isEqualTo(200);
            assertThat(resourceIds(response.path("items")))
                    .as(comparisonMethod)
                    .containsExactly(expectedId);
        }
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("nonBreakingWhitespaceSearchCases")
    @DisplayName("REQ-ORATORIANO-002/013 - name EQUALS and LIKE collapse non-breaking Unicode whitespace")
    void ordinarySearchShouldNormalizeNonBreakingUnicodeWhitespaceForEveryNameComparison(
            String scenario,
            String comparisonMethod,
            String whitespace
    ) {
        AuthSession caller = sudoSession();
        UUID expectedId = createOratoriano(caller, "Ana", "Silva");
        String submittedName = whitespace + "ANA" + whitespace + whitespace + "SILVA" + whitespace;

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("name", submittedName, comparisonMethod)))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode())
                .as(scenario + " with " + comparisonMethod + ": " + response.asString())
                .isEqualTo(200);
        assertThat(resourceIds(response.path("items"))).containsExactly(expectedId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("meaningfullyDifferentSearchCases")
    @DisplayName("REQ-ORATORIANO-002 - comparison normalization preserves meaningful punctuation")
    void ordinarySearchShouldNotEraseMeaningfulPunctuation(
            String scenario,
            String firstName,
            String surname,
            String submittedFullName
    ) {
        AuthSession caller = sudoSession();
        createOratoriano(caller, firstName, surname);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("name", submittedFullName, "EQUALS")))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.path("items"))).as(scenario).isEmpty();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-013 - id EQUALS/IN and name LIKE -> exact documented search catalog")
    void ordinarySearchShouldSupportEveryDocumentedFieldAndMethod() {
        AuthSession caller = sudoSession();
        UUID expectedId = createOratoriano(caller, "Alice", "Moraes");
        createOratoriano(caller, "Bruno", "Lima");

        List<Map<String, Object>> filters = List.of(
                filter("id", expectedId.toString(), "EQUALS"),
                filter("id", List.of(UUID.randomUUID().toString(), expectedId.toString()), "IN"),
                filter("name", "  ALICE   MORAES  ", "LIKE")
        );

        for (Map<String, Object> searchFilter : filters) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(searchPayload(searchFilter))
                    .post(ORATORIANOS + "/search")
                    .then()
                    .extract();

            assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
            assertThat(resourceIds(response.path("items"))).containsExactly(expectedId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSearchFilterCases")
    @DisplayName("REQ-ORATORIANO-008 - unsupported comparison or unknown filter -> safe HTTP 400")
    void invalidOrdinarySearchFilterShouldFailFast(
            String scenario,
            Map<String, Object> invalidFilter
    ) {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Alice", "Moraes");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(invalidFilter))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(400);
        assertThat(response.<String>path("message")).isNotBlank();
    }

    @Test
    @DisplayName("Search guideline - unknown public field -> exact generic message without submitted field leakage")
    void unknownOrdinarySearchFieldShouldReturnTheGenericSafeMessage() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Alice", "Moraes");
        String submittedField = "health.cpf";

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter(submittedField, "52998224725", "EQUALS")))
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        assertThat(response.<String>path("message")).isEqualTo("Unknown filter field.");
        assertThat(response.asString())
                .doesNotContain(submittedField)
                .doesNotContain("oratoriano_additional_forms", "draft_data", "cpf");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-004 - authentication and ordinary-profile permissions remain distinct")
    void ordinaryProfileRoutesShouldDistinguishAuthenticationAndAuthorization() {
        AuthSession setup = sudoSession();
        UUID id = createOratoriano(setup, "Erik", "Garcia");
        AuthSession member = newSession("MEMBER");

        assertThat(jsonRequest().get(ORATORIANOS + "/{id}", id).statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(member).get(ORATORIANOS + "/{id}", id).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .body(oratorianoRegistrationPayload("Carlos", "Lima"))
                .post(ORATORIANOS).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .body(searchPayload())
                .post(ORATORIANOS + "/search").statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .body(reasonPayload(VALID_REASON))
                .delete(ORATORIANOS + "/{id}", id).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-007 and REQ-ORATORIANO-012 - requested attendance summary derives all-time, year, and month counts")
    void requestedAttendanceSummaryShouldDeriveAllRequiredCounts() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Renata", "Nunes");
        UUID december2025 = createOratorio(caller, LocalDate.of(2025, 12, 13));
        UUID january2026 = createOratorio(caller, LocalDate.of(2026, 1, 10));
        UUID february2026 = createOratorio(caller, LocalDate.of(2026, 2, 14));
        markPresent(caller, december2025, oratorianoId);
        markPresent(caller, january2026, oratorianoId);
        markPresent(caller, february2026, oratorianoId);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("year", 2026)
                .queryParam("month", 1)
                .get(ORATORIANOS + "/{id}/attendance-summary", oratorianoId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<Number>path("oratorioAttendances").longValue()).isEqualTo(3);
        assertThat(response.<Number>path("oratorioDistinctMonthsAttendances").longValue()).isEqualTo(3);
        assertThat(response.<Number>path("oratorioDistinctYearsAttendances").longValue()).isEqualTo(2);
        assertThat(response.<Number>path("oratorioYearAttendances").longValue()).isEqualTo(2);
        assertThat(response.<Number>path("oratorioYearDistinctMonthsAttendances").longValue()).isEqualTo(2);
        assertThat(response.<Number>path("oratorioMonthAttendances").longValue()).isEqualTo(1);
        assertThat(response.jsonPath().getMap("$"))
                .doesNotContainKeys("rank", "score", "rate", "streak", "average");

        var oratoriano = oratorianoDomainLoader.requiredById(oratorianoId);
        assertThat(oratoriano.oratorioAttendances()).isEqualTo(3);
        assertThat(oratoriano.oratorioDistinctMonthsAttendances()).isEqualTo(3);
        assertThat(oratoriano.oratorioDistinctYearsAttendances()).isEqualTo(2);
        assertThat(oratoriano.oratorioYearAttendances(2026)).isEqualTo(2);
        assertThat(oratoriano.oratorioYearDistinctMonthsAttendances(2026)).isEqualTo(2);
        assertThat(oratoriano.oratorioMonthAttendances(2026, 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-012 and REQ-OPENAPI-004 - unrequested attendance summary dimensions are omitted")
    void attendanceSummaryShouldOmitUnrequestedYearAndMonthFields() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Renata", "Nunes");

        ExtractableResponse<Response> allTime = authenticatedJsonRequest(caller)
                .get(ORATORIANOS + "/{id}/attendance-summary", oratorianoId)
                .then()
                .extract();
        ExtractableResponse<Response> selectedYear = authenticatedJsonRequest(caller)
                .queryParam("year", 2026)
                .get(ORATORIANOS + "/{id}/attendance-summary", oratorianoId)
                .then()
                .extract();

        assertThat(allTime.statusCode()).as(allTime.asString()).isEqualTo(200);
        assertThat(allTime.jsonPath().getMap("$")).containsOnlyKeys(
                "oratorioAttendances",
                "oratorioDistinctMonthsAttendances",
                "oratorioDistinctYearsAttendances"
        );
        assertThat(selectedYear.statusCode()).as(selectedYear.asString()).isEqualTo(200);
        assertThat(selectedYear.jsonPath().getMap("$")).containsOnlyKeys(
                "oratorioAttendances",
                "oratorioDistinctMonthsAttendances",
                "oratorioDistinctYearsAttendances",
                "oratorioYearAttendances",
                "oratorioYearDistinctMonthsAttendances"
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAttendanceSummaryDimensionCases")
    @DisplayName("REQ-ORATORIANO-012 - invalid requested attendance dimensions fail without a partial summary")
    void invalidAttendanceSummaryDimensionsShouldBeRejected(
            String scenario,
            Integer year,
            Integer month,
            String expectedField,
            String expectedViolationCode
    ) {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Invalid", "Summary");
        var request = authenticatedJsonRequest(caller);
        if (year != null) {
            request.queryParam("year", year);
        }
        if (month != null) {
            request.queryParam("month", month);
        }

        ExtractableResponse<Response> response = request
                .get(ORATORIANOS + "/{id}/attendance-summary", oratorianoId)
                .then()
                .extract();

        assertSingleValidationViolation(
                response,
                "query",
                expectedField,
                expectedViolationCode,
                null
        );
    }

    @Test
    @DisplayName("REQ-ORATORIANO-012 - attendance history returns active facts newest occurrence first without sensitive data")
    void attendanceHistoryShouldReturnOnlyActiveFactsNewestFirst() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Bianca", "Rocha");
        UUID march = createOratorio(caller, LocalDate.of(2026, 3, 7));
        UUID april = createOratorio(caller, LocalDate.of(2026, 4, 11));
        markPresent(caller, march, oratorianoId);
        markPresent(caller, april, oratorianoId);
        authenticatedJsonRequest(caller)
                .body(reasonPayload("Correcting historical attendance"))
                .delete(
                        "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}",
                        march,
                        oratorianoId
                )
                .then()
                .statusCode(204);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get(ORATORIANOS + "/{id}/attendances", oratorianoId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = response.path("items");
        assertThat(items).hasSize(1);
        assertThat(response.asString())
                .contains(april.toString(), "2026-04-11", "COMPLETED")
                .doesNotContain(march.toString(), "forms", "health", "otherAttendees");

        var oratoriano = oratorianoDomainLoader.requiredById(oratorianoId);
        assertThat(oratoriano.oratorioAttendances()).isEqualTo(1);
        assertThat(oratoriano.oratorioYearAttendances(2026)).isEqualTo(1);
        assertThat(oratoriano.oratorioMonthAttendances(2026, 3)).isZero();
        assertThat(oratoriano.oratorioMonthAttendances(2026, 4)).isEqualTo(1);
        assertThat(oratoriano.oratorioDistinctMonthsAttendances()).isEqualTo(1);
        assertThat(oratoriano.oratorioDistinctYearsAttendances()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 - yearly-attendance sort uses derived count with normalized-name and UUID tie-breakers")
    void yearlyAttendanceSortShouldUseDerivedCountAndDeterministicTies() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID noAttendance = createOratoriano(caller, "Carlos", "Rocha");
        UUID alphabeticalTie = createOratoriano(caller, "Álvaro", "Mendes");
        UUID oneAttendance = createOratoriano(caller, "Bruno", "Lima");
        UUID twoAttendances = createOratoriano(caller, "Ana", "Silva");
        UUID first = createOratorio(caller, LocalDate.of(2026, 5, 9));
        UUID second = createOratorio(caller, LocalDate.of(2026, 6, 13));
        markPresent(caller, first, oneAttendance);
        markPresent(caller, first, twoAttendances);
        markPresent(caller, second, twoAttendances);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("sort", "oratorioYearAttendances,desc")
                .queryParam("attendanceYear", 2026)
                .queryParam("size", 100)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.path("items")))
                .containsSubsequence(twoAttendances, oneAttendance, alphabeticalTie, noAttendance);
        assertThat(response.asString())
                .doesNotContain("rank", "score", "mostFrequent", "threshold", "ranking");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008, REQ-ORATORIANO-012 and REQ-OPENAPI-007 - allowed yearly-attendance ascending sort follows its requested direction")
    void yearlyAttendanceSortShouldHonorTheDocumentedAscendingDirection() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID noAttendance = createOratoriano(caller, "Ana", "Silva");
        UUID oneAttendance = createOratoriano(caller, "Bruno", "Lima");
        UUID occurrence = createOratorio(caller, LocalDate.of(2026, 5, 9));
        markPresent(caller, occurrence, oneAttendance);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("sort", "oratorioYearAttendances,asc")
                .queryParam("attendanceYear", 2026)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.path("items")))
                .containsExactly(noAttendance, oneAttendance);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - repeatable sort validates every value against the endpoint allowlist")
    void repeatableSortShouldValidateEveryValueAgainstTheOratorianoAllowlist() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> repeatedAllowed = authenticatedJsonRequest(caller)
                .queryParam(
                        "sort",
                        List.of(
                                "oratorioYearAttendances,desc",
                                "oratorioYearAttendances,desc"
                        )
                )
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();
        ExtractableResponse<Response> oneDisallowed = authenticatedJsonRequest(caller)
                .queryParam(
                        "sort",
                        List.of(
                                "oratorioYearAttendances,desc",
                                "name,asc"
                        )
                )
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(repeatedAllowed.statusCode())
                .as(repeatedAllowed.asString())
                .isEqualTo(200);
        assertThat(oneDisallowed.statusCode())
                .as(oneDisallowed.asString())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - repeated sort values cannot repair split field and direction tokens")
    void repeatedSortValuesShouldEachRequireFieldAndDirectionSyntax() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload())
                .post(ORATORIANOS + "/search?sort=oratorioYearAttendances&sort=asc")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - supplied blank sort value is rejected rather than treated as omitted")
    void blankSortValueShouldBeRejected() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload())
                .post(ORATORIANOS + "/search?sort=")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOratorianoSortCases")
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - malformed or unknown sort directions are rejected")
    void invalidOratorianoSortShouldBeRejected(String scenario, String sort) {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("sort", sort)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - unsorted search uses normalized name and UUID ordering")
    void unsortedSearchShouldUseDeterministicNameOrdering() {
        AuthSession caller = sudoSession();
        UUID laterName = createOratoriano(caller, "Zelia", "Rocha");
        UUID earlierName = createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.path("items")))
                .containsExactly(earlierName, laterName);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-008 and REQ-OPENAPI-007 - explicit undocumented name sort is rejected")
    void explicitNameSortShouldBeRejected() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Ana", "Silva");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("sort", "name,asc")
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - Oratoriano search accepts size 100 and rejects size above 100")
    void searchShouldRejectOversizedPages() {
        AuthSession caller = sudoSession();
        createOratoriano(caller, "Marina", "Sousa");

        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .statusCode()).isEqualTo(200);
        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 101)
                .body(searchPayload())
                .post(ORATORIANOS + "/search")
                .statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - attendance history accepts size 100 and rejects size above 100")
    void attendanceHistoryShouldRejectOversizedPages() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Bianca", "Rocha");

        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .get(ORATORIANOS + "/{id}/attendances", oratorianoId)
                .statusCode()).isEqualTo(200);
        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 101)
                .get(ORATORIANOS + "/{id}/attendances", oratorianoId)
                .statusCode()).isEqualTo(400);
    }

    private long oratorianoCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oratorianos", Long.class);
    }

    private void assertSingleValidationViolation(
            ExtractableResponse<Response> response,
            String location,
            String field,
            String code,
            String rejectedValue
    ) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/json");
        assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        assertThat(response.<String>path("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(response.<String>path("message"))
                .isNotBlank()
                .doesNotContain(
                        "InvalidCommandException",
                        "InvalidPhoneNumberException",
                        "IllegalArgumentException"
                );
        assertThat(response.jsonPath().getMap("details")).containsOnlyKeys("violations");
        List<Map<String, Object>> violations = response.jsonPath().getList("details.violations");
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation)
                    .containsOnlyKeys("location", "field", "code", "message")
                    .containsEntry("location", location)
                    .containsEntry("field", field)
                    .containsEntry("code", code);
            assertThat(String.valueOf(violation.get("message"))).isNotBlank();
        });
        if (rejectedValue != null && !rejectedValue.isBlank()) {
            assertThat(response.asString()).doesNotContain(rejectedValue);
        }
    }

    private void markPresent(AuthSession caller, UUID oratorioId, UUID oratorianoId) {
        authenticatedJsonRequest(caller)
                .put(
                        "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}",
                        oratorioId,
                        oratorianoId
                )
                .then()
                .statusCode(201);
    }

    private static Stream<Arguments> humanEquivalentNameCases() {
        return Stream.of(
                Arguments.of("case and diacritic variants", "João", "Silva", "JOAO", "SILVA"),
                Arguments.of("flattened first-name/surname boundary", "Ana Maria", "Souza", "Ana", "Maria Souza")
        );
    }

    private static Stream<Arguments> nonBreakingWhitespaceSearchCases() {
        return Stream.of(
                Arguments.of("NO-BREAK SPACE U+00A0", "\u00A0"),
                Arguments.of("FIGURE SPACE U+2007", "\u2007"),
                Arguments.of("NARROW NO-BREAK SPACE U+202F", "\u202F")
        ).flatMap(arguments -> {
            Object[] values = arguments.get();
            return Stream.of(
                    Arguments.of(values[0], "EQUALS", values[1]),
                    Arguments.of(values[0], "LIKE", values[1])
            );
        });
    }

    private static Stream<Arguments> humanEquivalentSearchCases() {
        return Stream.of(
                Arguments.of(
                        "flattened boundary with accepted whitespace variants",
                        "Ana Maria",
                        "Souza",
                        "  ANA \t MARIA   SOUZA  "
                ),
                Arguments.of(
                        "typographic apostrophe and accent variant",
                        "Lia",
                        "D'Ávila",
                        "lia d’avila"
                ),
                Arguments.of(
                        "typographic dash variant",
                        "Ana-Luiza",
                        "Silva",
                        "ANA–LUIZA SILVA"
                )
        );
    }

    private static Stream<Arguments> meaningfullyDifferentSearchCases() {
        return Stream.of(
                Arguments.of(
                        "hyphen cannot be erased into whitespace",
                        "Ana-Luiza",
                        "Silva",
                        "Ana Luiza Silva"
                ),
                Arguments.of(
                        "apostrophe cannot be erased",
                        "Lia",
                        "D'Ávila",
                        "Lia Davila"
                )
        );
    }

    private static Stream<Arguments> invalidAttendanceSummaryDimensionCases() {
        return Stream.of(
                Arguments.of("month without year", null, 1, "$", "RELATION"),
                Arguments.of("month below January", 2026, 0, "month", "RANGE"),
                Arguments.of("month above December", 2026, 13, "month", "RANGE")
        );
    }

    private static Stream<Arguments> invalidOratorianoSortCases() {
        return Stream.of(
                Arguments.of(
                        "unknown direction",
                        "oratorioYearAttendances,sideways"
                ),
                Arguments.of(
                        "missing direction",
                        "oratorioYearAttendances"
                ),
                Arguments.of(
                        "missing field",
                        ",asc"
                )
        );
    }

    private static Stream<Arguments> meaningfullyDifferentNameCases() {
        return Stream.of(
                Arguments.of("hyphen remains meaningful", "Ana-Luiza", "Silva", "Ana Luiza", "Silva"),
                Arguments.of("apostrophe remains meaningful", "Lia", "D'Ávila", "Lia", "Davila"),
                Arguments.of("letter difference remains meaningful", "Ana", "Luiza", "Ana", "Luísa"),
                Arguments.of("surname letter difference remains meaningful", "Paulo", "Souza", "Paulo", "Sousa")
        );
    }

    private static Stream<Arguments> invalidReasonCases() {
        Map<String, Object> nullReason = new HashMap<>();
        nullReason.put("reason", null);
        String oversizedReason = "reason-secret-".repeat(155);
        return Stream.of(
                Arguments.of("missing reason", Map.of(), "REQUIRED", null),
                Arguments.of("null reason", nullReason, "REQUIRED", null),
                Arguments.of("blank reason", Map.of("reason", "   "), "NOT_BLANK", null),
                Arguments.of(
                        "reason above 2,000 characters",
                        Map.of("reason", oversizedReason),
                        "SIZE",
                        oversizedReason
                )
        );
    }

    private static Stream<Arguments> invalidSearchFilterCases() {
        return Stream.of(
                Arguments.of(
                        "UUID does not support LIKE",
                        filter("id", UUID.randomUUID().toString(), "LIKE")
                ),
                Arguments.of(
                        "name does not support ordering comparison",
                        filter("name", "Alice Moraes", "GREATER_THAN")
                ),
                Arguments.of(
                        "unknown fields are rejected",
                        filter("health.cpf", "52998224725", "EQUALS")
                )
        );
    }
}
