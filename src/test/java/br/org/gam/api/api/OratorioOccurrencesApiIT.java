package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@DisplayName("API - Oratorio occurrences and planning")
class OratorioOccurrencesApiIT extends OratorioModuleApiTestSupport {

    private static final String ORATORIOS = "/oratorios";

    @Test
    @DisplayName("REQ-ORATORIO-001, REQ-ORATORIO-002 and REQ-ORATORIO-004 - date-only creation derives immutable Event and fixed schedule")
    void dateOnlyCreationShouldDeriveSharedEventAndFixedSchedule() {
        AuthSession caller = sudoSession();
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(Map.of("date", "2030-06-15"))
                .post(ORATORIOS)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(response.path("id"));
        trackOratorio(id);
        assertPublicApiLocation(response, ORATORIOS + "/" + id);
        assertThat(response.<String>path("event.id")).isEqualTo(id.toString());
        assertThat(response.<String>path("event.title")).isEqualTo("Oratório");
        assertThat(response.<String>path("event.description")).isEmpty();
        assertThat(response.<String>path("event.beginDate")).isEqualTo("2030-06-15T17:00:00Z");
        assertThat(response.<String>path("event.endDate")).isEqualTo("2030-06-15T20:00:00Z");
        assertThat(response.<String>path("event.type")).isEqualTo("ORATORIO");
        assertThat(response.<String>path("event.status")).isEqualTo("SCHEDULED");
        assertThat(response.<String>path("event.requiredPermission.code")).isEqualTo("EVENT_GET_MEMBER");
        assertThat(response.<String>path("event.gamLocation.code")).isEqualTo("DBSM");
        assertThat(response.<Boolean>path("event.gamLocation.systemManaged")).isTrue();
        assertThat(response.<String>path("event.gamLocation.name")).isEqualTo("Dom Bosco São Mário");
        assertThat(response.asString())
                .contains(
                        "14:00", "15:30", "16:30", "17:00",
                        "Recreação livre", "Gincana",
                        "Boa Tarde das Crianças", "Boa Tarde dos Jovens", "Lanche"
                );
        assertThat(activityCountForTarget(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIO-002 - past and present dates use common temporal status without an artificial horizon")
    void pastAndDistantFutureDatesShouldBeAccepted() {
        AuthSession caller = sudoSession();

        ExtractableResponse<Response> past = authenticatedJsonRequest(caller)
                .body(Map.of("date", "2000-01-01"))
                .post(ORATORIOS)
                .then()
                .extract();
        assertThat(past.statusCode()).isEqualTo(201);
        UUID pastId = UUID.fromString(past.path("id"));
        trackOratorio(pastId);
        assertThat(past.<String>path("event.status")).isEqualTo("COMPLETED");

        ExtractableResponse<Response> future = authenticatedJsonRequest(caller)
                .body(Map.of("date", "2100-01-01"))
                .post(ORATORIOS)
                .then()
                .extract();
        assertThat(future.statusCode()).isEqualTo(201);
        UUID futureId = UUID.fromString(future.path("id"));
        trackOratorio(futureId);
        assertThat(future.<String>path("event.status")).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("REQ-ORATORIO-003 and ADR-0017 - concurrent duplicate dates -> one creation and one domain conflict")
    void concurrentDuplicateDatesShouldHaveOneWinner() throws Exception {
        AuthSession caller = sudoSession();
        LocalDate date = LocalDate.of(2031, 4, 5);
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> first = executor.submit(
                    () -> concurrentCreation(caller, date, ready, start)
            );
            Future<ExtractableResponse<Response>> second = executor.submit(
                    () -> concurrentCreation(caller, date, ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(201, 409);
            ExtractableResponse<Response> created = responses.stream()
                    .filter(candidate -> candidate.statusCode() == 201)
                    .findFirst()
                    .orElseThrow();
            UUID id = UUID.fromString(created.path("id"));
            trackOratorio(id);
            assertThat(activeOratorioCountFor(date)).isEqualTo(1);
            assertThat(activityCountForTarget(id)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-003 and REQ-ORATORIO-010 - soft deletion releases the local date")
    void softDeletionShouldReleaseLocalDate() {
        AuthSession caller = sudoSession();
        LocalDate date = LocalDate.of(2032, 5, 8);
        UUID firstId = createOratorio(caller, date);

        ExtractableResponse<Response> deletion = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Created for the wrong date  "))
                .delete(ORATORIOS + "/{id}", firstId)
                .then()
                .extract();
        assertThat(deletion.statusCode()).isEqualTo(204);

        ExtractableResponse<Response> replacement = authenticatedJsonRequest(caller)
                .body(Map.of("date", date.toString()))
                .post(ORATORIOS)
                .then()
                .extract();
        assertThat(replacement.statusCode()).isEqualTo(201);
        UUID replacementId = UUID.fromString(replacement.path("id"));
        trackOratorio(replacementId);
        assertThat(replacementId).isNotEqualTo(firstId);
        assertThat(activeOratorioCountFor(date)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPlanningCases")
    @DisplayName("REQ-ORATORIO-005 - invalid planning text -> HTTP 400 without mutation")
    void invalidPlanningTextShouldBeRejected(String scenario, Map<String, Object> payload) {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2033, 6, 12));
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .put(ORATORIOS + "/{id}/planning", id)
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(activityCountForTarget(id)).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-005 and REQ-ORATORIO-011 - full planning replacement normalizes blanks and no-ops")
    void fullPlanningReplacementShouldNormalizeBlanksAndNoOps() {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2033, 7, 10));
        Map<String, Object> payload = planningPayload(
                "  Bolo e suco  ",
                "  Circuito cooperativo  ",
                "   ",
                "  Serviço e amizade  "
        );
        clearActivities();

        ExtractableResponse<Response> first = authenticatedJsonRequest(caller)
                .body(payload)
                .put(ORATORIOS + "/{id}/planning", id)
                .then()
                .extract();

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.<String>path("planning.lancheDescription")).isEqualTo("Bolo e suco");
        assertThat(first.<String>path("planning.gincanaDescription")).isEqualTo("Circuito cooperativo");
        assertThat(first.<Object>path("planning.boaTardeCriancasPlan")).isNull();
        assertThat(first.<String>path("planning.boaTardeJovensPlan")).isEqualTo("Serviço e amizade");
        assertThat(activityCountForTarget(id)).isEqualTo(1);

        ExtractableResponse<Response> repeated = authenticatedJsonRequest(caller)
                .body(planningPayload(
                        "Bolo e suco", "Circuito cooperativo", null, "Serviço e amizade"
                ))
                .put(ORATORIOS + "/{id}/planning", id)
                .then()
                .extract();
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(activityCountForTarget(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIO-006 - team assignment is idempotent and preserves later inactive Member")
    void teamAssignmentShouldBeIdempotentAndPreserveInactiveMember() {
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, LocalDate.of(2034, 8, 19));
        UUID memberId = createActiveMember(caller, "Gincana team member");
        clearActivities();

        ExtractableResponse<Response> first = authenticatedJsonRequest(caller)
                .put(ORATORIOS + "/{id}/teams/GINCANA/members/{memberId}", oratorioId, memberId)
                .then()
                .extract();
        ExtractableResponse<Response> repeated = authenticatedJsonRequest(caller)
                .put(ORATORIOS + "/{id}/teams/GINCANA/members/{memberId}", oratorioId, memberId)
                .then()
                .extract();

        assertThat(first.statusCode()).isEqualTo(204);
        assertThat(repeated.statusCode()).isEqualTo(204);
        assertThat(activityCountForTarget(oratorioId)).isEqualTo(1);

        authenticatedJsonRequest(caller)
                .body(reasonPayload("No longer active"))
                .patch("/members/{memberId}/deactivate", memberId)
                .then()
                .statusCode(204);
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(ORATORIOS + "/{id}", oratorioId)
                .then()
                .statusCode(200)
                .extract();
        assertThat(detail.asString()).contains(memberId.toString(), "GINCANA", "INACTIVE");
    }

    @Test
    @DisplayName("REQ-ORATORIO-006, REQ-ORATORIO-009 and ADR-0017 - in-flight team mutation -> lifecycle cannot finalize first")
    void teamMutationAndFinalizationShouldSerializeOnOneOccurrenceBoundary() throws Exception {
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, LocalDate.of(2002, 9, 15));
        UUID memberId = createActiveMember(caller, "Serialized team member");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lockMember = blocker.prepareStatement(
                     "SELECT id FROM members WHERE id = ? FOR UPDATE"
             )) {
            blocker.setAutoCommit(false);
            lockMember.setObject(1, memberId);
            try (var ignored = lockMember.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> assignment = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .put(
                                    ORATORIOS + "/{id}/teams/GINCANA/members/{memberId}",
                                    oratorioId,
                                    memberId
                            )
                            .then()
                            .extract()
            );
            awaitWaitingQuery("INSERT INTO oratorio_team_assignments", 1);

            Future<ExtractableResponse<Response>> finalization = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .patch(ORATORIOS + "/{id}/finalize", oratorioId)
                            .then()
                            .extract()
            );
            boolean finalizedBeforeTeamMutationCouldCommit =
                    awaitEventStatus(oratorioId, "FINALIZED", 2, TimeUnit.SECONDS);

            blocker.commit();
            ExtractableResponse<Response> assignmentResponse =
                    assignment.get(10, TimeUnit.SECONDS);
            ExtractableResponse<Response> finalizationResponse =
                    finalization.get(10, TimeUnit.SECONDS);

            assertThat(finalizedBeforeTeamMutationCouldCommit)
                    .as("Finalization must wait behind the already in-flight team mutation")
                    .isFalse();
            assertThat(assignmentResponse.statusCode()).isEqualTo(204);
            assertThat(finalizationResponse.statusCode()).isEqualTo(204);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oratorio_team_assignments "
                            + "WHERE oratorio_id = ? AND member_id = ? AND team_type = 'GINCANA'",
                    Long.class,
                    oratorioId,
                    memberId
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-006/009 and ADR-0017 - in-flight team removal -> lifecycle waits on Event-first order")
    void teamRemovalAndFinalizationShouldSerializeOnOneOccurrenceBoundary() throws Exception {
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, LocalDate.of(2002, 9, 22));
        UUID memberId = createActiveMember(caller, "Serialized team removal member");
        authenticatedJsonRequest(caller)
                .put(
                        ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}",
                        oratorioId,
                        "LANCHE",
                        memberId
                )
                .then()
                .statusCode(204);
        long advisoryLock = 76_017_002L;
        String trigger = "test_block_oratorio_team_removal";
        String function = "test_block_oratorio_team_removal";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION " + function + "() RETURNS trigger "
                        + "LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF OLD.oratorio_id = '" + oratorioId + "'::uuid "
                        + "AND OLD.member_id = '" + memberId + "'::uuid THEN "
                        + "PERFORM pg_advisory_xact_lock(" + advisoryLock + "); "
                        + "END IF; RETURN OLD; END $$"
        );
        jdbcTemplate.execute(
                "CREATE TRIGGER " + trigger + " BEFORE DELETE ON oratorio_team_assignments "
                        + "FOR EACH ROW EXECUTE FUNCTION " + function + "()"
        );

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lock = blocker.prepareStatement("SELECT pg_advisory_lock(?)");
             PreparedStatement unlock = blocker.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            lock.setLong(1, advisoryLock);
            try (var ignored = lock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> removal = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .delete(
                                    ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}",
                                    oratorioId,
                                    "LANCHE",
                                    memberId
                            )
                            .then()
                            .extract()
            );
            awaitWaitingQuery("DELETE FROM oratorio_team_assignments", 1);

            Future<ExtractableResponse<Response>> finalization = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .patch(ORATORIOS + "/{id}/finalize", oratorioId)
                            .then()
                            .extract()
            );
            boolean finalizedBeforeRemovalCouldCommit =
                    awaitEventStatus(oratorioId, "FINALIZED", 2, TimeUnit.SECONDS);

            unlock.setLong(1, advisoryLock);
            try (var ignored = unlock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
                assertThat(ignored.getBoolean(1)).isTrue();
            }
            ExtractableResponse<Response> removalResponse = removal.get(10, TimeUnit.SECONDS);
            ExtractableResponse<Response> finalizationResponse =
                    finalization.get(10, TimeUnit.SECONDS);

            assertThat(finalizedBeforeRemovalCouldCommit)
                    .as("Finalization must wait behind the already in-flight team removal")
                    .isFalse();
            assertThat(removalResponse.statusCode())
                    .as(removalResponse.asString())
                    .isEqualTo(204);
            assertThat(finalizationResponse.statusCode())
                    .as(finalizationResponse.asString())
                    .isEqualTo(204);
            assertThat(teamAssignmentCount(oratorioId, memberId, "LANCHE")).isZero();
        } finally {
            executor.shutdownNow();
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + trigger + " ON oratorio_team_assignments");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-005, REQ-ORATORIO-009 and ADR-0017 - in-flight planning replacement -> lifecycle cannot finalize first")
    void planningAndFinalizationShouldSerializeOnOneOccurrenceBoundary() throws Exception {
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, LocalDate.of(2002, 10, 20));
        long advisoryLock = 76_017_001L;
        String trigger = "test_block_oratorio_planning_update";
        String function = "test_block_oratorio_planning_update";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION " + function + "() RETURNS trigger "
                        + "LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF NEW.id = '" + oratorioId + "'::uuid THEN "
                        + "PERFORM pg_advisory_xact_lock(" + advisoryLock + "); "
                        + "END IF; RETURN NEW; END $$"
        );
        jdbcTemplate.execute(
                "CREATE TRIGGER " + trigger + " BEFORE UPDATE ON oratorios "
                        + "FOR EACH ROW EXECUTE FUNCTION " + function + "()"
        );

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lock = blocker.prepareStatement("SELECT pg_advisory_lock(?)");
             PreparedStatement unlock = blocker.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            lock.setLong(1, advisoryLock);
            try (var ignored = lock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> planning = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(planningPayload("Bolo", "Circuito", null, null))
                            .put(ORATORIOS + "/{id}/planning", oratorioId)
                            .then()
                            .extract()
            );
            awaitWaitingQuery("UPDATE oratorios", 1);

            Future<ExtractableResponse<Response>> finalization = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .patch(ORATORIOS + "/{id}/finalize", oratorioId)
                            .then()
                            .extract()
            );
            boolean finalizedBeforePlanningCouldCommit =
                    awaitEventStatus(oratorioId, "FINALIZED", 2, TimeUnit.SECONDS);

