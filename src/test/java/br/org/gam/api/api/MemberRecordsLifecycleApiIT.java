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
    @DisplayName("REQ-MEMBER-016 - valid direct registration -> active non-Coordinator Member, preserved custom Role, and one audit event")
    void validDirectRegistrationShouldCommitTheCompleteMemberWorkflow() {
        AuthSession coordinator = newSession("COORD");
        String targetEmail = "direct-member-" + UUID.randomUUID() + "@example.com";
        String targetDisplayName = "Direct Member Target";
        UUID targetId = newAccount(targetEmail, targetDisplayName);
        UUID customRoleId = newCustomRole("DIRECT_REGISTRATION");
        grantRole(targetId, jdbcTemplate.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, customRoleId));
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
                .contains("MEMBER", jdbcTemplate.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, customRoleId))
                .doesNotContain("VISITOR", "COORD");
        assertThat(activityCount("MEMBER_REGISTERED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);

        Map<String, Object> activity = activity("MEMBER_REGISTERED");
        assertThat(activity)
                .containsEntry("actor_kind", "ACCOUNT")
                .containsEntry("actor_account_id", coordinator.accountId())
                .containsEntry("actor_reference", null)
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", memberId)
                .containsEntry("target_scope", null)
                .containsEntry("reason", "Accepted as a GAM Member");
        assertThat(activity.get("metadata").toString())
                .contains(targetId.toString(), roleId("MEMBER").toString())
                .doesNotContain(
                        memberId.toString(),
                        roleId("VISITOR").toString(),
                        roleId("COORD").toString()
                );
        assertThat(((UUID) activity.get("request_id")).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002 - direct registration normalizes Unicode city whitespace and NFC")
    void directRegistrationShouldNormalizeUnicodeCityThroughThePublicSeam() {
        AuthSession coordinator = newSession("COORD");
        UUID accountId = newAccount("Synthetic Unicode registration target");
        Map<String, Object> payload = new java.util.HashMap<>(
                memberPayload(accountId, LocalDate.now().minusYears(20), VALID_REASON));
        payload.put("residentialCity", "\u00a0Sa\u0303o\u2003\u2003Jose\u0301\u00a0");

        ExtractableResponse<Response> created = authenticatedJsonRequest(coordinator)
                .body(payload).post("/members").then().statusCode(201).extract();
        UUID memberId = UUID.fromString(created.path("id"));

        assertThat((String) created.path("residentialCity")).isEqualTo("São José");
        assertThat(authenticatedJsonRequest(coordinator).get("/members/{id}", memberId)
                .then().statusCode(200).extract().<String>path("residentialCity"))
                .isEqualTo("São José");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002/004 - direct registration city -> Unicode-only blank and 100-code-point bounds")
    void directRegistrationCityShouldUseNotBlankAndCodePointBounds() {
        AuthSession coordinator = newSession("COORD");

        UUID blankAccount = newAccount("Synthetic blank registration city target");
        Map<String, Object> blankPayload = new java.util.HashMap<>(
                memberPayload(blankAccount, LocalDate.now().minusYears(20), VALID_REASON));
        blankPayload.put("residentialCity", "\u0085");
        ExtractableResponse<Response> blankResponse = authenticatedJsonRequest(coordinator)
                .body(blankPayload).post("/members").then().extract();
        assertThat(blankResponse.statusCode()).as(blankResponse.asString()).isEqualTo(400);
        assertThat(blankResponse.<String>path("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(blankResponse.<List<Map<String, Object>>>path("details.violations"))
                .singleElement()
                .satisfies(violation -> assertThat(violation)
                        .containsEntry("field", "/residentialCity")
                        .containsEntry("code", "NOT_BLANK"));
        assertThat(memberCount(blankAccount)).isZero();

        UUID validAccount = newAccount("Synthetic 100-point registration city target");
        Map<String, Object> validPayload = new java.util.HashMap<>(
                memberPayload(validAccount, LocalDate.now().minusYears(20), VALID_REASON));
        validPayload.put("residentialCity", "A".repeat(100));
        authenticatedJsonRequest(coordinator).body(validPayload).post("/members")
                .then().statusCode(201).body("residentialCity", equalTo("A".repeat(100)));

        UUID overflowAccount = newAccount("Synthetic overflow registration city target");
        Map<String, Object> overflowPayload = new java.util.HashMap<>(validPayload);
        overflowPayload.put("accountId", overflowAccount.toString());
        overflowPayload.put("contactEmail", "overflow-city-" + overflowAccount + "@example.com");
        overflowPayload.put("residentialCity", "A".repeat(101));
        ExtractableResponse<Response> overflowResponse = authenticatedJsonRequest(coordinator)
                .body(overflowPayload).post("/members").then().extract();
        assertThat(overflowResponse.statusCode()).as(overflowResponse.asString()).isEqualTo(400);
        assertThat(overflowResponse.<String>path("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(overflowResponse.<List<Map<String, Object>>>path("details.violations"))
                .singleElement()
                .satisfies(violation -> assertThat(violation)
                        .containsEntry("field", "/residentialCity")
                        .containsEntry("code", "SIZE"));
        assertThat(memberCount(overflowAccount)).isZero();
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
    @DisplayName("REQ-MEMBER-006, REQ-MEMBER-011, and REQ-API-ERROR-002/003/004 - invalid direct-registration reason -> public validation violation without mutation")
    void invalidDirectRegistrationReasonShouldReturnBadRequest(
            String label,
            String reason,
            String expectedViolationCode
    ) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Invalid registration reason target");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, LocalDate.now().minusYears(20), reason))
                .post("/members")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        assertThat(response.<String>path("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(response.<Map<String, Object>>path("details")).containsOnlyKeys("violations");
        assertThat(response.<List<Map<String, Object>>>path("details.violations"))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation)
                            .containsOnlyKeys("location", "field", "code", "message")
                            .containsEntry("location", "body")
                            .containsEntry("field", "/reason")
                            .containsEntry("code", expectedViolationCode);
                    assertThat(violation.get("message")).isInstanceOf(String.class);
                    assertThat(String.valueOf(violation.get("message"))).isNotBlank();
                });
        assertThat(response.asString()).doesNotContain("IllegalArgumentException", "INVALID_REQUEST");

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
        AuthSession applicant = newSession(null);
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
        AuthSession applicant = newSession(null);
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

    @ParameterizedTest
    @MethodSource("preMemberLifecycleRoles")
    @DisplayName("REQ-MEMBER-016 - direct registration with a pre-Member lifecycle Role -> HTTP 409 without repair")
    void directRegistrationShouldRejectInconsistentPreMemberProjection(String roleName) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Inconsistent registration target");
        grantRole(targetId, roleName);
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(memberPayload(targetId, LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(memberCount(targetId)).isZero();
        assertThat(activeRoleNames(targetId)).containsExactly(roleName);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-016 and REQ-MEMBER-020 - Coordinator deactivation -> INACTIVE, VISITOR, no COORD, and one event")
    void deactivationShouldCommitStatusRolesAndAuditTogether() {
        AuthSession coordinator = newSession("SUDO");
        UUID targetId = newAccount("Deactivation target");
        UUID memberId = registerMember(coordinator, targetId);
        UUID customRoleId = newCustomRole("DEACTIVATION");
        String customRoleName = jdbcTemplate.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, customRoleId);
        grantRole(targetId, customRoleName);
        forceMemberProjection(memberId, targetId, "ACTIVE", "MEMBER", "COORD");
        clearActivities();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("  Pausing weekly activities  "))
                .patch("/members/{id}/deactivate", memberId)
                .then()
                .statusCode(204);

        assertThat(memberStatus(memberId)).isEqualTo("INACTIVE");
        assertThat(activeRoleNames(targetId))
                .contains("VISITOR", customRoleName)
                .doesNotContain("MEMBER", "COORD");
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
                        roleId("VISITOR").toString(),
                        roleId("COORD").toString()
                );
    }

    @Test
    @DisplayName("REQ-MEMBER-016 and REQ-MEMBER-020 - reactivation -> ACTIVE, MEMBER, preserved custom Role, and COORD remains absent")
    void reactivationShouldCommitStatusRolesAndAuditTogether() {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Reactivation target");
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
                .contains("MEMBER")
                .doesNotContain("VISITOR", "COORD");
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
            String reason,
            String expectedViolationCode
    ) {
        AuthSession coordinator = newSession("COORD");
        UUID targetId = newAccount("Invalid lifecycle reason target");
        UUID memberId = registerMember(coordinator, targetId);
        forceMemberState(memberId, targetId, initialStatus, initialRole);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch(route, memberId)
                .then()
                .extract();

        assertStructuredValidationError(response, "/reason", expectedViolationCode);

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
    @DisplayName("REQ-MEMBER-010 and REQ-MEMBER-INFO-012 - every documented public filter and comparison -> finds the target Member")
    void documentedMemberSearchFiltersShouldFindTheTarget() {
        AuthSession coordinator = newSession("COORD");
        String targetEmail = "member-search-" + UUID.randomUUID() + "@example.com";
        UUID targetAccountId = newAccount(targetEmail, "Member Search Target");
        UUID targetMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(targetMemberId, targetAccountId, "ACTIVE", "MEMBER");
        jdbcTemplate.update(
                "INSERT INTO member_contribution_areas (member_id, contribution_area) "
                        + "VALUES (?, CAST('FOOTBALL' AS member_contribution_area_enum))",
                targetMemberId
        );
        String contactEmail = "member-contact-" + targetAccountId + "@example.com";

        List<Map<String, Object>> filters = List.of(
                filter("id", targetMemberId.toString(), "EQUALS"),
                filter("id", List.of(targetMemberId.toString()), "IN"),
                filter("name", "Silva", "LIKE"),
                filter("birthDate", LocalDate.now().minusYears(20).toString(), "EQUALS"),
                filter("birthDate", "1900-01-01", "GREATER_THAN_OR_EQUAL"),
                filter("birthDate", LocalDate.now().toString(), "LESS_THAN_OR_EQUAL"),
                filter("gamEntryDate", "2020-01-01", "EQUALS"),
                filter("gamEntryDate", "1900-01-01", "GREATER_THAN_OR_EQUAL"),
                filter("gamEntryDate", LocalDate.now().toString(), "LESS_THAN_OR_EQUAL"),
                filter("residentialCity", "Synthetic City", "EQUALS"),
                filter("residentialCity", "Synthetic", "LIKE"),
                filter("phoneNumber", CANONICAL_PHONE, "EQUALS"),
                filter("phoneNumber", "99887", "LIKE"),
                filter("contactEmail", contactEmail, "EQUALS"),
                filter("contactEmail", "member-contact-", "LIKE"),
                filter("status", "ACTIVE", "EQUALS"),
                filter("status", List.of("ACTIVE", "INACTIVE"), "IN"),
                filter("accountId", targetAccountId.toString(), "EQUALS"),
                filter("accountEmail", targetEmail, "EQUALS"),
                filter("accountEmail", targetEmail.substring(0, targetEmail.indexOf('@')), "LIKE"),
                filter("hasLinkedAccount", true, "EQUALS"),
                filter("role", "MEMBER", "EQUALS"),
                filter("role", List.of("MEMBER", "COORD"), "IN"),
                filter("jornadaMissionaria", "NOT_INFORMED", "EQUALS"),
                filter("cursoDeLideranca", List.of("NOT_INFORMED"), "IN"),
                filter("pascoaJuvenil", "NOT_INFORMED", "EQUALS"),
                filter("acampabosco", List.of("NOT_INFORMED"), "IN"),
                filter("batismo", "NOT_INFORMED", "EQUALS"),
                filter("primeiraComunhao", List.of("NOT_INFORMED"), "IN"),
                filter("crisma", "NOT_INFORMED", "EQUALS"),
                filter("contributionArea", "FOOTBALL", "EQUALS"),
                filter("contributionArea", List.of("FOOTBALL", "TERERE"), "IN"),
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
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparisonMethod"))
                    .isEqualTo(200);
            assertThat(resourceIds(response.jsonPath().getList("items")))
                    .as("public filter %s %s", searchFilter.get("field"), searchFilter.get("comparisonMethod"))
                    .contains(targetMemberId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-010 - phone LIKE with narrow no-break space -> finds canonical digit sequence")
    void memberPhoneSearchShouldAcceptNarrowNoBreakSpaceFormatting() {
        AuthSession coordinator = newSession("COORD");
        UUID targetAccountId = newAccount(
                "member-phone-nb-space-" + UUID.randomUUID() + "@example.com",
                "Member Phone Non-Breaking Space Target"
        );
        UUID targetMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(targetMemberId, targetAccountId, "ACTIVE", "MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("phoneNumber", "19\u202F9988", "LIKE")))
                .post("/members/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.jsonPath().getList("items"))).contains(targetMemberId);
    }

    @Test
    @DisplayName("REQ-MEMBER-010 and REQ-SEARCH-007 - name LIKE trims and collapses Unicode whitespace")
    void memberNameSearchShouldNormalizeUnicodeWhitespace() {
        AuthSession coordinator = newSession("COORD");
        UUID targetAccountId = newAccount(
                "member-unicode-space-" + UUID.randomUUID() + "@example.com",
                "Member Unicode Space Target"
        );
        UUID targetMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(targetMemberId, targetAccountId, "ACTIVE", "MEMBER");
        List<String> submittedNames = List.of(
                "\u2003Ana\u2003\u2003Silva\u2003",
                "\u2002Ana\u2003\u205FSilva\u2002"
        );

        for (String submittedName : submittedNames) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                    .body(searchPayload(filter("name", submittedName, "LIKE")))
                    .post("/members/search?size=100")
                    .then()
                    .extract();

            assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
            assertThat(resourceIds(response.jsonPath().getList("items"))).containsExactly(targetMemberId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBreakingWhitespaceNameCases")
    @DisplayName("REQ-MEMBER-010 and REQ-SEARCH-007 - name LIKE collapses non-breaking Unicode whitespace")
    void memberNameSearchShouldNormalizeNonBreakingUnicodeWhitespace(String scenario, String whitespace) {
        AuthSession coordinator = newSession("COORD");
        UUID targetAccountId = newAccount(
                "member-nb-space-" + UUID.randomUUID() + "@example.com",
                "Member Non-Breaking Space Target"
        );
        UUID targetMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(targetMemberId, targetAccountId, "ACTIVE", "MEMBER");
        String submittedName = whitespace + "Ana" + whitespace + whitespace + "Silva" + whitespace;

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("name", submittedName, "LIKE")))
                .post("/members/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(200);
        assertThat(resourceIds(response.jsonPath().getList("items"))).containsExactly(targetMemberId);
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
                Arguments.of("BVA - 2,000 characters", "  " + "r".repeat(2_000) + "  ", "r".repeat(2_000)),
                Arguments.of(
                        "BVA - 2,000 code points surrounded by Unicode whitespace",
                        "\u00A0" + "\ud83c\udf06".repeat(2_000) + "\u3000",
                        "\ud83c\udf06".repeat(2_000)
                )
        );
    }

    private static Stream<Arguments> nonBreakingWhitespaceNameCases() {
        return Stream.of(
                Arguments.of("NO-BREAK SPACE U+00A0", "\u00A0"),
                Arguments.of("FIGURE SPACE U+2007", "\u2007"),
                Arguments.of("NARROW NO-BREAK SPACE U+202F", "\u202F")
        );
    }

    private static Stream<String> preMemberLifecycleRoles() {
        return Stream.of("MEMBER", "VISITOR", "COORD");
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
                Arguments.of("EP - null", null, "REQUIRED"),
                Arguments.of("EP - empty", "", "NOT_BLANK"),
                Arguments.of("EP - whitespace", " \n\t ", "NOT_BLANK"),
                Arguments.of("EP - Unicode whitespace only", "\u00A0\u2003\u3000", "NOT_BLANK"),
                Arguments.of("EP - NEXT LINE Unicode whitespace only", "\u0085", "NOT_BLANK"),
                Arguments.of(
                        "BVA - normalized reason over 2,000 code points",
                        "\u00A0" + "r".repeat(2_001) + "\u3000",
                        "SIZE"
                ),
                Arguments.of("BVA - 2,001 characters", "r".repeat(2_001), "SIZE")
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
                    values[1],
                    values[2]
            ));
            arguments.add(Arguments.of(
                    "activate",
                    values[0],
                    "INACTIVE",
                    "VISITOR",
                    "/members/{id}/activate",
                    values[1],
                    values[2]
            ));
        }
        return arguments.stream();
    }
}
