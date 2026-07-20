package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
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
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Member-owned Coordinator Lifecycle")
class CoordinatorLifecycleApiIT extends MemberApiTestSupport {

    @Test
    @DisplayName("REQ-MEMBER-017 - Coordinator routes require authentication")
    void coordinatorRoutesShouldRejectUnauthenticatedRequests() {
        UUID memberId = UUID.randomUUID();

        for (String transition : List.of("grant", "revoke")) {
            jsonRequest()
                    .body(reasonPayload(VALID_REASON))
                    .patch("/members/{memberId}/coordinator/{transition}", memberId, transition)
                    .then()
                    .statusCode(401)
                    .body("status", equalTo(401));
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-017 - unrelated permissions do not authorize Coordinator lifecycle")
    void unrelatedPermissionShouldReturnForbidden() {
        AuthSession caller = newSessionWithPermissions(
                "MEMBER_MANAGE", "MEMBER_ACTIVATION", "MEMBER_GET", "ACCOUNT_ROLE_MANAGE"
        );

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/grant", UUID.randomUUID())
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-017 - COORDINATOR_MANAGE alone is sufficient for Coordinator lifecycle")
    void coordinatorManageAloneShouldAuthorizeGrant() {
        AuthSession bootstrap = newSession("COORD");
        AuthSession caller = newSessionWithPermissions("COORDINATOR_MANAGE");
        UUID accountId = newAccount("Permission-scoped Coordinator target");
        UUID memberId = registerMember(bootstrap, accountId);
        clearActivities();

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/grant", memberId)
                .then()
                .statusCode(204);

        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrder("MEMBER", "COORD");
        assertThat(activityCount("COORDINATOR_GRANTED")).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-017 and REQ-MEMBER-018 - grant -> COORD, preserved active membership/custom Roles, 204, and one event")
    void coordinatorGrantShouldCommitProjectionAndAuditTogether() {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Coordinator grant target");
        UUID memberId = registerMember(actor, accountId);
        UUID customRoleId = newCustomRole("COORD_GRANT");
        String customRoleName = roleName(customRoleId);
        grantRole(accountId, customRoleName);
        clearActivities();

        authenticatedJsonRequest(actor)
                .header("X-Request-Id", "coordinator-grant-request")
                .header("User-Agent", "coordinator-lifecycle-test")
                .body(reasonPayload("  Leading the next activity  "))
                .patch("/members/{memberId}/coordinator/grant", memberId)
                .then()
                .statusCode(204)
                .body(equalTo(""));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(accountId))
                .contains("MEMBER", "COORD", customRoleName)
                .doesNotContain("VISITOR");
        assertThat(activityCount("COORDINATOR_GRANTED")).isEqualTo(1);
        assertThat(activityCount("ACCOUNT_ROLE_ADDED")).isZero();
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
        Map<String, Object> activity = activity("COORDINATOR_GRANTED");
        assertThat(activity)
                .containsEntry("actor_account_id", actor.accountId())
                .containsEntry("target_id", memberId)
                .containsEntry("reason", "Leading the next activity")
                .containsEntry("request_id", "coordinator-grant-request")
                .containsEntry("user_agent", "coordinator-lifecycle-test");
        assertThat(activity.get("metadata").toString())
                .contains(accountId.toString(), roleId("COORD").toString());
    }

    @Test
    @DisplayName("REQ-MEMBER-017 and REQ-MEMBER-018 - revoke -> active Member without COORD, 204, and one event")
    void coordinatorRevokeShouldCommitProjectionAndAuditTogether() {
        AuthSession sudo = newSession("SUDO");
        UUID accountId = newAccount("Coordinator revoke target");
        UUID memberId = registerMember(sudo, accountId);
        forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        UUID coordAssignmentId = jdbcTemplate.queryForObject(
                "SELECT ar.id FROM account_roles ar JOIN roles r ON r.id = ar.role_id "
                        + "WHERE ar.account_id = ? AND r.name = 'COORD' AND ar.deleted_at IS NULL",
                UUID.class,
                accountId
        );
        clearActivities();

        authenticatedJsonRequest(sudo)
                .body(reasonPayload("  Rotation of responsibility  "))
                .patch("/members/{memberId}/coordinator/revoke", memberId)
                .then()
                .statusCode(204)
                .body(equalTo(""));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(accountId)).contains("MEMBER").doesNotContain("VISITOR", "COORD");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM account_roles WHERE id = ?",
                Boolean.class,
                coordAssignmentId
        )).isTrue();
        assertThat(activityCount("COORDINATOR_REVOKED")).isEqualTo(1);
        assertThat(activityCount("ACCOUNT_ROLE_REMOVED")).isZero();
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
        assertThat(activity("COORDINATOR_REVOKED").get("reason")).isEqualTo("Rotation of responsibility");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCoordinatorReasons")
    @DisplayName("REQ-MEMBER-018 - invalid Coordinator reason -> HTTP 400 before target loading or mutation")
    void invalidCoordinatorReasonShouldReturnBadRequest(String transition, String label, String reason) {
        AuthSession actor = newSession("COORD");

        authenticatedJsonRequest(actor)
                .body(reasonPayload(reason))
                .patch("/members/{memberId}/coordinator/{transition}", UUID.randomUUID(), transition)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedNormalizedCoordinatorReasons")
    @DisplayName("REQ-MEMBER-018 - normalized 2,000-code-point Coordinator reason -> HTTP 204 and normalized audit")
    void normalizedCoordinatorReasonBoundaryShouldBeAccepted(
            String label,
            String transition,
            String submittedReason,
            String normalizedReason
    ) {
        AuthSession sudo = newSession("SUDO");
        UUID accountId = newAccount("Coordinator reason boundary target");
        UUID memberId = registerMember(sudo, accountId);
        if ("revoke".equals(transition)) {
            forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        }
        clearActivities();

        authenticatedJsonRequest(sudo)
                .body(reasonPayload(submittedReason))
                .patch("/members/{memberId}/coordinator/{transition}", memberId, transition)
                .then()
                .statusCode(204);

        String action = "grant".equals(transition) ? "COORDINATOR_GRANTED" : "COORDINATOR_REVOKED";
        assertThat(normalizedReason.codePointCount(0, normalizedReason.length())).isEqualTo(2_000);
        assertThat(activity(action).get("reason")).isEqualTo(normalizedReason);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGrantProjections")
    @DisplayName("REQ-MEMBER-016 and REQ-MEMBER-018 - invalid grant projection -> HTTP 409 without repair")
    void coordinatorGrantShouldRejectInvalidProjection(
            String label,
            String memberStatus,
            String[] roleNames
    ) {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Invalid Coordinator projection");
        UUID memberId = registerMember(actor, accountId);
        forceMemberProjection(memberId, accountId, memberStatus, roleNames);
        clearActivities();

        authenticatedJsonRequest(actor)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/grant", memberId)
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(memberStatus(memberId)).isEqualTo(memberStatus);
        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrder(roleNames);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-018 - revoke without COORD -> HTTP 409 without mutation or audit")
    void coordinatorRevokeShouldRejectActiveNonCoordinator() {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Non-Coordinator revoke target");
        UUID memberId = registerMember(actor, accountId);
        clearActivities();

        authenticatedJsonRequest(actor)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/revoke", memberId)
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(activeRoleNames(accountId)).containsExactly("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-017 - missing or soft-deleted Member -> HTTP 404")
    void unavailableMemberShouldReturnNotFound() {
        AuthSession actor = newSession("COORD");

        authenticatedJsonRequest(actor)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/grant", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        UUID accountId = newAccount("Deleted Coordinator target");
        UUID memberId = registerMember(actor, accountId);
        softDeleteMember(memberId);
        clearActivities();
        authenticatedJsonRequest(actor)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/grant", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(activeRoleNames(accountId)).containsExactly("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coordinatorTransitions")
    @DisplayName("REQ-MEMBER-017 - present Member with soft-deleted linked Account -> HTTP 404 without mutation")
    void unavailableLinkedAccountShouldReturnNotFound(String transition) {
        AuthSession sudo = newSession("SUDO");
        UUID accountId = newAccount("Deleted linked Account Coordinator target");
        UUID memberId = registerMember(sudo, accountId);
        if ("revoke".equals(transition)) {
            forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        }
        var rolesBeforeRequest = activeRoleNames(accountId);
        softDeleteAccount(accountId);
        clearActivities();

        authenticatedJsonRequest(sudo)
                .body(reasonPayload(VALID_REASON))
                .patch("/members/{memberId}/coordinator/{transition}", memberId, transition)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrderElementsOf(rolesBeforeRequest);
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-018 - audit persistence failure rolls back Coordinator grant")
    void failedCoordinatorAuditShouldRollBackRoleProjection() {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Coordinator audit rollback target");
        UUID memberId = registerMember(actor, accountId);
        clearActivities();
        failActivityWritesFor("COORDINATOR_GRANTED");

        try {
            authenticatedJsonRequest(actor)
                    .body(reasonPayload(VALID_REASON))
                    .patch("/members/{memberId}/coordinator/grant", memberId)
                    .then()
                    .statusCode(500)
                    .body("status", equalTo(500));
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(activeRoleNames(accountId)).containsExactly("MEMBER");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-019 - non-SUDO cannot revoke the final current Coordinator")
    void finalCoordinatorRevokeShouldBeForbiddenForNonSudo() {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Final Coordinator target");
        UUID memberId = registerMember(actor, accountId);
        forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        clearActivities();

        authenticatedJsonRequest(actor)
                .body(reasonPayload("Would remove final Coordinator"))
                .patch("/members/{memberId}/coordinator/revoke", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrder("MEMBER", "COORD");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-019 - deleted, inconsistent, and stale Coordinator-like records do not satisfy the final-Coordinator invariant")
    void invalidCoordinatorLikeRecordsShouldNotPreventFinalCoordinatorProtection() {
        AuthSession actor = newSession("COORD");
        UUID targetAccountId = newAccount("Only current Coordinator");
        UUID targetMemberId = registerMember(actor, targetAccountId);
        forceMemberProjection(targetMemberId, targetAccountId, "ACTIVE", "MEMBER", "COORD");

        UUID deletedMemberAccountId = newAccount("Deleted Member Coordinator decoy");
        UUID deletedMemberId = registerMember(actor, deletedMemberAccountId);
        forceMemberProjection(deletedMemberId, deletedMemberAccountId, "ACTIVE", "MEMBER", "COORD");
        softDeleteMember(deletedMemberId);

        UUID deletedAccountId = newAccount("Deleted Account Coordinator decoy");
        UUID deletedAccountMemberId = registerMember(actor, deletedAccountId);
        forceMemberProjection(deletedAccountMemberId, deletedAccountId, "ACTIVE", "MEMBER", "COORD");
        softDeleteAccount(deletedAccountId);

        UUID deletedAssignmentAccountId = newAccount("Deleted assignment Coordinator decoy");
        UUID deletedAssignmentMemberId = registerMember(actor, deletedAssignmentAccountId);
        forceMemberProjection(
                deletedAssignmentMemberId,
                deletedAssignmentAccountId,
                "ACTIVE",
                "MEMBER",
                "COORD"
        );
        softDeleteAccountRole(deletedAssignmentAccountId, "COORD");

        UUID staleRoleAccountId = newAccount("Stale Role Coordinator decoy");
        UUID staleRoleMemberId = registerMember(actor, staleRoleAccountId);
        forceMemberProjection(staleRoleMemberId, staleRoleAccountId, "ACTIVE", "MEMBER");
        UUID staleRoleId = newCustomRole("STALE_COORD");
        String staleRoleName = roleName(staleRoleId);
        jdbcTemplate.update("UPDATE roles SET system_managed = TRUE WHERE id = ?", staleRoleId);
        grantRole(staleRoleAccountId, staleRoleName);
        clearActivities();

        authenticatedJsonRequest(actor)
                .body(reasonPayload("Would remove the only current Coordinator"))
                .patch("/members/{memberId}/coordinator/revoke", targetMemberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeRoleNames(targetAccountId)).containsExactlyInAnyOrder("MEMBER", "COORD");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-MEMBER-019 - concurrent grants serialize to one 204, one 409, one assignment, and one event")
    void concurrentCoordinatorGrantsShouldCommitExactlyOneTransition() throws Exception {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Concurrent Coordinator grant target");
        UUID memberId = registerMember(actor, accountId);
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, actor, memberId, "grant", "First concurrent grant"), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, actor, memberId, "grant", "Second concurrent grant"), executor);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 409);
        }

        assertThat(activeRoleAssignmentCount(accountId, "COORD")).isEqualTo(1);
        assertThat(activityCount("COORDINATOR_GRANTED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-019 - concurrent revokes serialize to one 204, one 409, one removal, and one event")
    void concurrentCoordinatorRevokesShouldCommitExactlyOneTransition() throws Exception {
        AuthSession sudo = newSession("SUDO");
        UUID accountId = newAccount("Concurrent Coordinator revoke target");
        UUID memberId = registerMember(sudo, accountId);
        forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, sudo, memberId, "revoke", "First concurrent revoke"), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, sudo, memberId, "revoke", "Second concurrent revoke"), executor);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 409);
        }

        assertThat(activeRoleAssignmentCount(accountId, "COORD")).isZero();
        assertThat(activityCount("COORDINATOR_REVOKED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MEMBER-019 - concurrent removals of two Coordinators leave one current Coordinator for non-SUDO")
    void concurrentCoordinatorRemovalsShouldSerializeFinalCoordinatorDecision() throws Exception {
        AuthSession actor = newSession("COORD");
        UUID firstAccountId = newAccount("First concurrently removed Coordinator");
        UUID firstMemberId = registerMember(actor, firstAccountId);
        forceMemberProjection(firstMemberId, firstAccountId, "ACTIVE", "MEMBER", "COORD");
        UUID secondAccountId = newAccount("Second concurrently removed Coordinator");
        UUID secondMemberId = registerMember(actor, secondAccountId);
        forceMemberProjection(secondMemberId, secondAccountId, "ACTIVE", "MEMBER", "COORD");
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, actor, firstMemberId, "revoke", "Concurrent removal one"), executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, actor, secondMemberId, "revoke", "Concurrent removal two"), executor);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 403);
        }

        assertThat(
                activeRoleAssignmentCount(firstAccountId, "COORD")
                        + activeRoleAssignmentCount(secondAccountId, "COORD")
        ).isEqualTo(1);
        assertThat(activityCount("COORDINATOR_REVOKED")).isEqualTo(1);
        assertThat(allLifecycleActivityCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coordinatorTransitionsRacingDeactivation")
    @DisplayName("REQ-MEMBER-019 - Coordinator transition racing deactivation serializes to a valid inactive projection")
    void coordinatorTransitionRacingDeactivationShouldSerializeLifecycleDecision(
            String label,
            String transition,
            String[] initialRoles,
            String activityAction
    ) throws Exception {
        AuthSession sudo = newSession("SUDO");
        UUID accountId = newAccount("Mixed Coordinator lifecycle race");
        UUID memberId = registerMember(sudo, accountId);
        forceMemberProjection(memberId, accountId, "ACTIVE", initialRoles);
        clearActivities();
        CountDownLatch start = new CountDownLatch(1);

        int transitionStatus;
        int deactivationStatus;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> coordinatorTransition = CompletableFuture.supplyAsync(
                    () -> transitionAfter(start, sudo, memberId, transition, "Concurrent Coordinator transition"),
                    executor
            );
            CompletableFuture<Integer> deactivation = CompletableFuture.supplyAsync(
                    () -> deactivateAfter(start, sudo, memberId),
                    executor
            );
            start.countDown();
            transitionStatus = coordinatorTransition.get();
            deactivationStatus = deactivation.get();
        }

        assertThat(deactivationStatus).isEqualTo(204);
        assertThat(transitionStatus).isIn(204, 409);
        assertThat(memberStatus(memberId)).isEqualTo("INACTIVE");
        assertThat(activeRoleNames(accountId)).containsExactly("VISITOR");
        assertThat(activityCount("MEMBER_DEACTIVATED")).isEqualTo(1);
        assertThat(activityCount(activityAction)).isEqualTo(transitionStatus == 204 ? 1 : 0);
        assertThat(allLifecycleActivityCount()).isEqualTo(transitionStatus == 204 ? 2 : 1);
    }

    @Test
    @DisplayName("REQ-MEMBER-019 and REQ-MEMBER-020 - non-SUDO cannot deactivate the final current Coordinator")
    void finalCoordinatorDeactivationShouldBeForbiddenForNonSudo() {
        AuthSession actor = newSession("COORD");
        UUID accountId = newAccount("Final Coordinator deactivation target");
        UUID memberId = registerMember(actor, accountId);
        forceMemberProjection(memberId, accountId, "ACTIVE", "MEMBER", "COORD");
        clearActivities();

        authenticatedJsonRequest(actor)
                .body(reasonPayload("Would remove final Coordinator"))
                .patch("/members/{memberId}/deactivate", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(memberStatus(memberId)).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrder("MEMBER", "COORD");
        assertThat(allLifecycleActivityCount()).isZero();
    }

    private int transitionAfter(
            CountDownLatch start,
            AuthSession actor,
            UUID memberId,
            String transition,
            String reason
    ) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return authenticatedJsonRequest(actor)
                .body(reasonPayload(reason))
                .patch("/members/{memberId}/coordinator/{transition}", memberId, transition)
                .then()
                .extract()
                .statusCode();
    }

    private int deactivateAfter(CountDownLatch start, AuthSession actor, UUID memberId) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return authenticatedJsonRequest(actor)
                .body(reasonPayload("Concurrent Member deactivation"))
                .patch("/members/{memberId}/deactivate", memberId)
                .then()
                .extract()
                .statusCode();
    }

    private String roleName(UUID roleId) {
        return jdbcTemplate.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, roleId);
    }