            unlock.setLong(1, advisoryLock);
            try (var ignored = unlock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
                assertThat(ignored.getBoolean(1)).isTrue();
            }
            ExtractableResponse<Response> planningResponse = planning.get(10, TimeUnit.SECONDS);
            ExtractableResponse<Response> finalizationResponse =
                    finalization.get(10, TimeUnit.SECONDS);

            assertThat(finalizedBeforePlanningCouldCommit)
                    .as("Finalization must wait behind the already in-flight planning replacement")
                    .isFalse();
            assertThat(planningResponse.statusCode()).as(planningResponse.asString()).isEqualTo(200);
            assertThat(finalizationResponse.statusCode())
                    .as(finalizationResponse.asString())
                    .isEqualTo(204);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + trigger + " ON oratorios");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }

    @ParameterizedTest
    @MethodSource("teamTypes")
    @DisplayName("REQ-ORATORIO-006 and REQ-ORATORIO-012 - exact four-team catalog accepts assignment and idempotent removal")
    void exactTeamCatalogShouldSupportIdempotentAssignmentAndRemoval(String teamType) {
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, LocalDate.of(2035, 9, 22));
        UUID memberId = createActiveMember(caller, "Team catalog member " + teamType);

        authenticatedJsonRequest(caller)
                .put(ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}", oratorioId, teamType, memberId)
                .then()
                .statusCode(204);
        authenticatedJsonRequest(caller)
                .delete(ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}", oratorioId, teamType, memberId)
                .then()
                .statusCode(204);
        authenticatedJsonRequest(caller)
                .delete(ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}", oratorioId, teamType, memberId)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("REQ-ORATORIO-001 - generic Event mutations reject specialized Oratorio Event")
    void genericEventMutationShouldRejectSpecializedEvent() {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2036, 10, 18));
        clearActivities();
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("title", "Changed");
        replacement.put("description", "Changed");
        replacement.put("gamLocationId", jdbcTemplate.queryForObject(
                "SELECT gam_location_id FROM events WHERE id = ?", UUID.class, id
        ).toString());
        replacement.put("requiredPermissionId", permissionId("EVENT_GET_MEMBER").toString());
        replacement.put("beginDate", "2036-10-18T18:00:00Z");
        replacement.put("endDate", "2036-10-18T21:00:00Z");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(replacement)
                .put("/events/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(activityCountForTarget(id)).isZero();
        authenticatedJsonRequest(caller)
                .get(ORATORIOS + "/{id}", id)
                .then()
                .statusCode(200)
                .body("event.title", org.hamcrest.Matchers.equalTo("Oratório"));
    }

    @Test
    @DisplayName("REQ-ORATORIO-007 and REQ-ORATORIO-008 - Member reads plan but cannot mutate or see sensitive data")
    void memberShouldReadPlanWithoutMutationOrSensitiveData() {
        AuthSession setup = sudoSession();
        UUID id = createOratorio(setup, LocalDate.of(2037, 11, 14));
        AuthSession member = newSession("MEMBER");

        ExtractableResponse<Response> detail = authenticatedJsonRequest(member)
                .get(ORATORIOS + "/{id}", id)
                .then()
                .extract();

        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.asString())
                .doesNotContain(
                        "oratorianos", "attendance", "additionalForms", "health",
                        "signedAttachments", "cpf", "rg"
                );
        assertThat(authenticatedJsonRequest(member)
                .body(Map.of("date", "2037-11-15"))
                .post(ORATORIOS).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .body(planningPayload("Lanche", null, null, null))
                .put(ORATORIOS + "/{id}/planning", id).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("REQ-ORATORIO-007 - ORATORIO_GET without Event audience -> indistinguishable not found")
    void specializedReadShouldAlsoRequireTheBackingEventAudience() {
        AuthSession setup = sudoSession();
        UUID id = createOratorio(setup, LocalDate.of(2038, 1, 16));
        AuthSession specializedOnly = newSessionWithPermissions("ORATORIO_GET");
        AuthSession visible = newSessionWithPermissions("ORATORIO_GET", "EVENT_GET_MEMBER");

        ExtractableResponse<Response> inaccessible = authenticatedJsonRequest(specializedOnly)
                .get(ORATORIOS + "/{id}", id)
                .then()
                .extract();

        assertThat(inaccessible.statusCode()).isEqualTo(404);
        assertThat(inaccessible.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(authenticatedJsonRequest(visible)
                .get(ORATORIOS + "/{id}", id)
                .statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("REQ-ORATORIO-005/009 and ADR-0017 - planning locks Event first and rechecks finalized lifecycle")
    void planningShouldAcquireEventBeforeOratorioAndRejectLatestFinalizedState() throws Exception {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2003, 1, 18));
        clearActivities();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection lifecycle = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement finalizeEvent = lifecycle.prepareStatement(
                     "UPDATE events SET status = CAST('FINALIZED' AS event_status_enum) WHERE id = ?"
             )) {
            lifecycle.setAutoCommit(false);
            finalizeEvent.setObject(1, id);
            assertThat(finalizeEvent.executeUpdate()).isEqualTo(1);

            Future<ExtractableResponse<Response>> planning = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(planningPayload("Bolo", "Circuito", null, null))
                            .put(ORATORIOS + "/{id}/planning", id)
                            .then()
                            .extract()
            );

            boolean completedBeforeLifecycleCommit = awaitFutureCompletion(
                    planning,
                    1,
                    TimeUnit.SECONDS
            );
            Map<String, Object> planningBeforeCommit = jdbcTemplate.queryForMap(
                    "SELECT lanche_description, gincana_description FROM oratorios WHERE id = ?",
                    id
            );

            lifecycle.commit();
            ExtractableResponse<Response> response = planning.get(10, TimeUnit.SECONDS);

            assertThat(completedBeforeLifecycleCommit)
                    .as("Planning must wait at the already-held Event boundary before touching Oratorio")
                    .isFalse();
            assertThat(planningBeforeCommit)
                    .containsEntry("lanche_description", null)
                    .containsEntry("gincana_description", null);
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(409);
            assertThat(response.<String>path("code")).isEqualTo("ORATORIO_LIFECYCLE_CONFLICT");
            assertThat(jdbcTemplate.queryForMap(
                    "SELECT lanche_description, gincana_description FROM oratorios WHERE id = ?",
                    id
            )).containsEntry("lanche_description", null)
                    .containsEntry("gincana_description", null);
            assertThat(activityCountForTarget(id)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-006/009 and ADR-0017 - team assignment locks Event first and rechecks deletion")
    void teamAssignmentShouldAcquireEventBeforeOratorioAndRejectLatestDeletion() throws Exception {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2003, 2, 15));
        UUID memberId = createActiveMember(caller, "Deleted occurrence team target");
        clearActivities();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection deletion = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement deleteEvent = deletion.prepareStatement(
                     "UPDATE events SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?"
             )) {
            deletion.setAutoCommit(false);
            deleteEvent.setObject(1, id);
            assertThat(deleteEvent.executeUpdate()).isEqualTo(1);

            Future<ExtractableResponse<Response>> assignment = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .put(
                                    ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}",
                                    id,
                                    "GINCANA",
                                    memberId
                            )
                            .then()
                            .extract()
            );

            boolean completedBeforeDeletionCommit = awaitFutureCompletion(
                    assignment,
                    1,
                    TimeUnit.SECONDS
            );
            long assignmentsBeforeCommit = teamAssignmentCount(id, memberId, "GINCANA");

            deletion.commit();
            ExtractableResponse<Response> response = assignment.get(10, TimeUnit.SECONDS);

            assertThat(completedBeforeDeletionCommit)
                    .as("Team assignment must wait at the already-held Event boundary")
                    .isFalse();
            assertThat(assignmentsBeforeCommit).isZero();
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(404);
            assertThat(response.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
            assertThat(teamAssignmentCount(id, memberId, "GINCANA")).isZero();
            assertThat(activityCountForTarget(id)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-006/009 and ADR-0017 - team removal locks Event first and rechecks finalized lifecycle")
    void teamRemovalShouldAcquireEventBeforeOratorioAndRejectLatestFinalizedState() throws Exception {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2003, 3, 15));
        UUID memberId = createActiveMember(caller, "Finalized occurrence team target");
        authenticatedJsonRequest(caller)
                .put(
                        ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}",
                        id,
                        "LANCHE",
                        memberId
                )
                .then()
                .statusCode(204);
        clearActivities();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection lifecycle = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement finalizeEvent = lifecycle.prepareStatement(
                     "UPDATE events SET status = CAST('FINALIZED' AS event_status_enum) WHERE id = ?"
             )) {
            lifecycle.setAutoCommit(false);
            finalizeEvent.setObject(1, id);
            assertThat(finalizeEvent.executeUpdate()).isEqualTo(1);

            Future<ExtractableResponse<Response>> removal = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .delete(
                                    ORATORIOS + "/{id}/teams/{teamType}/members/{memberId}",
                                    id,
                                    "LANCHE",
                                    memberId
                            )
                            .then()
                            .extract()
            );

            boolean completedBeforeLifecycleCommit = awaitFutureCompletion(
                    removal,
                    1,
                    TimeUnit.SECONDS
            );
            long assignmentsBeforeCommit = teamAssignmentCount(id, memberId, "LANCHE");

            lifecycle.commit();
            ExtractableResponse<Response> response = removal.get(10, TimeUnit.SECONDS);

            assertThat(completedBeforeLifecycleCommit)
                    .as("Team removal must wait at the already-held Event boundary")
                    .isFalse();
            assertThat(assignmentsBeforeCommit).isEqualTo(1);
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(409);
            assertThat(response.<String>path("code")).isEqualTo("ORATORIO_LIFECYCLE_CONFLICT");
            assertThat(teamAssignmentCount(id, memberId, "LANCHE")).isEqualTo(1);
            assertThat(activityCountForTarget(id)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-009, REQ-ORATORIO-010 and ADR-0017 - deletion and finalization share one lock order")
    void deletionAndFinalizationShouldSerializeWithoutDatabaseDeadlock() throws Exception {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2004, 2, 14));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             var tableLock = blocker.createStatement()) {
            blocker.setAutoCommit(false);
            tableLock.execute("LOCK TABLE presences IN ACCESS EXCLUSIVE MODE");

            Future<ExtractableResponse<Response>> deletion = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(reasonPayload("Remove empty historical occurrence"))
                            .delete(ORATORIOS + "/{id}", id)
                            .then()
                            .extract()
            );
            awaitWaitingQuery("FROM presences", 1);

            Future<ExtractableResponse<Response>> finalization = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .patch(ORATORIOS + "/{id}/finalize", id)
                            .then()
                            .extract()
            );
            awaitAnyWaitingQuery(List.of("FROM events", "FROM oratorios"));

            blocker.commit();
            ExtractableResponse<Response> deletionResponse =
                    deletion.get(15, TimeUnit.SECONDS);
            ExtractableResponse<Response> finalizationResponse =
                    finalization.get(15, TimeUnit.SECONDS);

            assertThat(deletionResponse.statusCode())
                    .as(deletionResponse.asString())
                    .isEqualTo(204);
            assertThat(finalizationResponse.statusCode())
                    .as(finalizationResponse.asString())
                    .isEqualTo(404);
            assertThat(List.of(
                    deletionResponse.statusCode(),
                    finalizationResponse.statusCode()
            )).allSatisfy(status -> assertThat(status).isLessThan(500));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-009 and ADR-0017 - lifecycle transition and deletion use one occurrence lock order")
    void lifecycleTransitionAndDeletionShouldNotDeadlockAcrossOccurrenceBoundaries() throws Exception {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2004, 3, 13));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection eventBlocker = jdbcTemplate.getDataSource().getConnection();
             Connection oratorioBlocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lockEvent = eventBlocker.prepareStatement(
                     "SELECT id FROM events WHERE id = ? FOR UPDATE"
             );
             PreparedStatement lockOratorio = oratorioBlocker.prepareStatement(
                     "SELECT id FROM oratorios WHERE id = ? FOR UPDATE"
             )) {
            eventBlocker.setAutoCommit(false);
            oratorioBlocker.setAutoCommit(false);
            lockEvent.setObject(1, id);
            lockOratorio.setObject(1, id);
            try (var eventLock = lockEvent.executeQuery();
                 var oratorioLock = lockOratorio.executeQuery()) {
                assertThat(eventLock.next()).isTrue();
                assertThat(oratorioLock.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> finalization = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .patch(ORATORIOS + "/{id}/finalize", id)
                            .then()
                            .extract()
            );
            awaitAnyWaitingQuery(List.of("FROM events", "FROM oratorios"));

            Future<ExtractableResponse<Response>> deletion = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(reasonPayload("Remove empty historical occurrence"))
                            .delete(ORATORIOS + "/{id}", id)
                            .then()
                            .extract()
            );
            awaitOccurrenceBoundaryWaiters(2);

            oratorioBlocker.commit();
            awaitWaitingQuery("FROM events", 2);
            eventBlocker.commit();

            ExtractableResponse<Response> finalizationResponse =
                    finalization.get(15, TimeUnit.SECONDS);
            ExtractableResponse<Response> deletionResponse =
                    deletion.get(15, TimeUnit.SECONDS);

            assertThat(finalizationResponse.statusCode())
                    .as(finalizationResponse.asString())
                    .isEqualTo(204);
            assertThat(deletionResponse.statusCode())
                    .as(deletionResponse.asString())
                    .isEqualTo(409);
            assertThat(List.of(
                    finalizationResponse.statusCode(),
                    deletionResponse.statusCode()
            )).allSatisfy(status -> assertThat(status).isLessThan(500));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-009 and REQ-ORATORIO-010 - locked/finalized occurrence must reopen before deletion")
    void lockedAndFinalizedOccurrenceShouldRequireReopenBeforeDeletion() {
        AuthSession caller = sudoSession();
        UUID id = createOratorio(caller, LocalDate.of(2001, 12, 8));

        authenticatedJsonRequest(caller)
                .patch(ORATORIOS + "/{id}/lock", id)
                .then()
                .statusCode(204);
        assertThat(authenticatedJsonRequest(caller)
                .body(reasonPayload("Remove locked occurrence"))
                .delete(ORATORIOS + "/{id}", id).statusCode()).isEqualTo(409);

        authenticatedJsonRequest(caller)
                .body(Map.of("targetStatus", "COMPLETED", "reason", "Correction required"))
                .patch(ORATORIOS + "/{id}/reopen", id)
                .then()
                .statusCode(204);
        authenticatedJsonRequest(caller)
                .patch(ORATORIOS + "/{id}/finalize", id)
                .then()
                .statusCode(204);
        assertThat(authenticatedJsonRequest(caller)
                .body(reasonPayload("Remove finalized occurrence"))
                .delete(ORATORIOS + "/{id}", id).statusCode()).isEqualTo(409);
    }

    private ExtractableResponse<Response> concurrentCreation(
            AuthSession caller,
            LocalDate date,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return authenticatedJsonRequest(caller)
                .body(Map.of("date", date.toString()))
                .post(ORATORIOS)
                .then()
                .extract();
    }

    private long activeOratorioCountFor(LocalDate date) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratorios o JOIN events e ON e.id = o.id "
                        + "WHERE e.deleted_at IS NULL "
                        + "AND (e.begin_date AT TIME ZONE 'America/Sao_Paulo')::date = ?",
                Long.class,
                date
        );
    }

    private long teamAssignmentCount(UUID oratorioId, UUID memberId, String teamType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratorio_team_assignments "
                        + "WHERE oratorio_id = ? AND member_id = ? "
                        + "AND team_type = CAST(? AS oratorio_team_type_enum)",
                Long.class,
                oratorioId,
                memberId,
                teamType
        );
    }

    private void awaitWaitingQuery(String queryFragment, int expectedCount)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity "
                            + "WHERE datname = current_database() "
                            + "AND wait_event_type = 'Lock' AND query ILIKE ?",
                    Long.class,
                    "%" + queryFragment + "%"
            );
            if (count != null && count >= expectedCount) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for blocked query: " + queryFragment);
    }

    private void awaitOccurrenceBoundaryWaiters(int expectedCount)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity "
                            + "WHERE datname = current_database() "
                            + "AND wait_event_type = 'Lock' "
                            + "AND (query ILIKE '%FROM events%' OR query ILIKE '%FROM oratorios%')",
                    Long.class
            );
            if (count != null && count >= expectedCount) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for occurrence boundary waiters: " + expectedCount);
    }

    private boolean awaitFutureCompletion(
            Future<?> future,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                return true;
            }
            Thread.sleep(25);
        }
        return future.isDone();
    }

    private String awaitAnyWaitingQuery(List<String> queryFragments)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            for (String queryFragment : queryFragments) {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_stat_activity "
                                + "WHERE datname = current_database() "
                                + "AND wait_event_type = 'Lock' AND query ILIKE ?",
                        Long.class,
                        "%" + queryFragment + "%"
                );
                if (count != null && count > 0) {
                    return queryFragment;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for one of: " + queryFragments);
    }

    private boolean awaitEventStatus(
            UUID oratorioId,
            String expectedStatus,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status::text FROM events WHERE id = ?",
                    String.class,
                    oratorioId
            );
            if (expectedStatus.equals(status)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static Map<String, Object> planningPayload(
            String lanche,
            String gincana,
            String boaTardeCriancas,
            String boaTardeJovens
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lancheDescription", lanche);
        payload.put("gincanaDescription", gincana);
        payload.put("boaTardeCriancasPlan", boaTardeCriancas);
        payload.put("boaTardeJovensPlan", boaTardeJovens);
        return payload;
    }

    private static Stream<Arguments> invalidPlanningCases() {
        return Stream.of(
                Arguments.of("Lanche above 10,000 characters",
                        planningPayload("x".repeat(10_001), null, null, null)),
                Arguments.of("Gincana above 10,000 characters",
                        planningPayload(null, "x".repeat(10_001), null, null)),
                Arguments.of("children plan above 10,000 characters",
                        planningPayload(null, null, "x".repeat(10_001), null)),
                Arguments.of("youth plan above 10,000 characters",
                        planningPayload(null, null, null, "x".repeat(10_001)))
        );
    }

    private static Stream<String> teamTypes() {
        return Stream.of("LANCHE", "GINCANA", "BOA_TARDE_CRIANCAS", "BOA_TARDE_JOVENS");
    }
}
