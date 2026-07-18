package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@DisplayName("API - Member Records and Lifecycle")
class MemberRecordsLifecycleApiIT extends MemberApiTestSupport {

    @Test
    @DisplayName("REQ-MEMBER-003, REQ-MEMBER-007, REQ-MEMBER-008, REQ-MEMBER-011 - protected routes without authentication -> HTTP 401")
    void protectedMemberRoutesShouldRejectUnauthenticatedRequests() {
        UUID resourceId = UUID.randomUUID();

        jsonRequest()
                .body(memberPayload(UUID.randomUUID(), LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .get("/members/{id}", resourceId)
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .body(searchPayload())
                .post("/members/search")
                .then()
                .statusCode(401)
                .body("status", equalTo(401));

        jsonRequest()
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{id}/deactivate", resourceId)
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-MEMBER-003 and REQ-MEMBER-011 - caller without MEMBER_MANAGE -> HTTP 403 without registration")
    void directRegistrationWithoutMemberManageShouldReturnForbidden() {
        AuthSession visitor = newSession("VISITOR");
        UUID targetId = newAccount("Forbidden registration target");
        clearActivities();

        authenticatedJsonRequest(visitor)
                .body(memberPayload(targetId, LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(memberCount(targetId)).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-001, REQ-MEMBER-003, REQ-MEMBER-005, REQ-MEMBER-009, REQ-MEMBER-012 - valid direct registration -> active Member, role projection, record, and one audit event")
    void validDirectRegistrationShouldCommitTheCompleteMemberWorkflow() {
        AuthSession coordinator = newSession("COORD");
        String targetEmail = "direct-member-" + UUID.randomUUID() + "@example.com";
        String targetDisplayName = "Direct Member Target";
        UUID targetId = newAccount(targetEmail, targetDisplayName);
        grantRole(targetId, "VISITOR");
        grantRole(targetId, "COORD");
        clearActivities();

        ExtractableResponse<Response> response = withUntrustedForwardingHeaders(authenticatedJsonRequest(coordinator))
                .header("User-Agent", "member-lifecycle-functional-test")
                .body(memberPayload(
                        targetId,
                        LocalDate.now().minusYears(17),
                        "  Accepted as a GAM Member  "
                ))
                .post("/members")
                .then()
                .statusCode(201)
                .extract();

        UUID memberId = UUID.fromString(response.path("id"));
        assertPublicApiLocation(response, "/members/" + memberId);
        assertUuidV7(memberId);
        assertMemberRecord(
                response.jsonPath().getMap("$"),
                memberId,
                targetId,
                targetEmail,
                targetDisplayName,
                "ACTIVE"
        );
        assertThat(memberCount(targetId)).isEqualTo(1);
        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(targetId))
                .contains("MEMBER", "COORD")
                .doesNotContain("VISITOR");
        assertThat(activityCount("MEMBER_REGISTERED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        Map<String, Object> activity = activity("MEMBER_REGISTERED");
        assertThat(activity)
                .containsEntry("actor_account_id", coordinator.accountId())
                .containsEntry("target_id", memberId)
                .containsEntry("reason", "Accepted as a GAM Member");
        assertThat(activity.get("metadata").toString())
                .contains(targetId.toString(), memberId.toString(), roleId("MEMBER").toString());
        assertThat(activity.get("request_id")).isNotNull();
        assertThat(activity.get("ip_address")).isNotNull();
        assertThat(activity.get("user_agent")).isEqualTo("member-lifecycle-functional-test");
    }

    @Test
    @DisplayName("REQ-MEMBER-005 and REQ-MEMBER-012 - audit persistence failure -> Member and lifecycle-role writes roll back")
    void failedRegistrationAuditShouldRollBackMemberAndRoleProjection() {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Registration rollback target");
        clearActivities();
        failActivityWritesFor("MEMBER_REGISTERED");

        try {
            authenticatedJsonRequest(coordinator)
                    .body(memberPayload(targetId, LocalDate.now().minusYears(20), VALID_REASON))
                    .post("/members")
                    .then()
                    .statusCode(500)
                    .body("status", equalTo(500));
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(memberCount(targetId)).isZero();
        assertThat(activeRoleNames(targetId)).doesNotContain("MEMBER", "VISITOR");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedReasonBoundaries")
    @DisplayName("REQ-MEMBER-006 - valid normalized direct-registration reason boundaries -> HTTP 201 and normalized audit reason")
    void validReasonBoundariesShouldBeAccepted(String label, String submittedReason, String normalizedReason) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Reason boundary target");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, LocalDate.now().minusYears(20), submittedReason))
                .post("/members")
                .then()
                .statusCode(201);

        assertThat(activityCount("MEMBER_REGISTERED")).isEqualTo(1);
        assertThat(activity("MEMBER_REGISTERED").get("reason")).isEqualTo(normalizedReason);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMemberData")
    @DisplayName("REQ-MEMBER-002 and REQ-MEMBER-011 - invalid Member information -> HTTP 400 without mutation")
    void invalidMemberInformationShouldReturnBadRequest(
            String label,
            String firstName,
            String surname,
            LocalDate birthDate,
            String phoneNumber
    ) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Invalid member information target");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, firstName, surname, birthDate, phoneNumber, VALID_REASON))
                .post("/members")
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(memberCount(targetId)).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReasons")
    @DisplayName("REQ-MEMBER-006 and REQ-MEMBER-011 - invalid direct-registration reason -> HTTP 400 without mutation")
    void invalidDirectRegistrationReasonShouldReturnBadRequest(String label, String reason) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Invalid registration reason target");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, LocalDate.now().minusYears(20), reason))
                .post("/members")
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(memberCount(targetId)).isZero();
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-001 and REQ-MEMBER-011 - second lifetime Member for one Account -> HTTP 409 without duplicate state")
    void secondMemberForSameAccountShouldReturnConflict() {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Lifetime identity target");
        registerMember(coordinator, targetId);
        long registrationEventsBeforeConflict = activityCount("MEMBER_REGISTERED");

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, LocalDate.now().minusYears(19), "Duplicate registration attempt"))
                .post("/members")
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(memberCount(targetId)).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")).isEqualTo(registrationEventsBeforeConflict);
    }

    @Test
    @DisplayName("REQ-MEMBER-001 and REQ-MEMBER-011 - concurrent direct registrations -> one Member, one winner, and one event")
    void concurrentDirectRegistrationsShouldCommitExactlyOneMember() throws Exception {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Concurrent registration target");
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> registerAfter(start, coordinator, targetId, "First concurrent registration"), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> registerAfter(start, coordinator, targetId, "Second concurrent registration"), executor);

            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(memberCount(targetId)).isEqualTo(1);
        assertThat(activeRoleNames(targetId)).contains("MEMBER").doesNotContain("VISITOR");
        assertThat(activityCount("MEMBER_REGISTERED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-003 and REQ-MEMBER-SOL-004 - pending solicitation blocks direct registration -> HTTP 409")
    void pendingSolicitationShouldBlockDirectRegistration() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        submitSolicitation(applicant);

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(applicant.accountId(), LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(memberCount(applicant.accountId())).isZero();
        assertThat(pendingSolicitationCount(applicant.accountId())).isEqualTo(1);
        assertThat(activityCount("MEMBER_REGISTERED")).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-003 and REQ-MEMBER-SOL-004 - rejected solicitation history -> direct registration remains eligible")
    void rejectedSolicitationHistoryShouldNotBlockDirectRegistration() {
        AuthSession coordinator = newSession("COORD");
        AuthSession applicant = newSession("VISITOR");
        UUID solicitationId = submitSolicitation(applicant);
        rejectSolicitation(coordinator, solicitationId, "Not ready yet");

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(applicant.accountId(), LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(201);

        assertThat(memberCount(applicant.accountId())).isEqualTo(1);
        assertThat(solicitationStatus(solicitationId)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("REQ-MEMBER-003 and REQ-MEMBER-011 - missing or soft-deleted Account -> HTTP 404 without registration")
    void unavailableAccountShouldReturnNotFoundDuringRegistration() {
        AuthSession coordinator = newSession("COORD");
        UUID missingAccountId = UUID.randomUUID();

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(missingAccountId, LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        UUID deletedAccountId = newAccount("Deleted registration target");
        softDeleteAccount(deletedAccountId);

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(deletedAccountId, LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(memberCount(deletedAccountId)).isZero();
        assertThat(activityCount("MEMBER_REGISTERED")).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-004 through REQ-MEMBER-007 and REQ-MEMBER-012 - deactivation -> INACTIVE, VISITOR, preserved roles, 204, and one event")
    void deactivationShouldCommitStatusRolesAndAuditTogether() {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Deactivation target");
        grantRole(targetId, "COORD");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, "ACTIVE", "MEMBER");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("  Pausing weekly activities  "))
                .patch("/members/{id}/deactivate", memberId)
                .then()
                .statusCode(204);

        assertThat(memberStatus(memberId)).isEqualTo("INACTIVE");
        assertThat(activeRoleNames(targetId))
                .contains("VISITOR", "COORD")
                .doesNotContain("MEMBER");
        assertThat(activityCount("MEMBER_DEACTIVATED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        Map<String, Object> activity = activity("MEMBER_DEACTIVATED");
        assertThat(activity)
                .containsEntry("actor_account_id", coordinator.accountId())
                .containsEntry("target_id", memberId)
                .containsEntry("reason", "Pausing weekly activities");
        assertThat(activity.get("metadata").toString())
                .contains(
                        targetId.toString(),
                        "ACTIVE",
                        "INACTIVE",
                        roleId("MEMBER").toString(),
                        roleId("VISITOR").toString()
                );
    }

    @Test
    @DisplayName("REQ-MEMBER-004 through REQ-MEMBER-007 and REQ-MEMBER-012 - reactivation -> ACTIVE, MEMBER, preserved roles, 204, and one event")
    void reactivationShouldCommitStatusRolesAndAuditTogether() {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Reactivation target");
        grantRole(targetId, "COORD");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, "INACTIVE", "VISITOR");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("  Returning to weekly activities  "))
                .patch("/members/{id}/activate", memberId)
                .then()
                .statusCode(204);

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(targetId))
                .contains("MEMBER", "COORD")
                .doesNotContain("VISITOR");
        assertThat(activityCount("MEMBER_ACTIVATED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
        assertThat(activity("MEMBER_ACTIVATED").get("reason")).isEqualTo("Returning to weekly activities");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sameStatusTransitions")
    @DisplayName("REQ-MEMBER-004 and REQ-MEMBER-011 - same-status lifecycle command -> HTTP 409 without mutation")
    void sameStatusTransitionShouldReturnConflict(String label, String status, String role, String route) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Repeated transition target");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, status, role);
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(VALID_REASON))
                .patch(route, memberId)
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(memberStatus(memberId)).isEqualTo(status);
        assertThat(activeRoleNames(targetId)).contains(role);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("invalidLifecycleReasons")
    @DisplayName("REQ-MEMBER-006, REQ-MEMBER-007, and REQ-MEMBER-011 - invalid lifecycle reason -> HTTP 400 before mutation")
    void invalidLifecycleReasonShouldReturnBadRequest(
            String transition,
            String label,
            String initialStatus,
            String initialRole,
            String route,
            String reason
    ) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Invalid lifecycle reason target");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, initialStatus, initialRole);
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch(route, memberId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(memberStatus(memberId)).isEqualTo(initialStatus);
        assertThat(activeRoleNames(targetId)).contains(initialRole);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-007 and REQ-MEMBER-011 - caller without MEMBER_ACTIVATION -> HTTP 403 without mutation")
    void lifecycleChangeWithoutActivationPermissionShouldReturnForbidden() {
        AuthSession coordinator = newSession("COORD");
        AuthSession visitor = newSession("VISITOR");
        UUID targetId = newAccount("Forbidden lifecycle target");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, "ACTIVE", "MEMBER");
        clearActivities();

        authenticatedJsonRequest(visitor)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{id}/deactivate", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-008, REQ-MEMBER-009, and REQ-MEMBER-011 - visible active Member lookup -> complete safe response")
    void activeMemberLookupShouldReturnTheDocumentedRecord() {
        AuthSession coordinator = newSession("COORD");
        AuthSession memberReader = newSession("MEMBER");
        String targetEmail = "member-lookup-" + UUID.randomUUID() + "@example.com";
        String targetDisplayName = "Member Lookup Target";
        UUID targetId = newAccount(targetEmail, targetDisplayName);
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, "ACTIVE", "MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(memberReader)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertMemberRecord(
                response.jsonPath().getMap("$"),
                memberId,
                targetId,
                targetEmail,
                targetDisplayName,
                "ACTIVE"
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-008 and REQ-MEMBER-011 - inactive visibility -> hidden as 404 unless MEMBER_GET_NON_ACTIVE is present")
    void inactiveMemberLookupShouldRespectNonActiveVisibility() {
        AuthSession coordinator = newSession("COORD");
        AuthSession activeOnlyReader = newSession("MEMBER");
        String targetEmail = "inactive-member-" + UUID.randomUUID() + "@example.com";
        String targetDisplayName = "Inactive visibility target";
        UUID targetId = newAccount(targetEmail, targetDisplayName);
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, "INACTIVE", "VISITOR");

        authenticatedJsonRequest(activeOnlyReader)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        ExtractableResponse<Response> visibleResponse = authenticatedJsonRequest(coordinator)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertMemberRecord(
                visibleResponse.jsonPath().getMap("$"),
                memberId,
                targetId,
                targetEmail,
                targetDisplayName,
                "INACTIVE"
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-013 - missing or soft-deleted Member -> HTTP 404")
    void unavailableMemberLookupShouldReturnNotFound() {
        AuthSession coordinator = newSession("COORD");

        authenticatedJsonRequest(coordinator)
                .get("/members/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        UUID targetId = newAccount("Deleted member target");
        UUID memberId = registerMember(coordinator, targetId);
        softDeleteMember(memberId);

        authenticatedJsonRequest(coordinator)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-MEMBER-011, REQ-MEMBER-013, and REQ-MEMBER-014 - existing Member or search without capability -> HTTP 403")
    void missingReadPermissionsShouldReturnForbidden() {
        AuthSession coordinator = newSession("COORD");
        AuthSession visitor = newSession("VISITOR");
        UUID targetAccountId = newAccount("Forbidden lookup target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "ACTIVE", "MEMBER");

        authenticatedJsonRequest(visitor)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        authenticatedJsonRequest(visitor)
                .body(searchPayload())
                .post("/members/search")
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-008 and REQ-MEMBER-010 - empty search -> page constrained by non-active visibility")
    void emptySearchShouldApplyStatusVisibilityInAdditionToCallerFilters() {
        AuthSession coordinator = newSession("COORD");
        AuthSession activeOnlySearcher = newSessionWithPermissions("MEMBER_SEARCH");
        UUID activeAccountId = newAccount("Active search target");
        UUID inactiveAccountId = newAccount("Inactive search target");
        UUID activeMemberId = registerMember(coordinator, activeAccountId);
        UUID inactiveMemberId = registerMember(coordinator, inactiveAccountId);
        forceMemberState(activeMemberId, activeAccountId, "ACTIVE", "MEMBER");
        forceMemberState(inactiveMemberId, inactiveAccountId, "INACTIVE", "VISITOR");

        ExtractableResponse<Response> activeOnlyResponse = authenticatedJsonRequest(activeOnlySearcher)
                .body(searchPayload())
                .post("/members/search?size=100")
                .then()
                .statusCode(200)
                .extract();

        assertThat(resourceIds(activeOnlyResponse.jsonPath().getList("items")))
                .contains(activeMemberId)
                .doesNotContain(inactiveMemberId);

        ExtractableResponse<Response> coordinatorResponse = authenticatedJsonRequest(coordinator)
                .body(searchPayload())
                .post("/members/search?size=100")
                .then()
                .statusCode(200)
                .extract();

        assertThat(resourceIds(coordinatorResponse.jsonPath().getList("items")))
                .contains(activeMemberId, inactiveMemberId);
    }

    @Test
    @DisplayName("REQ-MEMBER-010 - every documented public filter and comparison -> finds the target Member")
    void documentedMemberSearchFiltersShouldFindTheTarget() {
        AuthSession coordinator = newSession("COORD");
        String targetEmail = "member-search-" + UUID.randomUUID() + "@example.com";
        UUID targetAccountId = newAccount(targetEmail, "Member Search Target");
        UUID targetMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(targetMemberId, targetAccountId, "ACTIVE", "MEMBER");

        List<Map<String, Object>> filters = List.of(
                filter("id", targetMemberId.toString(), "EQUALS"),
                filter("id", List.of(targetMemberId.toString()), "IN"),
                filter("name", "Silva", "LIKE"),
                filter("birthDate", LocalDate.now().minusYears(20).toString(), "EQUALS"),
                filter("birthDate", "1900-01-01", "GREATER_THAN_OR_EQUAL"),
                filter("birthDate", LocalDate.now().toString(), "LESS_THAN_OR_EQUAL"),
                filter("phoneNumber", CANONICAL_PHONE, "EQUALS"),
                filter("phoneNumber", "99887", "LIKE"),
                filter("status", "ACTIVE", "EQUALS"),
                filter("status", List.of("ACTIVE", "INACTIVE"), "IN"),
                filter("accountId", targetAccountId.toString(), "EQUALS"),
                filter("email", targetEmail, "EQUALS"),
                filter("email", targetEmail.substring(0, targetEmail.indexOf('@')), "LIKE"),
                filter("role", "MEMBER", "EQUALS"),
                filter("role", List.of("MEMBER", "COORD"), "IN"),
                filter("createdAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("createdAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL"),
                filter("updatedAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("updatedAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL")
        );

        for (Map<String, Object> searchFilter : filters) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                    .body(searchPayload(searchFilter))
                    .post("/members/search?size=100")
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparationMethod"))
                    .isEqualTo(200);
            assertThat(resourceIds(response.jsonPath().getList("items")))
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparationMethod"))
                    .contains(targetMemberId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-010 - unsupported method, invalid value, and unknown field -> safe HTTP 400 messages")
    void invalidMemberSearchFiltersShouldReturnSafeBadRequestMessages() {
        AuthSession coordinator = newSession("COORD");

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("id", UUID.randomUUID().toString(), "LIKE")))
                .post("/members/search")
                .then()
                .statusCode(400)
                .body("message", containsString("id"));

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("status", "PENDENT", "EQUALS")))
                .post("/members/search")
                .then()
                .statusCode(400)
                .body("message", containsString("status"));

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("account.accountRoles.role.name", "MEMBER", "EQUALS")))
                .post("/members/search")
                .then()
                .statusCode(400)
                .body("message", equalTo("Unknown filter field."));
    }

    private int registerAfter(
            CountDownLatch start,
            AuthSession coordinator,
            UUID accountId,
            String reason
    ) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }

        return authenticatedJsonRequest(coordinator)
                .body(memberPayload(accountId, LocalDate.now().minusYears(20), reason))
                .post("/members")
                .then()
                .extract()
                .statusCode();
    }

    private static Stream<Arguments> acceptedReasonBoundaries() {
        return Stream.of(
                Arguments.of("BVA - one character", "  x  ", "x"),
                Arguments.of("BVA - 2,000 characters", "  " + "r".repeat(2_000) + "  ", "r".repeat(2_000))
        );
    }

    private static Stream<Arguments> invalidMemberData() {
        return Stream.of(
                Arguments.of(
                        "EP - blank first name",
                        " ",
                        "Silva",
                        LocalDate.now().minusYears(20),
                        CANONICAL_PHONE
                ),
                Arguments.of(
                        "EP - blank surname",
                        "Ana",
                        "\t",
                        LocalDate.now().minusYears(20),
                        CANONICAL_PHONE
                ),
                Arguments.of(
                        "EP - invalid phone",
                        "Ana",
                        "Silva",
                        LocalDate.now().minusYears(20),
                        "not-a-phone"
                ),
                Arguments.of(
                        "BVA - one day before seventeenth birthday",
                        "Ana",
                        "Silva",
                        LocalDate.now().minusYears(17).plusDays(1),
                        CANONICAL_PHONE
                ),
                Arguments.of(
                        "EP - future birth date",
                        "Ana",
                        "Silva",
                        LocalDate.now().plusDays(1),
                        CANONICAL_PHONE
                )
        );
    }

    private static Stream<Arguments> invalidReasons() {
        return Stream.of(
                Arguments.of("EP - null", null),
                Arguments.of("EP - empty", ""),
                Arguments.of("EP - whitespace", " \n\t "),
                Arguments.of("BVA - 2,001 characters", "r".repeat(2_001))
        );
    }

    private static Stream<Arguments> sameStatusTransitions() {
        return Stream.of(
                Arguments.of("already active -> activate", "ACTIVE", "MEMBER", "/members/{id}/activate"),
                Arguments.of("already inactive -> deactivate", "INACTIVE", "VISITOR", "/members/{id}/deactivate")
        );
    }

    private static Stream<Arguments> invalidLifecycleReasons() {
        List<Arguments> arguments = new java.util.ArrayList<>();
        for (Arguments reason : invalidReasons().toList()) {
            Object[] values = reason.get();
            arguments.add(Arguments.of(
                    "deactivate",
                    values[0],
                    "ACTIVE",
                    "MEMBER",
                    "/members/{id}/deactivate",
                    values[1]
            ));
            arguments.add(Arguments.of(
                    "activate",
                    values[0],
                    "INACTIVE",
                    "VISITOR",
                    "/members/{id}/activate",
                    values[1]
            ));
        }
        return arguments.stream();
    }
}
