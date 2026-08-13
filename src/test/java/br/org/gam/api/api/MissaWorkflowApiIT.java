package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Missa workflow and liturgical assignments")
class MissaWorkflowApiIT extends MemberApiTestSupport {

    private static final String MISSAS = "/missas";
    private static final String VALID_REASON = "  Correcting the Missa plan  ";
    private static final List<String> RESPONSIBILITIES = List.of(
            "COMENTARIOS",
            "PRIMEIRA_LEITURA",
            "SALMO",
            "SEGUNDA_LEITURA",
            "PRECES",
            "ACOLHIDA",
            "BANDA"
    );
    private static final Set<String> EVENT_FIELDS = Set.of(
            "id", "title", "description", "gamLocation", "requiredPermission",
            "beginDate", "endDate", "type", "status", "cancellationReason"
    );

    private final List<UUID> missaIds = new ArrayList<>();
    private final List<UUID> accountlessMemberIds = new ArrayList<>();

    @AfterEach
    void cleanupMissaFixtures() {
        for (UUID missaId : missaIds) {
            jdbcTemplate.update("DELETE FROM presences WHERE event_id = ?", missaId);
            deleteMissaAssignmentsIfPresent("missa_assignments", "missa_id", missaId);
            deleteMissaAssignmentsIfPresent("missa_acolhida_members", "missa_id", missaId);
            deleteMissaAssignmentsIfPresent("missa_banda_members", "missa_id", missaId);
            jdbcTemplate.update("DELETE FROM missas WHERE id = ?", missaId);
        }
        for (UUID memberId : accountlessMemberIds) {
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
        }
        missaIds.clear();
        accountlessMemberIds.clear();
    }

    @Test
    @DisplayName("REQ-MISSA-001/002/004/011/017 - normalized creation -> shared identity, seven empty responsibilities, and one Missa activity")
    void normalizedCreationShouldReturnCompleteEmptyMissaAndOneHighLevelActivity() {
        AuthSession caller = newSession("SUDO");
        UUID locationId = createLocation(caller, "Missa creation");
        clearActivities();

        Map<String, Object> payload = missaPayload(
                "  Missa da Comunidade  ",
                locationId,
                null,
                Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(7_200)
        );
        payload.put("description", null);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(MISSAS)
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(201);
        UUID missaId = trackCreatedMissa(response);
        assertPublicApiLocation(response, MISSAS + "/" + missaId);
        assertUuidV7(missaId);
        assertThat(response.<String>path("id")).isEqualTo(missaId.toString());
        assertThat(response.<String>path("event.id")).isEqualTo(missaId.toString());
        assertThat(response.<Map<String, Object>>path("event"))
                .containsOnlyKeys(EVENT_FIELDS);
        assertThat(response.<String>path("event.title")).isEqualTo("Missa da Comunidade");
        assertThat(response.<String>path("event.description")).isEmpty();
        assertThat(response.<String>path("event.type")).isEqualTo("MISSA");
        assertThat(response.<String>path("event.status")).isEqualTo("SCHEDULED");
        assertThat(response.<Object>path("event.requiredPermission")).isNull();
        assertEmptyResponsibilityCatalog(response);

        assertThat(activityCountFor("MISSA_CREATED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("EVENT_CREATED", missaId)).isZero();
        Map<String, Object> activity = missaActivity("MISSA_CREATED", missaId);
        assertThat(activity)
                .containsEntry("actor_kind", "ACCOUNT")
                .containsEntry("actor_account_id", caller.accountId())
                .containsEntry("target_type", "MISSA")
                .containsEntry("target_id", missaId)
                .containsEntry("target_scope", null)
                .containsEntry("reason", null);
        assertThat(JsonPath.from(activity.get("metadata").toString()).getMap("$"))
                .containsOnlyKeys("type", "status", "gamLocationId", "requiredPermissionId")
                .containsEntry("type", "MISSA")
                .containsEntry("status", "SCHEDULED")
                .containsEntry("gamLocationId", locationId.toString())
                .containsEntry("requiredPermissionId", null);
    }

    @Test
    @DisplayName("REQ-MISSA-002 - creation payload containing assignments -> HTTP 400 without Event, Missa, Presence, or activity")
    void creationShouldRejectAssignmentsInsteadOfIgnoringThem() {
        AuthSession caller = newSession("SUDO");
        UUID locationId = createLocation(caller, "Rejected Missa creation");
        UUID memberId = createAccountlessMember("Ana", "Silva", "ACTIVE");
        Map<String, Object> payload = missaPayload(
                "Missa sem plano inicial",
                locationId,
                null,
                Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(7_200)
        );
        payload.put("assignments", List.of(Map.of(
                "responsibility", "COMENTARIOS",
                "memberId", memberId.toString()
        )));
        long eventCountBefore = activeEventCount();
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(MISSAS)
                .then()
                .extract();

        trackUnexpectedlyCreatedMissa(response);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        assertThat(activeEventCount()).isEqualTo(eventCountBefore);
        assertThat(activityCount("MISSA_CREATED")).isZero();
        assertThat(activityCount("EVENT_CREATED")).isZero();
    }

    @Test
    @DisplayName("REQ-MISSA-003/010/012 - specialized permissions and exact audience visibility govern reads and mutations")
    void specializedPermissionsAndAudienceVisibilityShouldGovernMissaAccess() {
        AuthSession creator = newSession("SUDO");
        UUID locationId = createLocation(creator, "Restricted Missa");
        UUID audiencePermissionId = permissionId("EVENT_GET_COORD");
        UUID missaId = createMissa(
                creator,
                "Restricted Missa",
                locationId,
                audiencePermissionId,
                Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(7_200)
        );
        AuthSession onlyMissaGet = newSessionWithPermissions("MISSA_GET");
        AuthSession onlyAudience = newSessionWithPermissions("EVENT_GET_COORD");
        AuthSession visibleReader = newSessionWithPermissions("MISSA_GET", "EVENT_GET_COORD");
        UUID memberId = createAccountlessMember("Bruna", "Souza", "ACTIVE");

        assertThat(jsonRequest().get(MISSAS + "/" + missaId).statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(onlyAudience)
                .get(MISSAS + "/" + missaId).statusCode()).isEqualTo(403);

        ExtractableResponse<Response> hidden = authenticatedJsonRequest(onlyMissaGet)
                .get(MISSAS + "/" + missaId)
                .then()
                .extract();
        assertThat(hidden.statusCode()).isEqualTo(404);
        assertThat(hidden.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(authenticatedJsonRequest(visibleReader)
                .get(MISSAS + "/" + missaId).statusCode()).isEqualTo(200);

        ExtractableResponse<Response> unauthorizedMutation = authenticatedJsonRequest(visibleReader)
                .put(assignmentPath(missaId, "PRECES", memberId))
                .then()
                .extract();
        assertThat(unauthorizedMutation.statusCode()).isEqualTo(403);
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-MISSA-012 and REQ-RBAC-002/003 - registry and baseline bundles expose the accepted Missa permissions")
    void rbacRegistryAndBaselineBundlesShouldContainMissaPermissions() {
        AuthSession caller = newSession("SUDO");

        assertPermissionMetadata(caller, "MISSA_GET", "View Missas",
                "Allows viewing specialized Missa details");
        assertPermissionMetadata(caller, "MISSA_CREATE", "Create Missas",
                "Allows creating Missas");
        assertPermissionMetadata(caller, "MISSA_MANAGE", "Manage Missas",
                "Allows managing Missa details, assignments, lifecycle, and deletion");

        assertThat(rolePermissionCodes(caller, "MEMBER")).contains("MISSA_GET")
                .doesNotContain("MISSA_CREATE", "MISSA_MANAGE");
        assertThat(rolePermissionCodes(caller, "COORD"))
                .contains("MISSA_GET", "MISSA_CREATE", "MISSA_MANAGE");
        assertThat(rolePermissionCodes(caller, "ORATORIO_COORD"))
                .doesNotContain("MISSA_GET", "MISSA_CREATE", "MISSA_MANAGE");
        assertThat(rolePermissionCodes(caller, "VISITOR"))
                .doesNotContain("MISSA_GET", "MISSA_CREATE", "MISSA_MANAGE");
        assertThat(rolePermissionCodes(caller, "SUDO"))
                .contains("MISSA_GET", "MISSA_CREATE", "MISSA_MANAGE");
    }

    @Test
    @DisplayName("REQ-MISSA-005/006/007/011/017 - active Account-less Member assignment -> Presence plus one high-level activity")
    void assignmentShouldAcceptAccountlessMemberAndAtomicallyCreatePresence() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Account-less assignment");
        UUID memberId = createAccountlessMember("Ana", "Silva", "ACTIVE");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "PRIMEIRA_LEITURA", memberId))
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        assertAssignedMember(response, "PRIMEIRA_LEITURA", memberId, "Ana", "Silva", "ACTIVE");
        UUID presenceId = activePresenceId(missaId, memberId);
        assertThat(presenceId).isNotNull();
        assertThat(activePresenceObservations(missaId, memberId)).isNull();
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("PRESENCE_REGISTERED", presenceId)).isZero();
        Map<String, Object> metadata = activityMetadata("MISSA_MEMBER_ASSIGNED", missaId);
        assertThat(metadata)
                .containsOnlyKeys("responsibility", "memberId", "presenceId", "presenceCreated")
                .containsEntry("responsibility", "PRIMEIRA_LEITURA")
                .containsEntry("memberId", memberId.toString())
                .containsEntry("presenceId", presenceId.toString())
                .containsEntry("presenceCreated", true);
    }

