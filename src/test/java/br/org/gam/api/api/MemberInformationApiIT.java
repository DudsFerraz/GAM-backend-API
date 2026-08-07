package br.org.gam.api.api;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Member information and protected annual responses")
class MemberInformationApiIT extends MemberApiTestSupport {

    private final Set<UUID> annualResponseIds = new LinkedHashSet<>();

    @AfterEach
    void cleanupAnnualResponses() {
        for (UUID responseId : annualResponseIds) {
            jdbcTemplate.update("DELETE FROM annual_member_occupations WHERE response_id = ?", responseId);
            jdbcTemplate.update("DELETE FROM annual_member_information_responses WHERE id = ?", responseId);
        }
        annualResponseIds.clear();
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-008 to 011 - shared ETag, state change, and stale no-op protection across information surfaces")
    void memberInformationSurfacesShouldShareAndAdvanceOneAggregateEtag() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Synthetic information target");
        UUID memberId = registerMember(coordinator, accountId);
        clearActivities();

        ExtractableResponse<Response> core = authenticatedJsonRequest(coordinator)
                .get("/members/{id}", memberId).then().statusCode(200).extract();
        String initialEtag = core.header("ETag");
        ExtractableResponse<Response> experience = authenticatedJsonRequest(coordinator)
                .get("/members/{id}/experiences-and-sacraments", memberId)
                .then().statusCode(200).extract();
        ExtractableResponse<Response> contribution = authenticatedJsonRequest(coordinator)
                .get("/members/{id}/contribution-profile", memberId)
                .then().statusCode(200).extract();

        assertThat(initialEtag).isNotBlank().isEqualTo(experience.header("ETag"), contribution.header("ETag"));
        Map<String, String> experiences = experience.path("experiences");
        Map<String, String> sacraments = experience.path("sacraments");
        assertThat(experiences)
                .containsOnlyKeys("JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA", "PASCOA_JUVENIL", "ACAMPABOSCO");
        assertThat(experiences.values()).containsOnly("NOT_INFORMED");
        assertThat(sacraments).containsOnlyKeys("BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA");
        assertThat(sacraments.values()).containsOnly("NOT_INFORMED");
        assertThat(contribution.<List<String>>path("contributionProfile.contributionAreas")).isEmpty();
        assertThat(contribution.<List<String>>path("contributionProfile.otherContributionAreas")).isEmpty();

        Map<String, Object> replacement = Map.of(
                "contributionAreas", List.of("FOOTBALL", "TERERE"),
                "otherContributionAreas", List.of("Synthetic event cooking"),
                "reason", "  Synthetic contribution review  "
        );
        ExtractableResponse<Response> updated = authenticatedJsonRequest(coordinator)
                .header("If-Match", initialEtag)
                .body(replacement)
                .put("/members/{id}/contribution-profile", memberId)
                .then().statusCode(204).extract();
        String updatedEtag = updated.header("ETag");

        assertThat(updatedEtag).isNotBlank().isNotEqualTo(initialEtag);
        assertThat(authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().header("ETag")).isEqualTo(updatedEtag);
        assertThat(activityCount("MEMBER_CONTRIBUTION_PROFILE_UPDATED")).isEqualTo(1);
        Map<String, Object> activity = activity("MEMBER_CONTRIBUTION_PROFILE_UPDATED");
        assertThat(activity).containsEntry("target_type", "MEMBER").containsEntry("target_id", memberId)
                .containsEntry("reason", "Synthetic contribution review");
        assertThat(activity.get("metadata").toString())
                .contains("contributionAreas", "otherContributionAreas")
                .doesNotContain("FOOTBALL", "TERERE", "Synthetic event cooking");

        authenticatedJsonRequest(coordinator)
                .header("If-Match", initialEtag)
                .body(replacement)
                .put("/members/{id}/contribution-profile", memberId)
                .then().statusCode(412).body("status", equalTo(412));
        assertThat(activityCount("MEMBER_CONTRIBUTION_PROFILE_UPDATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-014/015 - protected annual read -> full response and exactly one minimized activity")
    void protectedAnnualReadShouldReturnFullContractAndPersistMinimizedActivity() {
        AuthSession coordinator = newSession("COORD");
        AuthSession visitor = newSession("VISITOR");
        UUID accountId = newAccount("Synthetic annual-information target");
        UUID memberId = registerMember(coordinator, accountId);
        UUID responseId = insertSyntheticAnnualResponse(memberId);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .get("/members/{id}/annual-information/{cycle}", memberId, 2026)
                .then().statusCode(200).extract();

        assertThat((String) response.path("id")).isEqualTo(responseId.toString());
        assertThat(response.<List<String>>path("occupations.values")).containsExactly("WORK", "OTHER");
        assertThat((String) response.path("healthCondition.details")).isEqualTo("Synthetic health detail");
        assertThat((String) response.path("additionalComments")).isEqualTo("Synthetic annual comment");
        assertThat(activityCount("MEMBER_ANNUAL_INFORMATION_READ")).isEqualTo(1);
        Map<String, Object> activity = activity("MEMBER_ANNUAL_INFORMATION_READ");
        assertThat(activity)
                .containsEntry("target_type", "MEMBER_ANNUAL_INFORMATION_RESPONSE")
                .containsEntry("target_id", responseId)
                .containsEntry("reason", null);
        assertThat(activity.get("metadata").toString())
                .contains(memberId.toString(), "2026")
                .doesNotContain("Synthetic health detail", "Synthetic annual comment", "WORK", "OTHER");

        authenticatedJsonRequest(visitor)
                .get("/members/{id}/annual-information/{cycle}", memberId, 2026)
                .then().statusCode(403).body("status", equalTo(403));
        assertThat(activityCount("MEMBER_ANNUAL_INFORMATION_READ")).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-015/016 - annual-read audit failure -> protected response is not disclosed")
    void annualReadAuditFailureShouldPreventDisclosure() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Synthetic annual audit-failure target");
        UUID memberId = registerMember(coordinator, accountId);
        insertSyntheticAnnualResponse(memberId);
        clearActivities();
        failActivityWritesFor("MEMBER_ANNUAL_INFORMATION_READ");

        try {
            authenticatedJsonRequest(coordinator)
                    .get("/members/{id}/annual-information/{cycle}", memberId, 2026)
                    .then().statusCode(500).body("status", equalTo(500));
        } finally {
            removeActivityFailureTrigger();
        }
        assertThat(activityCount("MEMBER_ANNUAL_INFORMATION_READ")).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002/004/016 and REQ-API-ERROR-002/003 - invalid domain inputs -> structured safe validation violations")
    void invalidCoreAndDietaryDomainInputsShouldUseStructuredValidationErrors() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Synthetic validation target");
        UUID memberId = registerMember(coordinator, accountId);
        String etag = authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().header("ETag");

        ExtractableResponse<Response> invalidName = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of(
                        "firstName", "ana", "surname", "Silva",
                        "birthDate", "2000-01-01", "residentialCity", "Synthetic City",
                        "phoneNumber", CANONICAL_PHONE, "contactEmail", "validation@example.com",
                        "reason", "Synthetic validation"
                ))
                .put("/members/{id}", memberId).then().extract();
        assertValidationViolation(invalidName, "/firstName", "FORMAT", "ana");

        ExtractableResponse<Response> invalidSurname = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of(
                        "firstName", "Ana", "surname", "silva",
                        "birthDate", "2000-01-01", "residentialCity", "Synthetic City",
                        "phoneNumber", CANONICAL_PHONE, "contactEmail", "validation@example.com",
                        "reason", "Synthetic validation"
                ))
                .put("/members/{id}", memberId).then().extract();
        assertValidationViolation(invalidSurname, "/surname", "FORMAT", "silva");

        ExtractableResponse<Response> underage = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of(
                        "firstName", "Ana", "surname", "Silva",
                        "birthDate", LocalDate.now().minusYears(17).plusDays(1).toString(),
                        "residentialCity", "Synthetic City", "phoneNumber", CANONICAL_PHONE,
                        "contactEmail", "validation@example.com", "reason", "Synthetic validation"
                ))
                .put("/members/{id}", memberId).then().extract();
        assertValidationViolation(underage, "/birthDate", "RANGE", "Member must be at least");

