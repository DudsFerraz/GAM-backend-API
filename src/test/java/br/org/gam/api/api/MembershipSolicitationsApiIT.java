package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Membership Solicitations")
class MembershipSolicitationsApiIT extends MemberApiTestSupport {

    private static final int REJECTION_GATE_LOCK = 7_142_026;

    @Test
    @DisplayName("REQ-MEMBER-SOL-002, REQ-MEMBER-SOL-005, REQ-MEMBER-SOL-008, REQ-MEMBER-SOL-012 - protected routes without authentication -> HTTP 401")
    void protectedSolicitationRoutesShouldRejectUnauthenticatedRequests() {
        UUID solicitationId = UUID.randomUUID();

        jsonRequest()
                .body(solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION))
                .post("/membership-solicitations")
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .get("/membership-solicitations/{id}", solicitationId)
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .body(searchPayload())
                .post("/membership-solicitations/search")
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .body(reasonPayload(VALID_REASON))
                .patch("/membership-solicitations/{id}/approve", solicitationId)
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-001, REQ-MEMBER-SOL-002, REQ-MEMBER-SOL-006, REQ-MEMBER-SOL-013 - valid self-submission -> pending immutable resource and one event without membership")
    void validSelfSubmissionShouldCreateOnlyAPendingSolicitation() {
        AuthSession applicant = newSession("VISITOR");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .header("User-Agent", "membership-solicitation-functional-test")
                .body(solicitationPayload(
                        LocalDate.now().minusYears(17),
                        "  " + VALID_JUSTIFICATION + "  "
                ))
                .post("/membership-solicitations")
                .then()
                .statusCode(201)
                .header("Location", containsString("/membership-solicitations/"))
                .extract();

        UUID solicitationId = UUID.fromString(response.path("id"));
        assertUuidV7(solicitationId);
        Map<String, Object> record = response.jsonPath().getMap("$");
        assertSolicitationRecord(record, solicitationId, applicant, "PENDING");
        assertThat(record)
                .containsEntry("reviewedBy", null)
                .containsEntry("decidedAt", null)
                .containsEntry("reviewReason", null)
                .containsEntry("memberId", null);

        assertThat(solicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(pendingSolicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId()))
                .contains("VISITOR")
                .doesNotContain("MEMBER");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_SUBMITTED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        Map<String, Object> activity = activity("MEMBERSHIP_SOLICITATION_SUBMITTED");
        assertThat(activity)
                .containsEntry("actor_account_id", applicant.accountId())
                .containsEntry("target_id", solicitationId)
                .containsEntry("reason", null);
        assertThat(activity.get("metadata").toString())
                .contains(solicitationId.toString(), applicant.accountId().toString())
                .doesNotContain(VALID_JUSTIFICATION, CANONICAL_PHONE);
        assertThat(activity.get("request_id")).isNotNull();
        assertThat(activity.get("user_agent")).isEqualTo("membership-solicitation-functional-test");
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-002 - request-supplied accountId -> HTTP 400 and cannot apply for another Account")
    void requestSuppliedAccountIdShouldBeRejected() {
        AuthSession applicant = newSession("VISITOR");
        UUID otherAccountId = newAccount("Other application target");
        Map<String, Object> payload = new HashMap<>(
                solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION)
        );
        payload.put("accountId", otherAccountId.toString());
        clearActivities();

        authenticatedJsonRequest(applicant)
                .body(payload)
                .post("/membership-solicitations")
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(solicitationCount(applicant.accountId())).isZero();
        assertThat(solicitationCount(otherAccountId)).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSubmissionData")
    @DisplayName("REQ-MEMBER-SOL-002 and REQ-MEMBER-SOL-012 - invalid form data -> HTTP 400 without solicitation, membership, roles, or audit")
    void invalidSubmissionDataShouldReturnBadRequest(
            String label,
            String firstName,
            String surname,
            LocalDate birthDate,
            String phoneNumber,
            String justification
    ) {
        AuthSession applicant = newSession("VISITOR");
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstName", firstName);
        payload.put("surname", surname);
        payload.put("birthDate", birthDate == null ? null : birthDate.toString());
        payload.put("phoneNumber", phoneNumber);
        payload.put("justification", justification);
        clearActivities();

        authenticatedJsonRequest(applicant)
                .body(payload)
                .post("/membership-solicitations")
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(solicitationCount(applicant.accountId())).isZero();
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedJustificationBoundaries")
    @DisplayName("REQ-MEMBER-SOL-002 - justification boundaries after trimming -> HTTP 201 with normalized snapshot")
    void validJustificationBoundariesShouldBeAccepted(
            String label,
            String submittedJustification,
            String normalizedJustification
    ) {
        AuthSession applicant = newSession("VISITOR");

        authenticatedJsonRequest(applicant)
                .body(solicitationPayload(LocalDate.now().minusYears(20), submittedJustification))
                .post("/membership-solicitations")
                .then()
                .statusCode(201)
                .body("justification", equalTo(normalizedJustification));
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-002 and REQ-MEMBER-SOL-012 - existing lifetime Member -> HTTP 409 without solicitation")
    void existingMemberShouldNotSubmitSolicitation() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        registerMember(coordinator, applicant.accountId());
        clearActivities();

        authenticatedJsonRequest(applicant)
                .body(solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION))
                .post("/membership-solicitations")
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(solicitationCount(applicant.accountId())).isZero();
        assertThat(memberCount(applicant.accountId())).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-004 and REQ-MEMBER-SOL-012 - second pending solicitation -> HTTP 409 without duplicate or event")
    void secondPendingSolicitationShouldReturnConflict() {
        AuthSession applicant = newSession("VISITOR");
        UUID firstSolicitationId = submitSolicitation(applicant);
        long submittedEventsBeforeConflict = activityCount("MEMBERSHIP_SOLICITATION_SUBMITTED");

        authenticatedJsonRequest(applicant)
                .body(solicitationPayload(LocalDate.now().minusYears(20), "Another application"))
                .post("/membership-solicitations")
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(solicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(pendingSolicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(solicitationStatus(firstSolicitationId)).isEqualTo("PENDING");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_SUBMITTED"))
                .isEqualTo(submittedEventsBeforeConflict);
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-004 and REQ-MEMBER-SOL-012 - concurrent submissions -> one pending solicitation, one winner, and one event")
    void concurrentSubmissionsShouldCommitExactlyOnePendingSolicitation() throws Exception {
        AuthSession applicant = newSession("VISITOR");
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> submitAfter(start, applicant, "First concurrent application"), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> submitAfter(start, applicant, "Second concurrent application"), executor);

            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(solicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(pendingSolicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_SUBMITTED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-003 and REQ-MEMBER-SOL-002/004/011 - direct registration racing submission -> one outcome and one event")
    void directRegistrationRacingSubmissionShouldCommitExactlyOneOutcome() throws Exception {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> submission = CompletableFuture.supplyAsync(
                    () -> submitAfter(start, applicant, "Concurrent membership application"), executor);
            CompletableFuture<Integer> directRegistration = CompletableFuture.supplyAsync(
                    () -> registerAfter(start, coordinator, applicant.accountId()), executor);

            start.countDown();
            assertThat(List.of(submission.get(), directRegistration.get()))
                    .containsExactlyInAnyOrder(201, 409);
        }

        long members = memberCount(applicant.accountId());
        long pendingSolicitations = pendingSolicitationCount(applicant.accountId());
        assertThat(members + pendingSolicitations).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")
                + activityCount("MEMBERSHIP_SOLICITATION_SUBMITTED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        if (members == 1) {
            assertThat(pendingSolicitations).isZero();
            assertThat(activeRoleNames(applicant.accountId())).contains("MEMBER").doesNotContain("VISITOR");
        } else {
            assertThat(pendingSolicitations).isEqualTo(1);
            assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-003 and REQ-MEMBER-SOL-004 - rejected applicant reapplies -> new pending UUID and preserved rejected snapshot")
    void rejectedApplicantShouldCreateANewImmutableSolicitation() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID rejectedId = submitSolicitation(applicant);
        rejectSolicitation(coordinator, rejectedId, "Please clarify availability");

        Map<String, Object> correctedPayload = new HashMap<>(
                solicitationPayload(LocalDate.now().minusYears(20), "I can now attend every week")
        );
        correctedPayload.put("surname", "Souza");

        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body(correctedPayload)
                .post("/membership-solicitations")
                .then()
                .statusCode(201)
                .extract();

        UUID pendingId = UUID.fromString(response.path("id"));
        assertThat(pendingId).isNotEqualTo(rejectedId);
        assertUuidV7(pendingId);
        assertThat(solicitationCount(applicant.accountId())).isEqualTo(2);
        assertThat(solicitationStatus(rejectedId)).isEqualTo("REJECTED");
        assertThat(solicitationStatus(pendingId)).isEqualTo("PENDING");

        authenticatedJsonRequest(applicant)
                .get("/membership-solicitations/{id}", rejectedId)
                .then()
                .statusCode(200)
                .body("surname", equalTo("Silva"))
                .body("justification", equalTo(VALID_JUSTIFICATION))
                .body("reviewReason", equalTo("Please clarify availability"));
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-005 and REQ-MEMBER-SOL-012 - direct lookup -> own history or manager visibility, foreign history hidden as 404")
    void solicitationLookupShouldEnforceOwnershipWithoutExistenceDisclosure() {
        AuthSession coordinator = newSession("COORD");
        AuthSession firstApplicant = newSession("VISITOR");
        AuthSession secondApplicant = newSession("VISITOR");
        UUID firstId = submitSolicitation(firstApplicant);
        UUID secondId = submitSolicitation(secondApplicant);

        authenticatedJsonRequest(firstApplicant)
                .get("/membership-solicitations/{id}", firstId)
                .then()
                .statusCode(200)
                .body("id", equalTo(firstId.toString()));

        authenticatedJsonRequest(firstApplicant)
                .get("/membership-solicitations/{id}", secondId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        authenticatedJsonRequest(coordinator)
                .get("/membership-solicitations/{id}", secondId)
                .then()
                .statusCode(200)
                .body("id", equalTo(secondId.toString()));

        authenticatedJsonRequest(coordinator)
                .get("/membership-solicitations/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-005 and REQ-MEMBER-SOL-007 - empty and caller-supplied filters -> ownership scope cannot be bypassed")
    void solicitationSearchShouldApplyOwnershipInAdditionToFilters() {
        AuthSession coordinator = newSession("COORD");
        AuthSession firstApplicant = newSession("VISITOR");
        AuthSession secondApplicant = newSession("VISITOR");
        UUID firstId = submitSolicitation(firstApplicant);
        UUID secondId = submitSolicitation(secondApplicant);

        ExtractableResponse<Response> ownHistory = authenticatedJsonRequest(firstApplicant)
                .body(searchPayload())
                .post("/membership-solicitations/search?size=100")
                .then()
                .statusCode(200)
                .extract();
        assertThat(resourceIds(ownHistory.jsonPath().getList("items")))
                .contains(firstId)
                .doesNotContain(secondId);

        ExtractableResponse<Response> attemptedBypass = authenticatedJsonRequest(firstApplicant)
                .body(searchPayload(filter("accountId", secondApplicant.accountId().toString(), "EQUALS")))
                .post("/membership-solicitations/search?size=100")
                .then()
                .statusCode(200)
                .extract();
        assertThat(resourceIds(attemptedBypass.jsonPath().getList("items"))).isEmpty();

        ExtractableResponse<Response> managerHistory = authenticatedJsonRequest(coordinator)
                .body(searchPayload())
                .post("/membership-solicitations/search?size=100")
                .then()
                .statusCode(200)
                .extract();
        assertThat(resourceIds(managerHistory.jsonPath().getList("items"))).contains(firstId, secondId);
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-007 - every documented public filter and comparison -> finds the target solicitation")
    void documentedSolicitationSearchFiltersShouldFindTheTarget() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        rejectSolicitation(coordinator, solicitationId, "Application reviewed for search coverage");

        String emailPrefix = applicant.email().substring(0, applicant.email().indexOf('@'));
        List<Map<String, Object>> filters = List.of(
                filter("id", solicitationId.toString(), "EQUALS"),
                filter("id", List.of(solicitationId.toString()), "IN"),
                filter("accountId", applicant.accountId().toString(), "EQUALS"),
                filter("email", applicant.email(), "EQUALS"),
                filter("email", emailPrefix, "LIKE"),
                filter("name", "Silva", "LIKE"),
                filter("status", "REJECTED", "EQUALS"),
                filter("status", List.of("PENDING", "REJECTED"), "IN"),
                filter("submittedAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("submittedAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL"),
                filter("decidedAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("decidedAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL"),
                filter("reviewedByAccountId", coordinator.accountId().toString(), "EQUALS")
        );

        for (Map<String, Object> searchFilter : filters) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                    .body(searchPayload(searchFilter))
                    .post("/membership-solicitations/search?size=100")
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparationMethod"))
                    .isEqualTo(200);
            assertThat(resourceIds(response.jsonPath().getList("items")))
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparationMethod"))
                    .contains(solicitationId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-007 - unsupported method, invalid value, and unknown field -> safe HTTP 400 messages")
    void invalidSolicitationSearchFiltersShouldReturnSafeBadRequestMessages() {
        AuthSession coordinator = newSession("COORD");

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("accountId", coordinator.accountId().toString(), "LIKE")))
                .post("/membership-solicitations/search")
                .then()
                .statusCode(400)
                .body("message", containsString("accountId"));

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("status", "CANCELLED", "EQUALS")))
                .post("/membership-solicitations/search")
                .then()
                .statusCode(400)
                .body("message", containsString("status"));

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("reviewedBy.id", coordinator.accountId().toString(), "EQUALS")))
                .post("/membership-solicitations/search")
                .then()
                .statusCode(400)
                .body("message", equalTo("Unknown filter field."));
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-008 and REQ-MEMBER-SOL-012 - review without MEMBER_MANAGE -> HTTP 403 and pending state unchanged")
    void reviewWithoutMemberManageShouldReturnForbidden() {
        AuthSession applicant = newSession("VISITOR");
        AuthSession unrelatedVisitor = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();

        authenticatedJsonRequest(unrelatedVisitor)
                .body(reasonPayload(VALID_REASON))
                .patch("/membership-solicitations/{id}/approve", solicitationId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(solicitationStatus(solicitationId)).isEqualTo("PENDING");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("invalidReviewReasons")
    @DisplayName("REQ-MEMBER-SOL-008 and REQ-MEMBER-SOL-012 - invalid approval/rejection reason -> HTTP 400 before mutation")
    void invalidReviewReasonShouldReturnBadRequest(
            String decision,
            String label,
            String route,
            String reason
    ) {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch(route, solicitationId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(solicitationStatus(solicitationId)).isEqualTo("PENDING");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-006, REQ-MEMBER-SOL-008, REQ-MEMBER-SOL-009, REQ-MEMBER-SOL-013 - approval -> one active Member, role projection, decided response, and one event")
    void approvalShouldCommitTheCompleteMembershipWorkflow() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        grantRole(applicant.accountId(), "COORD");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(reasonPayload("  Approved after Coordinator review  "))
                .patch("/membership-solicitations/{id}/approve", solicitationId)
                .then()
                .statusCode(200)
                .extract();

        Map<String, Object> record = response.jsonPath().getMap("$");
        assertSolicitationRecord(record, solicitationId, applicant, "APPROVED");
        UUID memberId = UUID.fromString((String) record.get("memberId"));
        assertUuidV7(memberId);
        assertDecisionFields(record, coordinator, "Approved after Coordinator review", memberId);
        assertThat(solicitationStatus(solicitationId)).isEqualTo("APPROVED");
        assertThat(memberCount(applicant.accountId())).isEqualTo(1);
        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(applicant.accountId()))
                .contains("MEMBER", "COORD")
                .doesNotContain("VISITOR");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_APPROVED")).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")).isZero();
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        Map<String, Object> activity = activity("MEMBERSHIP_SOLICITATION_APPROVED");
        assertThat(activity)
                .containsEntry("actor_account_id", coordinator.accountId())
                .containsEntry("target_id", solicitationId)
                .containsEntry("reason", "Approved after Coordinator review");
        assertThat(activity.get("metadata").toString())
                .contains(
                        solicitationId.toString(),
                        applicant.accountId().toString(),
                        memberId.toString(),
                        roleId("MEMBER").toString(),
                        roleId("VISITOR").toString()
                )
                .doesNotContain(VALID_JUSTIFICATION, CANONICAL_PHONE);
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-009 and REQ-MEMBER-SOL-013 - approval audit failure -> decision, Member, and role projection roll back")
    void failedApprovalAuditShouldRollBackTheCompleteMembershipWorkflow() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();
        failActivityWritesFor("MEMBERSHIP_SOLICITATION_APPROVED");

        try {
            authenticatedJsonRequest(coordinator)
                    .body(reasonPayload(VALID_REASON))
                    .patch("/membership-solicitations/{id}/approve", solicitationId)
                    .then()
                    .statusCode(500)
                    .body("status", equalTo(500));
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(solicitationStatus(solicitationId)).isEqualTo("PENDING");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-006, REQ-MEMBER-SOL-008, REQ-MEMBER-SOL-010, REQ-MEMBER-SOL-013 - rejection -> immutable decision without Member or role change and one event")
    void rejectionShouldCommitOnlyTheSolicitationDecisionAndAudit() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        grantRole(applicant.accountId(), "COORD");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(reasonPayload("  Please provide more availability details  "))
                .patch("/membership-solicitations/{id}/reject", solicitationId)
                .then()
                .statusCode(200)
                .extract();

        Map<String, Object> record = response.jsonPath().getMap("$");
        assertSolicitationRecord(record, solicitationId, applicant, "REJECTED");
        assertDecisionFields(record, coordinator, "Please provide more availability details", null);
        assertThat(solicitationStatus(solicitationId)).isEqualTo("REJECTED");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId()))
                .contains("VISITOR", "COORD")
                .doesNotContain("MEMBER");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_REJECTED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
        assertThat(activity("MEMBERSHIP_SOLICITATION_REJECTED"))
                .containsEntry("reason", "Please provide more availability details")
                .containsEntry("target_id", solicitationId);
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-003, REQ-MEMBER-SOL-011, REQ-MEMBER-SOL-012 - review of decided solicitation -> HTTP 409 and immutable first outcome")
    void decidedSolicitationShouldRejectAnotherDecision() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        rejectSolicitation(coordinator, solicitationId, "First and final decision");
        long rejectedEventsBeforeConflict = activityCount("MEMBERSHIP_SOLICITATION_REJECTED");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Attempt to reverse the decision"))
                .patch("/membership-solicitations/{id}/approve", solicitationId)
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(solicitationStatus(solicitationId)).isEqualTo("REJECTED");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_REJECTED"))
                .isEqualTo(rejectedEventsBeforeConflict);
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_APPROVED")).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-009 and REQ-MEMBER-SOL-012 - soft-deleted applicant Account at approval -> HTTP 404 without partial decision")
    void approvalShouldRevalidateApplicantAccountAvailability() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();
        softDeleteAccount(applicant.accountId());

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(VALID_REASON))
                .patch("/membership-solicitations/{id}/approve", solicitationId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(solicitationStatus(solicitationId)).isEqualTo("PENDING");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-011 - concurrent approval and rejection -> one immutable outcome, one winner, and one event")
    void concurrentDecisionsShouldCommitExactlyOneOutcome() throws Exception {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> approval = CompletableFuture.supplyAsync(
                    () -> decideAfter(start, coordinator, solicitationId, "approve", "Concurrent approval"),
                    executor
            );
            CompletableFuture<Integer> rejection = CompletableFuture.supplyAsync(
                    () -> decideAfter(start, coordinator, solicitationId, "reject", "Concurrent rejection"),
                    executor
            );

            start.countDown();
            Set<Integer> statuses = Set.of(approval.get(), rejection.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }

        String committedStatus = solicitationStatus(solicitationId);
        assertThat(committedStatus).isIn("APPROVED", "REJECTED");
        assertThat(
                activityCount("MEMBERSHIP_SOLICITATION_APPROVED")
                        + activityCount("MEMBERSHIP_SOLICITATION_REJECTED")
        ).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        if ("APPROVED".equals(committedStatus)) {
            assertThat(memberCount(applicant.accountId())).isEqualTo(1);
            assertThat(activeRoleNames(applicant.accountId())).contains("MEMBER").doesNotContain("VISITOR");
        } else {
            assertThat(memberCount(applicant.accountId())).isZero();
            assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-004 and REQ-MEMBER-SOL-011 - approval racing direct registration -> approval is the sole Member-creation outcome")
    void approvalRacingDirectRegistrationShouldCommitOnlyApproval() throws Exception {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> approval = CompletableFuture.supplyAsync(
                    () -> decideAfter(start, coordinator, solicitationId, "approve", "Concurrent approval"),
                    executor
            );
            CompletableFuture<Integer> directRegistration = CompletableFuture.supplyAsync(
                    () -> registerAfter(start, coordinator, applicant.accountId()),
                    executor
            );

            start.countDown();
            assertThat(List.of(approval.get(), directRegistration.get())).containsExactlyInAnyOrder(200, 409);
        }

        assertThat(solicitationStatus(solicitationId)).isEqualTo("APPROVED");
        assertThat(memberCount(applicant.accountId())).isEqualTo(1);
        assertThat(activeRoleNames(applicant.accountId())).contains("MEMBER").doesNotContain("VISITOR");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_APPROVED")).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")).isZero();
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-011 - rejection winning the Account lock blocks a concurrent direct registration outcome")
    void rejectionWinningAccountLockShouldBlockConcurrentDirectRegistration() throws Exception {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        clearActivities();
        installRejectionGate();

        try (Connection gate = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            acquireRejectionGate(gate);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                CompletableFuture<Integer> rejection = CompletableFuture.supplyAsync(
                        () -> decide(coordinator, solicitationId, "reject", "Concurrent rejection"),
                        executor
                );
                boolean gateReleased = false;

                try {
                    awaitRejectionGateWait();
                    CompletableFuture<Integer> directRegistration = CompletableFuture.supplyAsync(
                            () -> register(coordinator, applicant.accountId()),
                            executor
                    );
                    awaitAccountLockWait();
                    releaseRejectionGate(gate);
                    gateReleased = true;

                    assertThat(rejection.get()).isEqualTo(200);
                    assertThat(directRegistration.get()).isEqualTo(409);
                } finally {
                    if (!gateReleased) {
                        releaseRejectionGate(gate);
                    }
                }
            }
        } finally {
            removeRejectionGate();
        }

        assertThat(solicitationStatus(solicitationId)).isEqualTo("REJECTED");
        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(activeRoleNames(applicant.accountId())).contains("VISITOR").doesNotContain("MEMBER");
        assertThat(activityCount("MEMBERSHIP_SOLICITATION_REJECTED")).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")).isZero();
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    private int submitAfter(CountDownLatch start, AuthSession applicant, String justification) {
        await(start);
        return authenticatedJsonRequest(applicant)
                .body(solicitationPayload(LocalDate.now().minusYears(20), justification))
                .post("/membership-solicitations")
                .then()
                .extract()
                .statusCode();
    }

    private int registerAfter(CountDownLatch start, AuthSession coordinator, UUID accountId) {
        await(start);
        return register(coordinator, accountId);
    }

    private int register(AuthSession coordinator, UUID accountId) {
        return authenticatedJsonRequest(coordinator)
                .body(memberPayload(accountId, LocalDate.now().minusYears(20), "Concurrent direct registration"))
                .post("/members")
                .then()
                .extract()
                .statusCode();
    }

    private int decideAfter(
            CountDownLatch start,
            AuthSession coordinator,
            UUID solicitationId,
            String decision,
            String reason
    ) {
        await(start);

        return decide(coordinator, solicitationId, decision, reason);
    }

    private int decide(
            AuthSession coordinator,
            UUID solicitationId,
            String decision,
            String reason
    ) {
        return authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch("/membership-solicitations/{id}/" + decision, solicitationId)
                .then()
                .extract()
                .statusCode();
    }

    private void installRejectionGate() {
        removeRejectionGate();
        jdbcTemplate.execute(("""
                CREATE OR REPLACE FUNCTION block_test_rejection() RETURNS trigger AS $$
                BEGIN
                    IF NEW.status = 'REJECTED' THEN
                        PERFORM pg_advisory_xact_lock(%d);
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """).formatted(REJECTION_GATE_LOCK));
        try {
            jdbcTemplate.execute("""
                    CREATE TRIGGER block_test_rejection_trigger
                    BEFORE UPDATE ON membership_solicitations
                    FOR EACH ROW EXECUTE FUNCTION block_test_rejection()
                    """);
        } catch (RuntimeException exception) {
            removeRejectionGate();
            throw exception;
        }
    }

    private void removeRejectionGate() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS block_test_rejection_trigger ON membership_solicitations");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS block_test_rejection()");
    }

    private static void acquireRejectionGate(Connection gate) throws SQLException {
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(" + REJECTION_GATE_LOCK + ")");
        }
    }

    private static void releaseRejectionGate(Connection gate) throws SQLException {
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_advisory_unlock(" + REJECTION_GATE_LOCK + ")");
        }
    }

    private void awaitRejectionGateWait() {
        awaitDatabaseCondition(
                "SELECT EXISTS (SELECT 1 FROM pg_locks "
                        + "WHERE locktype = 'advisory' AND classid = 0 AND objid = ? "
                        + "AND objsubid = 1 AND granted = FALSE)",
                "rejection advisory lock",
                REJECTION_GATE_LOCK
        );
    }

    private void awaitAccountLockWait() {
        awaitDatabaseCondition(
                "SELECT EXISTS (SELECT 1 FROM pg_locks l "
                        + "JOIN pg_stat_activity a ON a.pid = l.pid "
                        + "WHERE l.locktype = 'transactionid' AND l.granted = FALSE "
                        + "AND a.state = 'active' AND a.query ILIKE '%accounts%')",
                "Account row lock"
        );
    }

    private void awaitDatabaseCondition(String query, String description, Object... arguments) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(
                    query,
                    Boolean.class,
                    arguments
            );
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }

        throw new AssertionError("Timed out waiting for blocked " + description);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertDecisionFields(
            Map<String, Object> record,
            AuthSession coordinator,
            String reviewReason,
            UUID memberId
    ) {
        assertThat(record.get("decidedAt")).isNotNull();
        assertThat(record.get("reviewReason")).isEqualTo(reviewReason);
        assertThat(record.get("memberId")).isEqualTo(memberId == null ? null : memberId.toString());

        Map<String, Object> reviewedBy = (Map<String, Object>) record.get("reviewedBy");
        assertThat(reviewedBy).containsOnlyKeys("id", "email", "displayName");
        assertThat(reviewedBy)
                .containsEntry("id", coordinator.accountId().toString())
                .containsEntry("email", coordinator.email());
    }

    private static Stream<Arguments> invalidSubmissionData() {
        LocalDate adultBirthDate = LocalDate.now().minusYears(20);
        return Stream.of(
                Arguments.of("EP - blank first name", " ", "Silva", adultBirthDate, CANONICAL_PHONE, VALID_JUSTIFICATION),
                Arguments.of("EP - blank surname", "Ana", "\t", adultBirthDate, CANONICAL_PHONE, VALID_JUSTIFICATION),
                Arguments.of("EP - invalid phone", "Ana", "Silva", adultBirthDate, "invalid", VALID_JUSTIFICATION),
                Arguments.of(
                        "BVA - one day before seventeenth birthday",
                        "Ana",
                        "Silva",
                        LocalDate.now().minusYears(17).plusDays(1),
                        CANONICAL_PHONE,
                        VALID_JUSTIFICATION
                ),
                Arguments.of(
                        "EP - future birth date",
                        "Ana",
                        "Silva",
                        LocalDate.now().plusDays(1),
                        CANONICAL_PHONE,
                        VALID_JUSTIFICATION
                ),
                Arguments.of("EP - null justification", "Ana", "Silva", adultBirthDate, CANONICAL_PHONE, null),
                Arguments.of("EP - empty justification", "Ana", "Silva", adultBirthDate, CANONICAL_PHONE, ""),
                Arguments.of("EP - whitespace justification", "Ana", "Silva", adultBirthDate, CANONICAL_PHONE, " \n\t "),
                Arguments.of(
                        "BVA - 2,001-character justification",
                        "Ana",
                        "Silva",
                        adultBirthDate,
                        CANONICAL_PHONE,
                        "j".repeat(2_001)
                )
        );
    }

    private static Stream<Arguments> acceptedJustificationBoundaries() {
        return Stream.of(
                Arguments.of("BVA - one character", "  j  ", "j"),
                Arguments.of("BVA - 2,000 characters", "  " + "j".repeat(2_000) + "  ", "j".repeat(2_000))
        );
    }

    private static Stream<Arguments> invalidReviewReasons() {
        List<Arguments> invalid = List.of(
                Arguments.of("EP - null", null),
                Arguments.of("EP - empty", ""),
                Arguments.of("EP - whitespace", " \n\t "),
                Arguments.of("BVA - 2,001 characters", "r".repeat(2_001))
        );
        List<Arguments> decisions = new ArrayList<>();

        for (Arguments reason : invalid) {
            Object[] values = reason.get();
            decisions.add(Arguments.of(
                    "approve",
                    values[0],
                    "/membership-solicitations/{id}/approve",
                    values[1]
            ));
            decisions.add(Arguments.of(
                    "reject",
                    values[0],
                    "/membership-solicitations/{id}/reject",
                    values[1]
            ));
        }
        return decisions.stream();
    }
}