    @Test
    @DisplayName("REQ-MISSA-006/007 - existing Presence is reused and exact retry is an unaudited no-op")
    void assignmentShouldReusePresenceAndTreatExactRetryAsNoOp() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Presence reuse");
        UUID memberId = createAccountlessMember("Caio", "Mendes", "ACTIVE");
        UUID presenceId = registerPresence(caller, missaId, memberId, "Already confirmed");
        clearActivities();

        ExtractableResponse<Response> first = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "PRECES", memberId))
                .then()
                .extract();
        ExtractableResponse<Response> retry = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "PRECES", memberId))
                .then()
                .extract();

        assertThat(first.statusCode()).as(first.asString()).isEqualTo(200);
        assertThat(retry.statusCode()).as(retry.asString()).isEqualTo(200);
        assertThat(activePresenceId(missaId, memberId)).isEqualTo(presenceId);
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isEqualTo(1);
        assertThat(activityMetadata("MISSA_MEMBER_ASSIGNED", missaId))
                .containsEntry("presenceCreated", false);
    }

    @Test
    @DisplayName("REQ-MISSA-004/006 - occupied singleton rejects replacement while multi-member responsibilities remain unbounded and ordered")
    void responsibilitiesShouldEnforceSingletonOccupancyAndOrderedUnboundedSets() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Responsibility cardinality");
        UUID currentMember = createAccountlessMember("Zuleica", "Costa", "ACTIVE");
        UUID challenger = createAccountlessMember("Ana", "Lima", "ACTIVE");
        UUID third = createAccountlessMember("Bruno", "Lima", "ACTIVE");
        authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "COMENTARIOS", currentMember))
                .then()
                .statusCode(200);
        clearActivities();

        ExtractableResponse<Response> conflict = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "COMENTARIOS", challenger))
                .then()
                .extract();
        assertThat(conflict.statusCode()).as(conflict.asString()).isEqualTo(409);
        assertThat(conflict.<String>path("code"))
                .isEqualTo("MISSA_RESPONSIBILITY_ALREADY_ASSIGNED");
        assertThat(conflict.<Map<String, Object>>path("details"))
                .containsOnlyKeys("missaId", "responsibility", "currentMemberId")
                .containsEntry("missaId", missaId.toString())
                .containsEntry("responsibility", "COMENTARIOS")
                .containsEntry("currentMemberId", currentMember.toString());
        assertThat(activePresenceIdOrNull(missaId, challenger)).isNull();

        for (UUID memberId : List.of(currentMember, challenger, third)) {
            authenticatedJsonRequest(caller)
                    .put(assignmentPath(missaId, "ACOLHIDA", memberId))
                    .then()
                    .statusCode(200);
            authenticatedJsonRequest(caller)
                    .put(assignmentPath(missaId, "BANDA", memberId))
                    .then()
                    .statusCode(200);
        }
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(MISSAS + "/" + missaId)
                .then()
                .statusCode(200)
                .extract();
        assertThat(assignmentMemberIds(detail, "ACOLHIDA"))
                .containsExactly(challenger, third, currentMember);
        assertThat(assignmentMemberIds(detail, "BANDA"))
                .containsExactly(challenger, third, currentMember);
    }

    @Test
    @DisplayName("REQ-MISSA-005 - inactive target conflicts, while a retained assignment stays visible and retry remains idempotent")
    void inactiveMemberShouldBeRejectedUnlessTheExactAssignmentAlreadyExists() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Inactive Member handling");
        UUID retainedMember = createAccountlessMember("Davi", "Rocha", "ACTIVE");
        UUID neverAssignedMember = createAccountlessMember("Eva", "Ramos", "INACTIVE");
        authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "SALMO", retainedMember))
                .then()
                .statusCode(200);
        jdbcTemplate.update(
                "UPDATE members SET status = CAST('INACTIVE' AS member_status_enum) WHERE id = ?",
                retainedMember
        );
        clearActivities();

        ExtractableResponse<Response> retry = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "SALMO", retainedMember))
                .then()
                .extract();
        assertThat(retry.statusCode()).as(retry.asString()).isEqualTo(200);
        assertAssignedMember(retry, "SALMO", retainedMember, "Davi", "Rocha", "INACTIVE");
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();

        ExtractableResponse<Response> conflict = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "BANDA", neverAssignedMember))
                .then()
                .extract();
        assertThat(conflict.statusCode()).as(conflict.asString()).isEqualTo(409);
        assertThat(conflict.<String>path("code")).isEqualTo("MISSA_MEMBER_NOT_ACTIVE");
        assertThat(conflict.<Map<String, Object>>path("details"))
                .containsOnlyKeys("missaId", "memberId", "status")
                .containsEntry("missaId", missaId.toString())
                .containsEntry("memberId", neverAssignedMember.toString())
                .containsEntry("status", "INACTIVE");
    }

    @Test
    @DisplayName("REQ-MISSA-008 - exact removal keeps Presence; absent and wrong-singleton pairs are unaudited no-ops")
    void removalShouldAffectOnlyTheExactAssignmentAndNeverPresence() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Assignment removal");
        UUID assignedMember = createAccountlessMember("Fabi", "Moraes", "ACTIVE");
        UUID otherMember = createAccountlessMember("Gabi", "Nunes", "ACTIVE");
        authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "PRECES", assignedMember))
                .then()
                .statusCode(200);
        UUID presenceId = activePresenceId(missaId, assignedMember);
        clearActivities();

        assertThat(authenticatedJsonRequest(caller)
                .delete(assignmentPath(missaId, "PRECES", otherMember)).statusCode()).isEqualTo(204);
        ExtractableResponse<Response> stillAssigned = authenticatedJsonRequest(caller)
                .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
        assertThat(assignmentMemberIds(stillAssigned, "PRECES")).containsExactly(assignedMember);
        assertThat(activityCountFor("MISSA_MEMBER_REMOVED", missaId)).isZero();

        assertThat(authenticatedJsonRequest(caller)
                .delete(assignmentPath(missaId, "PRECES", assignedMember)).statusCode()).isEqualTo(204);
        assertThat(activePresenceId(missaId, assignedMember)).isEqualTo(presenceId);
        assertThat(activityCountFor("MISSA_MEMBER_REMOVED", missaId)).isEqualTo(1);
        assertThat(authenticatedJsonRequest(caller)
                .delete(assignmentPath(missaId, "PRECES", assignedMember)).statusCode()).isEqualTo(204);
        assertThat(activityCountFor("MISSA_MEMBER_REMOVED", missaId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-MISSA-009 - COMPLETED actual changes require reason but an exact retry does not")
    void completedAssignmentChangesShouldRequireReasonOnlyWhenStateChanges() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createCompletedMissa(caller, "Completed assignment correction");
        UUID memberId = createAccountlessMember("Heitor", "Oliveira", "ACTIVE");

        ExtractableResponse<Response> missingReason = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "BANDA", memberId))
                .then()
                .extract();
        assertThat(missingReason.statusCode()).as(missingReason.asString()).isEqualTo(400);

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .put(assignmentPath(missaId, "BANDA", memberId))
                .then()
                .statusCode(200);
        clearActivities();
        assertThat(authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "BANDA", memberId)).statusCode()).isEqualTo(200);
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();

        assertThat(authenticatedJsonRequest(caller)
                .delete(assignmentPath(missaId, "BANDA", memberId)).statusCode()).isEqualTo(400);
        assertThat(authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete(assignmentPath(missaId, "BANDA", memberId)).statusCode()).isEqualTo(204);
        assertThat(missaActivity("MISSA_MEMBER_REMOVED", missaId).get("reason"))
                .isEqualTo("Correcting the Missa plan");
    }

    @Test
    @DisplayName("REQ-MISSA-009/014/015 - locked, finalized, and cancelled Missas freeze assignments with intent-specific conflict")
    void closedLifecycleStatesShouldFreezeAssignments() {
        AuthSession caller = newSession("SUDO");
        UUID completed = createCompletedMissa(caller, "Closed assignment states");
        UUID memberId = createAccountlessMember("Iara", "Pires", "ACTIVE");
        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/lock", completed)
                .then()
                .statusCode(200);

        ExtractableResponse<Response> locked = authenticatedJsonRequest(caller)
                .put(assignmentPath(completed, "ACOLHIDA", memberId))
                .then()
                .extract();
        assertAssignmentNotAllowed(locked, completed, "ACOLHIDA", "LOCKED");

        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/finalize", completed)
                .then()
                .statusCode(200);
        ExtractableResponse<Response> finalized = authenticatedJsonRequest(caller)
                .put(assignmentPath(completed, "ACOLHIDA", memberId))
                .then()
                .extract();
        assertAssignmentNotAllowed(finalized, completed, "ACOLHIDA", "FINALIZED");

        UUID scheduled = createScheduledMissa(caller, "Cancelled assignment state");
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(MISSAS + "/{id}/cancel", scheduled)
                .then()
                .statusCode(200);
        ExtractableResponse<Response> cancelled = authenticatedJsonRequest(caller)
                .put(assignmentPath(scheduled, "ACOLHIDA", memberId))
                .then()
                .extract();
        assertAssignmentNotAllowed(cancelled, scheduled, "ACOLHIDA", "CANCELLED");
    }

    @Test
    @DisplayName("REQ-MISSA-015/019 and REQ-PRESENCE-018 - active assignment blocks Presence removal while open, but cancellation permits correction")
    void presenceRemovalShouldHonorAssignmentDependencyAndCancellationException() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Presence dependency");
        UUID memberId = createAccountlessMember("Joana", "Queiroz", "ACTIVE");
        authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "ACOLHIDA", memberId))
                .then()
                .statusCode(200);
        UUID presenceId = activePresenceId(missaId, memberId);

        ExtractableResponse<Response> blocked = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete("/events/{eventId}/presences/{memberId}", missaId, memberId)
                .then()
                .extract();
        assertThat(blocked.statusCode()).as(blocked.asString()).isEqualTo(409);
        assertThat(blocked.<String>path("code"))
                .isEqualTo("MISSA_ASSIGNMENT_REQUIRES_PRESENCE");
        assertThat(blocked.<Map<String, Object>>path("details"))
                .containsOnlyKeys("missaId", "memberId")
                .containsEntry("missaId", missaId.toString())
                .containsEntry("memberId", memberId.toString());
        assertThat(activePresenceId(missaId, memberId)).isEqualTo(presenceId);

        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(MISSAS + "/{id}/cancel", missaId)
                .then()
                .statusCode(200);
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete("/events/{eventId}/presences/{memberId}", missaId, memberId)
                .then()
                .statusCode(204);
        assertThat(activePresenceIdOrNull(missaId, memberId)).isNull();
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
        assertThat(assignmentMemberIds(detail, "ACOLHIDA")).containsExactly(memberId);
    }

    @Test
    @DisplayName("REQ-MISSA-013/014/017 - specialized edit and lifecycle commands emit only Missa activities; normalized edit is a no-op")
    void specializedEditingAndLifecycleShouldUseMissaActivities() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createCompletedMissa(caller, "Editable Missa");
        Map<String, Object> replacement = currentMissaReplacement(missaId);
        replacement.put("title", "  Missa corrigida  ");
        clearActivities();

        ExtractableResponse<Response> changed = authenticatedJsonRequest(caller)
                .body(replacement)
                .put(MISSAS + "/" + missaId)
                .then()
                .extract();
        assertThat(changed.statusCode()).as(changed.asString()).isEqualTo(200);
        assertThat(changed.<String>path("event.title")).isEqualTo("Missa corrigida");
        assertThat(activityCountFor("MISSA_UPDATED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("EVENT_UPDATED", missaId)).isZero();
        assertThat(activityMetadata("MISSA_UPDATED", missaId))
                .containsOnlyKeys("changedFields")
                .containsEntry("changedFields", List.of("title"));

        clearActivities();
        Map<String, Object> noOp = currentMissaReplacement(missaId);
        noOp.put("title", "  Missa corrigida  ");
        assertThat(authenticatedJsonRequest(caller)
                .body(noOp).put(MISSAS + "/" + missaId).statusCode()).isEqualTo(200);
        assertThat(activityCountFor("MISSA_UPDATED", missaId)).isZero();

        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/lock", missaId)
                .then()
                .statusCode(200);
        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/finalize", missaId)
                .then()
                .statusCode(200);
        ExtractableResponse<Response> reopened = authenticatedJsonRequest(caller)
                .body(Map.of("targetStatus", "COMPLETED", "reason", VALID_REASON))
                .patch(MISSAS + "/{id}/reopen", missaId)
                .then()
                .extract();
        assertThat(reopened.statusCode()).as(reopened.asString()).isEqualTo(200);
        assertThat(reopened.<String>path("event.status")).isEqualTo("COMPLETED");
        assertThat(activityCountFor("MISSA_LOCKED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("MISSA_FINALIZED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("MISSA_REOPENED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("EVENT_LOCKED", missaId)).isZero();
        assertThat(activityCountFor("EVENT_FINALIZED", missaId)).isZero();
        assertThat(activityCountFor("EVENT_REOPENED", missaId)).isZero();
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-MISSA-015/016 - cancelled deletion removes current aggregate state but preserves removed Presence and activity history")
    void cancelledMissaDeletionShouldPreserveHistoricalPresenceAndActivities() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Missa to delete");
        UUID memberId = createAccountlessMember("Kaique", "Reis", "ACTIVE");
        authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "BANDA", memberId))
                .then()
                .statusCode(200);
        UUID presenceId = activePresenceId(missaId, memberId);
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .patch(MISSAS + "/{id}/cancel", missaId)
                .then()
                .statusCode(200);
        authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete("/events/{eventId}/presences/{memberId}", missaId, memberId)
                .then()
                .statusCode(204);
        clearActivities();

        ExtractableResponse<Response> deletion = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete(MISSAS + "/" + missaId)
                .then()
                .extract();

        assertThat(deletion.statusCode()).as(deletion.asString()).isEqualTo(204);
        assertThat(authenticatedJsonRequest(caller)
                .get(MISSAS + "/" + missaId).statusCode()).isEqualTo(404);
        assertThat(jsonRequest().get("/events/" + missaId).statusCode()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM presences WHERE id = ? AND deleted_at IS NOT NULL",
                Long.class,
                presenceId
        )).isEqualTo(1L);
        assertThat(activityCountFor("MISSA_DELETED", missaId)).isEqualTo(1);
        assertThat(activityCountFor("EVENT_DELETED", missaId)).isZero();
    }

    @Test
    @DisplayName("REQ-MISSA-016 - active Presence blocks deletion with active count")
    void activePresenceShouldBlockMissaDeletion() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Protected Missa deletion");
        UUID firstMember = createAccountlessMember("Lia", "Santos", "ACTIVE");
        UUID secondMember = createAccountlessMember("Mara", "Teixeira", "ACTIVE");
        registerPresence(caller, missaId, firstMember, null);
        registerPresence(caller, missaId, secondMember, null);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON))
                .delete(MISSAS + "/" + missaId)
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("EVENT_HAS_PRESENCES");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsOnlyKeys("eventId", "activePresenceCount")
                .containsEntry("eventId", missaId.toString())
                .containsEntry("activePresenceCount", 2);
        assertThat(activityCountFor("MISSA_DELETED", missaId)).isZero();
    }

    @Test
    @DisplayName("REQ-MISSA-018 and ADR-0032 - concurrent singleton assignment -> one winner and one stable occupied conflict")
    void concurrentSingletonAssignmentShouldHaveOneWinner() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Concurrent assignment");
        UUID firstMember = createAccountlessMember("Nina", "Vieira", "ACTIVE");
        UUID secondMember = createAccountlessMember("Olga", "Xavier", "ACTIVE");
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> first = executor.submit(() ->
                    concurrentAssignment(caller, missaId, firstMember, ready, start));
            Future<ExtractableResponse<Response>> second = executor.submit(() ->
                    concurrentAssignment(caller, missaId, secondMember, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(200, 409);
            ExtractableResponse<Response> conflict = responses.stream()
                    .filter(response -> response.statusCode() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflict.<String>path("code"))
                    .isEqualTo("MISSA_RESPONSIBILITY_ALREADY_ASSIGNED");
            ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                    .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
            assertThat(assignmentMemberIds(detail, "COMENTARIOS")).hasSize(1);
            assertThat(activePresenceCount(missaId)).isEqualTo(1);
            assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-MISSA-004/020 - unknown responsibility -> HTTP 400 without mutation or activity")
    void unknownResponsibilityShouldBeRejectedWithoutMutation() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Closed responsibility catalog");
        UUID memberId = createAccountlessMember("Paula", "Zanetti", "ACTIVE");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "INSTRUMENTO", memberId))
                .then()
                .extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        assertThat(activePresenceIdOrNull(missaId, memberId)).isNull();
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();
    }

    @Test
    @DisplayName("REQ-MISSA-002 - creation boundaries and invalid equivalence classes follow the common Event contract")
    void creationShouldEnforceDocumentedBoundariesAndRejectOwnedFields() {
        AuthSession caller = newSession("SUDO");
        UUID locationId = createLocation(caller, "Missa creation boundaries");
        Instant begin = Instant.parse("2035-01-01T10:00:00Z");
        Map<String, Object> boundary = missaPayload(
                "t".repeat(255),
                locationId,
                null,
                begin,
                begin.plusNanos(1_000)
        );
        boundary.put("description", "d".repeat(10_000));

        ExtractableResponse<Response> accepted = authenticatedJsonRequest(caller)
                .body(boundary).post(MISSAS).then().extract();
        assertThat(accepted.statusCode()).as(accepted.asString()).isEqualTo(201);
        UUID acceptedId = trackCreatedMissa(accepted);
        assertThat(accepted.<String>path("event.title")).hasSize(255);
        assertThat(accepted.<String>path("event.description")).hasSize(10_000);
        assertThat(accepted.<String>path("event.beginDate")).isEqualTo(begin.toString());
        assertThat(accepted.<String>path("event.endDate"))
                .isEqualTo(begin.plusNanos(1_000).toString());
        assertThat(acceptedId).isNotNull();

        List<Map<String, Object>> invalidChanges = List.of(
                Map.of("title", " "),
                Map.of("title", "t".repeat(256)),
                Map.of("description", "d".repeat(10_001)),
                Map.of("endDate", begin.toString()),
                Map.of("type", "MISSA"),
                Map.of("status", "SCHEDULED"),
                Map.of("cancellationReason", "Client value"),
                Map.of("memberId", UUID.randomUUID().toString())
        );
        for (Map<String, Object> changes : invalidChanges) {
            Map<String, Object> payload = missaPayload(
                    "Valid Missa",
                    locationId,
                    null,
                    begin,
                    begin.plusSeconds(3_600)
            );
            payload.putAll(changes);
            ExtractableResponse<Response> rejected = authenticatedJsonRequest(caller)
                    .body(payload).post(MISSAS).then().extract();
            trackUnexpectedlyCreatedMissa(rejected);
            assertThat(rejected.statusCode()).as(changes.toString()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("REQ-MISSA-002/010 - MISSA_CREATE is sufficient for public creation and restricted creation requires the exact audience permission")
    void creationShouldUseOnlyMissaCreateAndTheSelectedAudiencePermission() {
        AuthSession setup = newSession("SUDO");
        UUID locationId = createLocation(setup, "Missa creation authorization");
        AuthSession publicCreator = newSessionWithPermissions("MISSA_CREATE");
        AuthSession restrictedCreator = newSessionWithPermissions("MISSA_CREATE", "EVENT_GET_COORD");
        UUID audiencePermissionId = permissionId("EVENT_GET_COORD");
        Instant begin = Instant.now().plusSeconds(3_600);

        ExtractableResponse<Response> publicResponse = authenticatedJsonRequest(publicCreator)
                .body(missaPayload(
                        "Public Missa without EVENT_CREATE",
                        locationId,
                        null,
                        begin,
                        begin.plusSeconds(3_600)
                ))
                .post(MISSAS)
                .then()
                .extract();
        assertThat(publicResponse.statusCode()).as(publicResponse.asString()).isEqualTo(201);
        trackCreatedMissa(publicResponse);

        ExtractableResponse<Response> forbidden = authenticatedJsonRequest(publicCreator)
                .body(missaPayload(
                        "Hidden restricted Missa",
                        locationId,
                        audiencePermissionId,
                        begin,
                        begin.plusSeconds(3_600)
                ))
                .post(MISSAS)
                .then()
                .extract();
        assertThat(forbidden.statusCode()).as(forbidden.asString()).isEqualTo(403);

        ExtractableResponse<Response> restricted = authenticatedJsonRequest(restrictedCreator)
                .body(missaPayload(
                        "Visible restricted Missa",
                        locationId,
                        audiencePermissionId,
                        begin,
                        begin.plusSeconds(3_600)
                ))
                .post(MISSAS)
                .then()
                .extract();
        assertThat(restricted.statusCode()).as(restricted.asString()).isEqualTo(201);
        trackCreatedMissa(restricted);
    }

    @Test
    @DisplayName("REQ-MISSA-005 - missing and soft-deleted Members cannot receive new assignments")
    void missingAndSoftDeletedMembersShouldReturnResourceNotFound() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Missing Member assignments");
        UUID missingMemberId = UUID.randomUUID();
        UUID deletedMemberId = createAccountlessMember("Removida", "Historico", "ACTIVE");
        softDeleteMember(deletedMemberId);
        clearActivities();

        for (UUID memberId : List.of(missingMemberId, deletedMemberId)) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .put(assignmentPath(missaId, "BANDA", memberId))
                    .then()
                    .extract();
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(404);
            assertThat(response.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        }
        assertThat(activePresenceCount(missaId)).isZero();
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-MISSA-001/007/017 - activity persistence failure rolls back creation and assignment-Presence mutation")
    void activityFailureShouldRollBackMissaCreationAndAssignment() {
        AuthSession caller = newSession("SUDO");
        UUID locationId = createLocation(caller, "Missa activity rollback");
        long eventsBefore = activeEventCount();
        failActivityWritesFor("MISSA_CREATED");

        ExtractableResponse<Response> failedCreation = authenticatedJsonRequest(caller)
                .body(missaPayload(
                        "Rolled back Missa",
                        locationId,
                        null,
                        Instant.now().plusSeconds(3_600),
                        Instant.now().plusSeconds(7_200)
                ))
                .post(MISSAS)
                .then()
                .extract();
        assertThat(failedCreation.statusCode()).isEqualTo(500);
        assertThat(activeEventCount()).isEqualTo(eventsBefore);
        removeActivityFailureTrigger();

        UUID missaId = createScheduledMissa(caller, "Assignment activity rollback");
        UUID memberId = createAccountlessMember("Rollback", "Member", "ACTIVE");
        clearActivities();
        failActivityWritesFor("MISSA_MEMBER_ASSIGNED");

        ExtractableResponse<Response> failedAssignment = authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "PRECES", memberId))
                .then()
                .extract();
        assertThat(failedAssignment.statusCode()).isEqualTo(500);
        assertThat(activePresenceIdOrNull(missaId, memberId)).isNull();
        assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();
        removeActivityFailureTrigger();
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
        assertThat(assignmentMemberIds(detail, "PRECES")).isEmpty();
    }

    @Test
    @DisplayName("REQ-MISSA-013/017 - audience replacement requires a reason and new exact visibility; locked Missa cannot move its end into the future")
    void replacementShouldEnforceAudienceReasonVisibilityAndLockedDateBoundary() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createCompletedMissa(caller, "Audience replacement");
        UUID audiencePermissionId = permissionId("EVENT_GET_COORD");
        Map<String, Object> replacement = currentMissaReplacement(missaId);
        replacement.put("requiredPermissionId", audiencePermissionId.toString());
        clearActivities();

        ExtractableResponse<Response> missingReason = authenticatedJsonRequest(caller)
                .body(replacement).put(MISSAS + "/" + missaId).then().extract();
        assertThat(missingReason.statusCode()).as(missingReason.asString()).isEqualTo(400);
        assertThat(activityCountFor("MISSA_UPDATED", missaId)).isZero();

        replacement.put("reason", "\u2003  Audience correction  \u2003");
        ExtractableResponse<Response> changed = authenticatedJsonRequest(caller)
                .body(replacement).put(MISSAS + "/" + missaId).then().extract();
        assertThat(changed.statusCode()).as(changed.asString()).isEqualTo(200);
        assertThat(changed.<String>path("event.requiredPermission.code"))
                .isEqualTo("EVENT_GET_COORD");
        assertThat(missaActivity("MISSA_UPDATED", missaId).get("reason"))
                .isEqualTo("Audience correction");

        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/lock", missaId)
                .then()
                .statusCode(200);
        Map<String, Object> futureReplacement = currentMissaReplacement(missaId);
        futureReplacement.put("endDate", Instant.now().plusSeconds(7_200).toString());
        ExtractableResponse<Response> locked = authenticatedJsonRequest(caller)
                .body(futureReplacement).put(MISSAS + "/" + missaId).then().extract();
        assertThat(locked.statusCode()).as(locked.asString()).isEqualTo(409);
        assertThat(locked.<String>path("code")).isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");
    }

    @Test
    @DisplayName("REQ-MISSA-014 - direct finalization, both reopen targets, and invalid no-op transitions follow the closed matrix")
    void lifecycleShouldSupportTheCompleteTransitionMatrixAndRejectAbsentTransitions() {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createCompletedMissa(caller, "Complete Missa lifecycle");

        ExtractableResponse<Response> finalized = authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/finalize", missaId).then().extract();
        assertThat(finalized.statusCode()).as(finalized.asString()).isEqualTo(200);
        assertThat(finalized.<String>path("event.status")).isEqualTo("FINALIZED");

        ExtractableResponse<Response> reopenedLocked = authenticatedJsonRequest(caller)
                .body(Map.of("targetStatus", "LOCKED", "reason", VALID_REASON))
                .patch(MISSAS + "/{id}/reopen", missaId).then().extract();
        assertThat(reopenedLocked.statusCode()).as(reopenedLocked.asString()).isEqualTo(200);
        assertThat(reopenedLocked.<String>path("event.status")).isEqualTo("LOCKED");

        authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/finalize", missaId)
                .then()
                .statusCode(200);
        ExtractableResponse<Response> reopenedCompleted = authenticatedJsonRequest(caller)
                .body(Map.of("targetStatus", "COMPLETED", "reason", VALID_REASON))
                .patch(MISSAS + "/{id}/reopen", missaId).then().extract();
        assertThat(reopenedCompleted.statusCode()).as(reopenedCompleted.asString()).isEqualTo(200);
        assertThat(reopenedCompleted.<String>path("event.status")).isEqualTo("COMPLETED");

        ExtractableResponse<Response> noOp = authenticatedJsonRequest(caller)
                .body(Map.of("targetStatus", "COMPLETED", "reason", VALID_REASON))
                .patch(MISSAS + "/{id}/reopen", missaId).then().extract();
        assertThat(noOp.statusCode()).as(noOp.asString()).isEqualTo(409);
        assertThat(noOp.<String>path("code")).isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");

        UUID scheduledId = createScheduledMissa(caller, "Premature lock");
        ExtractableResponse<Response> prematureLock = authenticatedJsonRequest(caller)
                .patch(MISSAS + "/{id}/lock", scheduledId).then().extract();
        assertThat(prematureLock.statusCode()).as(prematureLock.asString()).isEqualTo(409);
        assertThat(prematureLock.<String>path("code"))
                .isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-MISSA-001 - database rejects a Missa UUID different from its Event UUID")
    void persistenceShouldEnforceSharedMissaAndEventIdentity() {
        UUID missaId = createScheduledMissa(newSession("SUDO"), "Shared Missa identity");
        UUID mismatchedId = UUID.randomUUID();

        Throwable violation = catchThrowable(() -> jdbcTemplate.update(
                "UPDATE missas SET id = ? WHERE id = ?",
                mismatchedId,
                missaId
        ));
        if (violation == null) {
            jdbcTemplate.update("UPDATE missas SET id = ? WHERE id = ?", missaId, mismatchedId);
        }

        assertThat(violation)
                .as("The database must enforce the shared Missa/Event UUID identity")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("REQ-MISSA-018 and ADR-0032 - concurrent repeated assignment leaves one relationship, Presence, and activity")
    void concurrentRepeatedAssignmentShouldRemainIdempotent() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Concurrent repeated assignment");
        UUID memberId = createAccountlessMember("Repetida", "Concorrente", "ACTIVE");
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> first = executor.submit(() ->
                    concurrentAssignment(caller, missaId, memberId, ready, start));
            Future<ExtractableResponse<Response>> second = executor.submit(() ->
                    concurrentAssignment(caller, missaId, memberId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .extracting(ExtractableResponse::statusCode)
                    .containsOnly(200);
            ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                    .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
            assertThat(assignmentMemberIds(detail, "COMENTARIOS")).containsExactly(memberId);
            assertThat(activePresenceCount(missaId)).isEqualTo(1);
            assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-MISSA-018 and ADR-0032 - assignment waits for Member deactivation and rejects the latest inactive state")
    void assignmentShouldSerializeWithMemberDeactivation() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Concurrent Member deactivation");
        UUID memberId = createAccountlessMember("Desativada", "Concorrente", "ACTIVE");
        clearActivities();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection deactivation = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement update = deactivation.prepareStatement(
                     "UPDATE members SET status = CAST('INACTIVE' AS member_status_enum) WHERE id = ?"
             )) {
            deactivation.setAutoCommit(false);
            update.setObject(1, memberId);
            assertThat(update.executeUpdate()).isEqualTo(1);

            Future<ExtractableResponse<Response>> assignment = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .put(assignmentPath(missaId, "BANDA", memberId))
                            .then()
                            .extract());
            assertThat(awaitFutureCompletion(assignment, 1, TimeUnit.SECONDS))
                    .as("Assignment must wait for the locked Member row")
                    .isFalse();
            deactivation.commit();

            ExtractableResponse<Response> response = assignment.get(10, TimeUnit.SECONDS);
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(409);
            assertThat(response.<String>path("code")).isEqualTo("MISSA_MEMBER_NOT_ACTIVE");
            assertThat(activePresenceIdOrNull(missaId, memberId)).isNull();
            assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-MISSA-018 and ADR-0032 - assignment versus lifecycle closure -> latest serialized state and matching activities")
    void assignmentShouldSerializeWithLifecycleClosure() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createCompletedMissa(caller, "Concurrent lifecycle closure");
        UUID memberId = createAccountlessMember("Cecilia", "Fechamento", "ACTIVE");
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> assignment = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(reasonPayload(VALID_REASON))
                            .put(assignmentPath(missaId, "BANDA", memberId))
                            .then()
                            .extract()
            );
            Future<ExtractableResponse<Response>> closure = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .patch(MISSAS + "/{id}/lock", missaId)
                            .then()
                            .extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> assignmentResponse = assignment.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> closureResponse = closure.get(20, TimeUnit.SECONDS);
            assertThat(closureResponse.statusCode()).as(closureResponse.asString()).isEqualTo(200);
            assertThat(assignmentResponse.statusCode()).isIn(200, 409);
            if (assignmentResponse.statusCode() == 409) {
                assertThat(assignmentResponse.<String>path("code"))
                        .isEqualTo("MISSA_ASSIGNMENT_NOT_ALLOWED");
            }

            ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                    .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
            assertThat(detail.<String>path("event.status")).isEqualTo("LOCKED");
            boolean assignmentCommitted = assignmentResponse.statusCode() == 200;
            assertThat(assignmentMemberIds(detail, "BANDA"))
                    .containsExactlyElementsOf(assignmentCommitted ? List.of(memberId) : List.of());
            assertThat(activePresenceCount(missaId)).isEqualTo(assignmentCommitted ? 1 : 0);
            assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId))
                    .isEqualTo(assignmentCommitted ? 1 : 0);
            assertThat(activityCountFor("MISSA_LOCKED", missaId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-MISSA-018/019 and ADR-0032 - Presence removal versus assignment -> no committed assignment without active Presence")
    void presenceRemovalShouldSerializeWithAssignmentCreation() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Concurrent Presence dependency");
        UUID memberId = createAccountlessMember("Helena", "Presenca", "ACTIVE");
        registerPresence(caller, missaId, memberId, null);
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> assignment = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .put(assignmentPath(missaId, "ACOLHIDA", memberId))
                            .then()
                            .extract()
            );
            Future<ExtractableResponse<Response>> removal = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(reasonPayload(VALID_REASON))
                            .delete("/events/{eventId}/presences/{memberId}", missaId, memberId)
                            .then()
                            .extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> assignmentResponse = assignment.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> removalResponse = removal.get(20, TimeUnit.SECONDS);
            assertThat(assignmentResponse.statusCode()).as(assignmentResponse.asString()).isEqualTo(200);
            assertThat(removalResponse.statusCode()).isIn(204, 409);
            if (removalResponse.statusCode() == 409) {
                assertThat(removalResponse.<String>path("code"))
                        .isEqualTo("MISSA_ASSIGNMENT_REQUIRES_PRESENCE");
                assertThat(removalResponse.<Map<String, Object>>path("details"))
                        .containsOnlyKeys("missaId", "memberId");
            }

            ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                    .get(MISSAS + "/" + missaId).then().statusCode(200).extract();
            assertThat(assignmentMemberIds(detail, "ACOLHIDA")).containsExactly(memberId);
            assertThat(activePresenceCount(missaId)).isEqualTo(1);
            assertThat(activityCountFor("MISSA_MEMBER_ASSIGNED", missaId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-MISSA-016/018 and ADR-0032 - deletion versus Presence registration -> one serialized valid outcome")
    void deletionShouldSerializeWithPresenceRegistration() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID missaId = createScheduledMissa(caller, "Concurrent deletion and Presence");
        UUID memberId = createAccountlessMember("Laura", "Exclusao", "ACTIVE");
        Map<String, Object> presencePayload = new LinkedHashMap<>();
        presencePayload.put("memberId", memberId.toString());
        presencePayload.put("observations", null);
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> registration = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(presencePayload)
                            .post("/events/{eventId}/presences", missaId)
                            .then()
                            .extract()
            );
            Future<ExtractableResponse<Response>> deletion = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(reasonPayload(VALID_REASON))
                            .delete(MISSAS + "/" + missaId)
                            .then()
                            .extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> registrationResponse = registration.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> deletionResponse = deletion.get(20, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(
                    registrationResponse.statusCode(),
                    deletionResponse.statusCode()
            );
            assertThat(statuses).isIn(List.of(201, 409), List.of(404, 204));

            if (deletionResponse.statusCode() == 409) {
                assertThat(deletionResponse.<String>path("code")).isEqualTo("EVENT_HAS_PRESENCES");
                assertThat(activeEventCount(missaId)).isEqualTo(1);
                assertThat(activePresenceCount(missaId)).isEqualTo(1);
                assertThat(activityCountFor("PRESENCE_REGISTERED", UUID.fromString(
                        registrationResponse.path("id")
                ))).isEqualTo(1);
                assertThat(activityCountFor("MISSA_DELETED", missaId)).isZero();
            } else {
                assertThat(registrationResponse.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
                assertThat(activeEventCount(missaId)).isZero();
                assertThat(activePresenceCount(missaId)).isZero();
                assertThat(activityCountFor("MISSA_DELETED", missaId)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createScheduledMissa(AuthSession caller, String title) {
        return createMissa(
                caller,
                title,
                createLocation(caller, "Location for " + title),
                null,
                Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(7_200)
        );
    }

    private UUID createCompletedMissa(AuthSession caller, String title) {
        return createMissa(
                caller,
                title,
                createLocation(caller, "Location for " + title),
                null,
                Instant.now().minusSeconds(7_200),
                Instant.now().minusSeconds(3_600)
        );
    }

    private UUID createMissa(
            AuthSession caller,
            String title,
            UUID locationId,
            UUID requiredPermissionId,
            Instant beginDate,
            Instant endDate
    ) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(missaPayload(title, locationId, requiredPermissionId, beginDate, endDate))
                .post(MISSAS)
                .then()
                .extract();
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(201);
        return trackCreatedMissa(response);
    }

    private UUID createLocation(AuthSession caller, String name) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(gamLocationPayload(name))
                .post("/gam-locations")
                .then()
                .statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        trackGamLocation(id);
        return id;
    }

    private UUID createAccountlessMember(String firstName, String surname, String status) {
        UUID memberId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO members (id, account_id, first_name, surname, birth_date, phone_number, "
                        + "gam_entry_date, residential_city, contact_email, status, created_at, updated_at) "
                        + "VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, CAST(? AS member_status_enum), ?, ?)",
                memberId,
                firstName,
                surname,
                LocalDate.of(2000, 1, 1),
                "+5519" + Math.abs(memberId.hashCode() % 900_000_000 + 100_000_000),
                LocalDate.of(2020, 1, 1),
                "Synthetic City",
                memberId + "@example.com",
                status,
                now,
                now
        );
        accountlessMemberIds.add(memberId);
        return memberId;
    }

    private UUID registerPresence(AuthSession caller, UUID missaId, UUID memberId, String observations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", memberId.toString());
        payload.put("observations", observations);
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post("/events/{eventId}/presences", missaId)
                .then()
                .statusCode(201)
                .extract();
        return UUID.fromString(response.path("id"));
    }

    private ExtractableResponse<Response> concurrentAssignment(
            AuthSession caller,
            UUID missaId,
            UUID memberId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return authenticatedJsonRequest(caller)
                .put(assignmentPath(missaId, "COMENTARIOS", memberId))
                .then()
                .extract();
    }

    private Future<ExtractableResponse<Response>> concurrentRequest(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Supplier<ExtractableResponse<Response>> request
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return request.get();
        });
    }

    private boolean awaitFutureCompletion(Future<?> future, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                return true;
            }
            Thread.sleep(25);
        }
        return future.isDone();
    }

    private UUID trackCreatedMissa(ExtractableResponse<Response> response) {
        UUID id = UUID.fromString(response.path("id"));
        missaIds.add(id);
        trackEvent(id);
        return id;
    }

    private void trackUnexpectedlyCreatedMissa(ExtractableResponse<Response> response) {
        if (response.statusCode() == 201 && response.path("id") != null) {
            trackCreatedMissa(response);
        }
    }

    private static Map<String, Object> missaPayload(
            String title,
            UUID locationId,
            UUID requiredPermissionId,
            Instant beginDate,
            Instant endDate
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("description", "Missa integration fixture");
        payload.put("gamLocationId", locationId.toString());
        payload.put("requiredPermissionId",
                requiredPermissionId == null ? null : requiredPermissionId.toString());
        payload.put("beginDate", beginDate.toString());
        payload.put("endDate", endDate.toString());
        return payload;
    }

    private static String assignmentPath(UUID missaId, String responsibility, UUID memberId) {
        return MISSAS + "/" + missaId + "/assignments/" + responsibility + "/members/" + memberId;
    }

    private void assertEmptyResponsibilityCatalog(ExtractableResponse<Response> response) {
        List<Map<String, Object>> assignments = response.path("assignments");
        assertThat(assignments).hasSize(7);
        assertThat(assignments).extracting(entry -> entry.get("responsibility"))
                .containsExactlyElementsOf(RESPONSIBILITIES);
        assertThat(assignments).allSatisfy(entry -> {
            assertThat(entry).containsOnlyKeys("responsibility", "members");
            assertThat((List<?>) entry.get("members")).isEmpty();
        });
    }

    private static void assertAssignedMember(
            ExtractableResponse<Response> response,
            String responsibility,
            UUID memberId,
            String firstName,
            String surname,
            String status
    ) {
        Map<String, Object> member = assignmentEntries(response).stream()
                .filter(entry -> responsibility.equals(entry.get("responsibility")))
                .map(entry -> (List<Map<String, Object>>) entry.get("members"))
                .flatMap(List::stream)
                .findFirst()
                .orElseThrow();
        assertThat(member)
                .containsOnlyKeys("id", "firstName", "surname", "status")
                .containsEntry("id", memberId.toString())
                .containsEntry("firstName", firstName)
                .containsEntry("surname", surname)
                .containsEntry("status", status);
    }

    private static List<UUID> assignmentMemberIds(
            ExtractableResponse<Response> response,
            String responsibility
    ) {
        return assignmentEntries(response).stream()
                .filter(entry -> responsibility.equals(entry.get("responsibility")))
                .map(entry -> (List<Map<String, Object>>) entry.get("members"))
                .flatMap(List::stream)
                .map(member -> UUID.fromString(member.get("id").toString()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> assignmentEntries(ExtractableResponse<Response> response) {
        return response.path("assignments");
    }

    private void assertAssignmentNotAllowed(
            ExtractableResponse<Response> response,
            UUID missaId,
            String responsibility,
            String status
    ) {
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("MISSA_ASSIGNMENT_NOT_ALLOWED");
        assertThat(response.<Map<String, Object>>path("details"))
                .containsOnlyKeys("missaId", "responsibility", "status", "evaluationInstant")
                .containsEntry("missaId", missaId.toString())
                .containsEntry("responsibility", responsibility)
                .containsEntry("status", status);
        assertThat(response.<String>path("details.evaluationInstant")).isNotBlank();
    }

    private UUID activePresenceId(UUID eventId, UUID memberId) {
        UUID id = activePresenceIdOrNull(eventId, memberId);
        assertThat(id).as("active Presence for Event/Member").isNotNull();
        return id;
    }

    private UUID activePresenceIdOrNull(UUID eventId, UUID memberId) {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM presences WHERE event_id = ? AND member_id = ? AND deleted_at IS NULL",
                UUID.class,
                eventId,
                memberId
        );
        assertThat(ids).hasSizeLessThanOrEqualTo(1);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String activePresenceObservations(UUID eventId, UUID memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT observations FROM presences "
                        + "WHERE event_id = ? AND member_id = ? AND deleted_at IS NULL",
                String.class,
                eventId,
                memberId
        );
    }

    private long activePresenceCount(UUID eventId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM presences WHERE event_id = ? AND deleted_at IS NULL",
                Long.class,
                eventId
        ));
    }

    private long activeEventCount() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE deleted_at IS NULL",
                Long.class
        ));
    }

    private long activeEventCount(UUID eventId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE id = ? AND deleted_at IS NULL",
                Long.class,
                eventId
        ));
    }

    private long activityCountFor(String action, UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = ? AND target_id = ?",
                Long.class,
                action,
                targetId
        ));
    }

    private Map<String, Object> missaActivity(String action, UUID targetId) {
        return jdbcTemplate.queryForMap(
                "SELECT actor_kind, actor_account_id, actor_reference, target_type, target_id, "
                        + "target_scope, reason, metadata, request_id "
                        + "FROM activity_logs WHERE action = ? AND target_id = ?",
                action,
                targetId
        );
    }

    private Map<String, Object> activityMetadata(String action, UUID targetId) {
        return JsonPath.from(missaActivity(action, targetId).get("metadata").toString()).getMap("$");
    }

    private Map<String, Object> currentMissaReplacement(UUID missaId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, description, gam_location_id AS \"gamLocationId\", "
                        + "required_permission_id AS \"requiredPermissionId\", "
                        + "begin_date AS \"beginDate\", end_date AS \"endDate\" "
                        + "FROM events WHERE id = ?",
                missaId
        );
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("title", row.get("title"));
        replacement.put("description", row.get("description"));
        replacement.put("gamLocationId", row.get("gamLocationId").toString());
        replacement.put("requiredPermissionId", row.get("requiredPermissionId") == null
                ? null : row.get("requiredPermissionId").toString());
        replacement.put("beginDate", ((Timestamp) row.get("beginDate")).toInstant().toString());
        replacement.put("endDate", ((Timestamp) row.get("endDate")).toInstant().toString());
        return replacement;
    }

    private List<String> rolePermissionCodes(AuthSession caller, String roleName) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get("/roles/{roleId}/permissions", roleId(roleName))
                .then()
                .statusCode(200)
                .extract();
        return response.<List<Map<String, Object>>>path("permissions").stream()
                .map(permission -> permission.get("code").toString())
                .toList();
    }

    private void assertPermissionMetadata(
            AuthSession caller,
            String code,
            String label,
            String description
    ) {
        List<Map<String, Object>> persisted = jdbcTemplate.queryForList(
                "SELECT id, code, label, description FROM permissions "
                        + "WHERE code = ? AND deleted_at IS NULL",
                code
        );
        assertThat(persisted)
                .as("active system permission %s", code)
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission)
                            .containsEntry("code", code)
                            .containsEntry("label", label)
                            .containsEntry("description", description);
                    UUID permissionId = (UUID) permission.get("id");
                    Map<String, Object> apiPermission = authenticatedJsonRequest(caller)
                            .get("/permissions/{permissionId}", permissionId)
                            .then()
                            .statusCode(200)
                            .extract()
                            .jsonPath()
                            .getMap("$");
                    assertThat(apiPermission)
                            .containsEntry("code", code)
                            .containsEntry("label", label)
                            .containsEntry("description", description);
                });
    }

    private void deleteMissaAssignmentsIfPresent(String table, String idColumn, UUID missaId) {
        if (tableExists(table)) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE " + idColumn + " = ?", missaId);
        }
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?)",
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(exists);
    }
}
