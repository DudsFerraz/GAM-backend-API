package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Oratorio Coordinator designation")
class OratorioCoordinatorDesignationApiIT extends OratorioModuleApiTestSupport {

    private static final String GRANT = "/members/{memberId}/oratorio-coordinator/grant";
    private static final String REVOKE = "/members/{memberId}/oratorio-coordinator/revoke";
    private static final String VALID_REASON = "  Responsible for Saturday Oratorio operations  ";

    @Test
    @DisplayName("REQ-ORATORIO-COORD-001 through REQ-ORATORIO-COORD-003 - grant and revoke preserve active Member identity")
    void grantAndRevokeShouldPreserveActiveMemberIdentityAndAuditEachTransition() {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);
        clearActivities();

        ExtractableResponse<Response> grant = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .extract();

        assertThat(grant.statusCode()).isEqualTo(204);
        assertThat(memberStatus(target.memberId())).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(target.accountId()))
                .contains("MEMBER", "ORATORIO_COORD")
                .doesNotContain("VISITOR");
        assertThat(activityCountForActionAndTarget(
                "ORATORIO_COORDINATOR_GRANTED", target.memberId()
        )).isEqualTo(1);
        assertDesignationActivity("ORATORIO_COORDINATOR_GRANTED", target, "Responsible for Saturday Oratorio operations");
        assertThat(activityCount("ACCOUNT_ROLE_ADDED")).isZero();

        ExtractableResponse<Response> revoke = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Rotating the responsibility  "))
                .patch(REVOKE, target.memberId())
                .then()
                .extract();

        assertThat(revoke.statusCode()).isEqualTo(204);
        assertThat(memberStatus(target.memberId())).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(target.accountId()))
                .contains("MEMBER")
                .doesNotContain("ORATORIO_COORD", "VISITOR");
        assertThat(activityCountForActionAndTarget(
                "ORATORIO_COORDINATOR_REVOKED", target.memberId()
        )).isEqualTo(1);
        assertDesignationActivity("ORATORIO_COORDINATOR_REVOKED", target, "Rotating the responsibility");
        assertThat(activityCount("ACCOUNT_ROLE_REMOVED")).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-002 - ORATORIO_COORD lacks designation-management authority")
    void oratorioCoordinatorShouldNotGrantDesignation() {
        AuthSession setup = sudoSession();
        TargetMember target = createTargetMember(setup);
        AuthSession oratorioCoordinator = newSession("ORATORIO_COORD");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(oratorioCoordinator)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(activeRoleNames(target.accountId())).doesNotContain("ORATORIO_COORD");
        assertThat(activityCountForTarget(target.memberId())).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-002 - unauthenticated designation mutation -> HTTP 401")
    void unauthenticatedDesignationMutationShouldBeUnauthorized() {
        assertThat(jsonRequest()
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, UUID.randomUUID())
                .statusCode()).isEqualTo(401);
        assertThat(jsonRequest()
                .body(reasonPayload(VALID_REASON))
                .patch(REVOKE, UUID.randomUUID())
                .statusCode()).isEqualTo(401);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReasonCases")
    @DisplayName("REQ-ORATORIO-COORD-003 - invalid bounded reason -> HTTP 400 before mutation")
    void invalidBoundedReasonShouldBeRejected(String scenario, Map<String, Object> body) {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body)
                .patch(GRANT, target.memberId())
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(activeRoleNames(target.accountId())).containsExactlyInAnyOrder("MEMBER");
        assertThat(activityCountForTarget(target.memberId())).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-002 - repeated and inconsistent transitions fail without repair")
    void repeatedAndInconsistentTransitionsShouldFailWithoutRepair() {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> repeatedGrant = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .extract();
        assertThat(repeatedGrant.statusCode()).isEqualTo(409);
        assertThat(activityCountForTarget(target.memberId())).isZero();

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(REVOKE, target.memberId())
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> repeatedRevoke = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(REVOKE, target.memberId())
                .then()
                .extract();
        assertThat(repeatedRevoke.statusCode()).isEqualTo(409);
        assertThat(activityCountForTarget(target.memberId())).isZero();

        forceMemberProjection(target.memberId(), target.accountId(), "ACTIVE");
        ExtractableResponse<Response> inconsistentGrant = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .extract();
        assertThat(inconsistentGrant.statusCode()).isEqualTo(409);
        assertThat(activeRoleNames(target.accountId())).isEmpty();
        assertThat(activityCountForTarget(target.memberId())).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-004 - Member deactivation removes designation through one high-level activity")
    void deactivationShouldRemoveDesignationWithoutSeparateRevocationActivity() {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Pausing Member participation  "))
                .patch("/members/{memberId}/deactivate", target.memberId())
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(memberStatus(target.memberId())).isEqualTo("INACTIVE");
        assertThat(activeRoleNames(target.accountId()))
                .containsExactlyInAnyOrder("VISITOR")
                .doesNotContain("MEMBER", "ORATORIO_COORD");
        assertThat(activityCountForActionAndTarget("MEMBER_DEACTIVATED", target.memberId())).isEqualTo(1);
        assertThat(activityCountForActionAndTarget(
                "ORATORIO_COORDINATOR_REVOKED", target.memberId()
        )).isZero();
        assertThat(activityCount("ACCOUNT_ROLE_REMOVED")).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-004 - reactivation does not restore former designation")
    void reactivationShouldNotRestoreFormerDesignation() {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, target.memberId())
                .then()
                .statusCode(204);
        authenticatedJsonRequest(caller)
                .body(reasonPayload("Temporarily inactive"))
                .patch("/members/{memberId}/deactivate", target.memberId())
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Returning to activities  "))
                .patch("/members/{memberId}/activate", target.memberId())
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(memberStatus(target.memberId())).isEqualTo("ACTIVE");
        assertThat(activeRoleNames(target.accountId()))
                .containsExactlyInAnyOrder("MEMBER")
                .doesNotContain("VISITOR", "ORATORIO_COORD");
        assertThat(activityCountForActionAndTarget("MEMBER_ACTIVATED", target.memberId())).isEqualTo(1);
        assertThat(activityCountForActionAndTarget(
                "ORATORIO_COORDINATOR_GRANTED", target.memberId()
        )).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-005 and ADR-0017 - concurrent duplicate grants have one winner and one activity")
    void concurrentDuplicateGrantsShouldHaveOneWinnerAndOneActivity() throws Exception {
        AuthSession caller = sudoSession();
        TargetMember target = createTargetMember(caller);
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> first = executor.submit(
                    () -> concurrentGrant(caller, target.memberId(), ready, start)
            );
            Future<ExtractableResponse<Response>> second = executor.submit(
                    () -> concurrentGrant(caller, target.memberId(), ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode()
            );
            assertThat(statuses).containsExactlyInAnyOrder(204, 409);
            assertThat(activeRoleAssignmentCount(target.accountId(), "ORATORIO_COORD")).isEqualTo(1);
            assertThat(activityCountForActionAndTarget(
                    "ORATORIO_COORDINATOR_GRANTED", target.memberId()
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private TargetMember createTargetMember(AuthSession caller) {
        UUID accountId = newAccount("Oratorio designation target");
        UUID memberId = registerMember(caller, accountId);
        return new TargetMember(accountId, memberId);
    }

    private void assertDesignationActivity(String action, TargetMember target, String reason) {
        Map<String, Object> activity = jdbcTemplate.queryForMap(
                "SELECT target_type, target_id, actor_account_id, reason, metadata "
                        + "FROM activity_logs WHERE action = ? AND target_id = ?",
                action,
                target.memberId()
        );
        assertThat(activity)
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", target.memberId())
                .containsEntry("reason", reason);
        assertThat(activity.get("metadata").toString())
                .contains(target.accountId().toString(), "ORATORIO_COORD")
                .doesNotContain("firstName", "surname", "phoneNumber");
    }

    private ExtractableResponse<Response> concurrentGrant(
            AuthSession caller,
            UUID memberId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(GRANT, memberId)
                .then()
                .extract();
    }

    private static Stream<Arguments> invalidReasonCases() {
        Map<String, Object> nullReason = new HashMap<>();
        nullReason.put("reason", null);
        return Stream.of(
                Arguments.of("missing reason", Map.of()),
                Arguments.of("null reason", nullReason),
                Arguments.of("blank reason", Map.of("reason", "   ")),
                Arguments.of("reason above 2,000 characters", Map.of("reason", "x".repeat(2_001)))
        );
    }

    private record TargetMember(UUID accountId, UUID memberId) {
    }
}