    private static Stream<Arguments> invalidCoordinatorReasons() {
        List<Arguments> arguments = new java.util.ArrayList<>();
        for (String transition : List.of("grant", "revoke")) {
            arguments.add(Arguments.of(transition, "EP - null", null));
            arguments.add(Arguments.of(transition, "EP - empty", ""));
            arguments.add(Arguments.of(transition, "EP - whitespace", " \n\t "));
            arguments.add(Arguments.of(transition, "BVA - 2,001 characters", "r".repeat(2_001)));
        }
        return arguments.stream();
    }

    private static Stream<Arguments> acceptedNormalizedCoordinatorReasons() {
        String maximumAsciiReason = "r".repeat(2_000);
        String maximumSupplementaryReason = "😀".repeat(2_000);
        return Stream.of(
                Arguments.of(
                        "grant trims before applying the maximum",
                        "grant",
                        "  " + maximumAsciiReason + "  ",
                        maximumAsciiReason
                ),
                Arguments.of(
                        "revoke counts supplementary Unicode code points",
                        "revoke",
                        maximumSupplementaryReason,
                        maximumSupplementaryReason
                )
        );
    }

    private static Stream<String> coordinatorTransitions() {
        return Stream.of("grant", "revoke");
    }

    private static Stream<Arguments> invalidGrantProjections() {
        return Stream.of(
                Arguments.of("inactive Member", "INACTIVE", new String[]{"VISITOR"}),
                Arguments.of("active Member missing MEMBER", "ACTIVE", new String[]{}),
                Arguments.of("active Member also has VISITOR", "ACTIVE", new String[]{"MEMBER", "VISITOR"}),
                Arguments.of("Coordinator already granted", "ACTIVE", new String[]{"MEMBER", "COORD"})
        );
    }

    private static Stream<Arguments> coordinatorTransitionsRacingDeactivation() {
        return Stream.of(
                Arguments.of(
                        "grant racing deactivation",
                        "grant",
                        new String[]{"MEMBER"},
                        "COORDINATOR_GRANTED"
                ),
                Arguments.of(
                        "revoke racing deactivation",
                        "revoke",
                        new String[]{"MEMBER", "COORD"},
                        "COORDINATOR_REVOKED"
                )
        );
    }
}