        ExtractableResponse<Response> invalidDietary = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of("status", "YES", "details", "", "reason", "Synthetic validation"))
                .put("/members/{id}/dietary-restriction", memberId).then().extract();
        assertValidationViolation(invalidDietary, "/details", "RELATION", "Dietary restriction details");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002/004/006 - Unicode whitespace -> normalized at current Member boundaries")
    void unicodeWhitespaceShouldNormalizeCityCustomContributionAndDietaryDetails() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Synthetic Unicode normalization target");
        UUID memberId = registerMember(coordinator, accountId);
        String etag = authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().header("ETag");

        ExtractableResponse<Response> core = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of(
                        "firstName", "Ana", "surname", "Silva", "birthDate", "2000-01-01",
                        "residentialCity", "\u00a0Synthetic\u2003City\u00a0", "phoneNumber", CANONICAL_PHONE,
                        "contactEmail", "unicode@example.com", "reason", "Synthetic Unicode review"
                ))
                .put("/members/{id}", memberId).then().statusCode(204).extract();
        etag = core.header("ETag");
        assertThat(authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().<String>path("residentialCity")).isEqualTo("Synthetic City");

        ExtractableResponse<Response> contribution = authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of(
                        "contributionAreas", List.of("FOOTBALL"),
                        "otherContributionAreas", List.of("\u00a0Synthetic\u2003Cooking\u00a0"),
                        "reason", "Synthetic Unicode review"
                ))
                .put("/members/{id}/contribution-profile", memberId).then().statusCode(204).extract();
        etag = contribution.header("ETag");
        assertThat(authenticatedJsonRequest(coordinator).get("/members/{id}/contribution-profile", memberId)
                .then().statusCode(200).extract().<List<String>>path("contributionProfile.otherContributionAreas"))
                .containsExactly("Synthetic Cooking");

        authenticatedJsonRequest(coordinator)
                .header("If-Match", etag)
                .body(Map.of("status", "YES", "details", "\u00a0Lactose\u00a0", "reason", "Synthetic Unicode review"))
                .put("/members/{id}/dietary-restriction", memberId).then().statusCode(204);
        assertThat(authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().<String>path("dietaryRestriction.details")).isEqualTo("Lactose");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002/003 and REQ-API-ERROR-003 - solicitation city -> 100-code-point boundary and structured overflow")
    void solicitationCityShouldUseUnicodeCodePointBoundary() {
        AuthSession acceptedApplicant = newSession("VISITOR");
        Map<String, Object> accepted = new HashMap<>(
                solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION));
        accepted.put("residentialCity", "A".repeat(99) + "\ud83c\udf06");
        authenticatedJsonRequest(acceptedApplicant).body(accepted).post("/membership-solicitations")
                .then().statusCode(201);

        AuthSession rejectedApplicant = newSession("VISITOR");
        Map<String, Object> rejected = new HashMap<>(
                solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION));
        rejected.put("residentialCity", "A".repeat(100) + "\ud83c\udf06");
        ExtractableResponse<Response> response = authenticatedJsonRequest(rejectedApplicant)
                .body(rejected).post("/membership-solicitations").then().extract();
        assertValidationViolation(response, "/residentialCity", "SIZE", "A".repeat(20));
    }

    private static void assertValidationViolation(
            ExtractableResponse<Response> response,
            String field,
            String code,
            String forbiddenText
    ) {
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        assertThat(response.<String>path("code")).isEqualTo("VALIDATION_ERROR");
        List<Map<String, Object>> violations = response.path("details.violations");
        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation)
                .containsOnlyKeys("location", "field", "code", "message")
                .containsEntry("location", "body")
                .containsEntry("field", field)
                .containsEntry("code", code));
        assertThat(response.asString()).doesNotContain(forbiddenText, "IllegalArgumentException", "MemberEntity");
    }

    private UUID insertSyntheticAnnualResponse(UUID memberId) {
        UUID responseId = UUIDGenerator.generateUUIDV7();
        annualResponseIds.add(responseId);
        jdbcTemplate.update("""
                INSERT INTO annual_member_information_responses (
                    id, member_id, survey_cycle, submitted_at, occupations_details,
                    health_condition_status, health_condition_details, religious_vocation_considered,
                    mass_attendance_frequency, saturday_oratorio_impediment_status,
                    saturday_oratorio_impediment_details, formation_and_meeting_interests,
                    coordination_interest, additional_comments, oratorio_activity_suggestions,
                    instagram_post_suggestions, import_batch_id, created_at
                ) VALUES (?, ?, 2026, ?, ?, CAST('YES' AS member_information_status_enum), ?,
                    CAST('NO' AS member_information_status_enum),
                    CAST('WEEKLY' AS member_mass_attendance_frequency_enum),
                    CAST('NO' AS member_information_status_enum), NULL, NULL,
                    CAST('MAYBE' AS member_coordination_interest_enum), ?, NULL, NULL, NULL, ?)
                """, responseId, memberId, Timestamp.from(Instant.parse("2026-02-02T01:28:11Z")),
                "Synthetic occupation detail", "Synthetic health detail", "Synthetic annual comment",
                Timestamp.from(Instant.now()));
        jdbcTemplate.update("""
                INSERT INTO annual_member_occupations (response_id, occupation)
                VALUES (?, CAST('OTHER' AS member_occupation_enum)),
                       (?, CAST('WORK' AS member_occupation_enum))
                """, responseId, responseId);
        return responseId;
    }
}
