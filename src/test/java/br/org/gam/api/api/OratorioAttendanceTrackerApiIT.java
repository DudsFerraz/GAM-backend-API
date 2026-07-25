package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Oratorio attendance tracker")
class OratorioAttendanceTrackerApiIT extends OratorioModuleApiTestSupport {

    private static final LocalDate OCCURRENCE_DATE = LocalDate.of(2026, 7, 25);

    @Test
    @DisplayName("REQ-ORATORIO-ATT-004 - 13:30 inclusive early boundary applies to both tracker sections")
    void earlyBoundaryShouldBeInclusiveForMembersAndOratorianos() {
        setCurrentInstant(Instant.parse("2026-07-25T12:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID memberId = createActiveMember(caller, "Early-boundary Member");
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        clearActivities();

        setCurrentInstant(Instant.parse("2026-07-25T16:29:59Z"));
        assertThat(markMember(caller, oratorioId, memberId).statusCode()).isEqualTo(409);
        assertThat(markOratoriano(caller, oratorioId, oratorianoId).statusCode()).isEqualTo(409);
        assertThat(activityLogCount()).isZero();

        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        ExtractableResponse<Response> member = markMember(caller, oratorioId, memberId);
        ExtractableResponse<Response> oratoriano = markOratoriano(caller, oratorioId, oratorianoId);

        assertThat(member.statusCode()).isEqualTo(201);
        assertThat(oratoriano.statusCode()).isEqualTo(201);
        assertUuidV7(UUID.fromString(member.path("id")));
        assertUuidV7(UUID.fromString(oratoriano.path("id")));
        assertThat(activityLogCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-005 and REQ-ORATORIO-ATT-008 - repeated checks and unchecks are activity-free no-ops")
    void repeatedChecksAndUnchecksShouldBeIdempotent() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID memberId = createActiveMember(caller, "Idempotent tracker Member");
        UUID oratorianoId = createOratoriano(caller, "Ana", "Souza");
        clearActivities();

        ExtractableResponse<Response> memberCreated = markMember(caller, oratorioId, memberId);
        ExtractableResponse<Response> memberRepeated = markMember(caller, oratorioId, memberId);
        ExtractableResponse<Response> oratorianoCreated = markOratoriano(caller, oratorioId, oratorianoId);
        ExtractableResponse<Response> oratorianoRepeated = markOratoriano(caller, oratorioId, oratorianoId);

        assertThat(memberCreated.statusCode()).isEqualTo(201);
        assertThat(memberRepeated.statusCode()).isEqualTo(200);
        assertThat(memberRepeated.<String>path("id")).isEqualTo(memberCreated.<String>path("id"));
        assertThat(oratorianoCreated.statusCode()).isEqualTo(201);
        assertThat(oratorianoRepeated.statusCode()).isEqualTo(200);
        assertThat(oratorianoRepeated.<String>path("id")).isEqualTo(oratorianoCreated.<String>path("id"));
        assertThat(activityLogCount()).isEqualTo(2);

        assertThat(uncheckMember(caller, oratorioId, memberId, null).statusCode()).isEqualTo(204);
        assertThat(uncheckMember(caller, oratorioId, memberId, null).statusCode()).isEqualTo(204);
        assertThat(uncheckOratoriano(caller, oratorioId, oratorianoId, null).statusCode()).isEqualTo(204);
        assertThat(uncheckOratoriano(caller, oratorioId, oratorianoId, null).statusCode()).isEqualTo(204);
        assertThat(activityLogCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-006 - COMPLETED uncheck requires and audits a normalized reason")
    void completedUncheckShouldRequireAndAuditReason() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID oratorianoId = createOratoriano(caller, "Paulo", "Mendes");
        ExtractableResponse<Response> marked = markOratoriano(caller, oratorioId, oratorianoId);
        assertThat(marked.statusCode()).isEqualTo(201);
        clearActivities();

        assertThat(uncheckOratoriano(caller, oratorioId, oratorianoId, null).statusCode()).isEqualTo(400);
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> corrected = uncheckOratoriano(
                caller,
                oratorioId,
                oratorianoId,
                "  Marked the wrong person  "
        );
        assertThat(corrected.statusCode()).isEqualTo(204);
        assertThat(activityLogCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM activity_logs",
                String.class
        )).isEqualTo("Marked the wrong person");
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-004 and REQ-ORATORIO-ATT-006 - CANCELLED rejects additions but permits reason-free correction")
    void cancelledOccurrenceShouldRejectAdditionAndPermitRemoval() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID existingId = createOratoriano(caller, "Carlos", "Lima");
        UUID newId = createOratoriano(caller, "Marina", "Sousa");
        assertThat(markOratoriano(caller, oratorioId, existingId).statusCode()).isEqualTo(201);
        authenticatedJsonRequest(caller)
                .body(reasonPayload("Weather emergency"))
                .patch("/oratorios/{id}/cancel", oratorioId)
                .then()
                .statusCode(204);
        clearActivities();

        assertThat(markOratoriano(caller, oratorioId, newId).statusCode()).isEqualTo(409);
        assertThat(uncheckOratoriano(caller, oratorioId, existingId, null).statusCode()).isEqualTo(204);
        assertThat(activityLogCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-004 - LOCKED and FINALIZED reject every attendance mutation")
    void lockedAndFinalizedOccurrencesShouldRejectEveryAttendanceMutation() {
        setCurrentInstant(Instant.parse("2026-07-25T21:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID existingId = createOratoriano(caller, "Lia", "Davila");
        UUID newId = createOratoriano(caller, "Pedro", "Alves");
        assertThat(markOratoriano(caller, oratorioId, existingId).statusCode()).isEqualTo(201);
        authenticatedJsonRequest(caller)
                .patch("/oratorios/{id}/lock", oratorioId)
                .then()
                .statusCode(204);
        clearActivities();

        assertThat(markOratoriano(caller, oratorioId, newId).statusCode()).isEqualTo(409);
        assertThat(uncheckOratoriano(caller, oratorioId, existingId, "Correction").statusCode())
                .isEqualTo(409);
        assertThat(activityLogCount()).isZero();

        authenticatedJsonRequest(caller)
                .patch("/oratorios/{id}/finalize", oratorioId)
                .then()
                .statusCode(204);
        clearActivities();
        assertThat(markOratoriano(caller, oratorioId, newId).statusCode()).isEqualTo(409);
        assertThat(uncheckOratoriano(caller, oratorioId, existingId, "Correction").statusCode())
                .isEqualTo(409);
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-007 and REQ-ORATORIO-ATT-008 - quick registration creates one person and attendance atomically")
    void quickRegistrationShouldCreatePersonAndAttendanceAtomically() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload("Erik", "Garcia"))
                .post("/oratorios/{id}/attendance/oratorianos/register-and-mark", oratorioId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID oratorianoId = UUID.fromString(response.path("oratoriano.id"));
        UUID attendanceId = UUID.fromString(response.path("attendance.id"));
        trackOratoriano(oratorianoId);
        assertUuidV7(oratorianoId);
        assertUuidV7(attendanceId);
        assertThat(activityLogCount()).isEqualTo(1);
        assertThat(oratorianoAttendanceCount(oratorioId, oratorianoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-007 - exact existing or reserved name never marks attendance automatically")
    void existingOrReservedNameShouldNeverBeMarkedAutomatically() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID existingId = createOratoriano(caller, "João", "Silva");
        clearActivities();

        ExtractableResponse<Response> existing = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload("JOAO", "SILVA"))
                .post("/oratorios/{id}/attendance/oratorianos/register-and-mark", oratorioId)
                .then()
                .extract();
        assertThat(existing.statusCode()).isEqualTo(409);
        assertThat(oratorianoAttendanceCount(oratorioId, existingId)).isZero();
        assertThat(activityLogCount()).isZero();

        authenticatedJsonRequest(caller)
                .body(reasonPayload("Duplicate record"))
                .delete("/oratorianos/{id}", existingId)
                .then()
                .statusCode(204);
        clearActivities();
        ExtractableResponse<Response> reserved = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload("João", "Silva"))
                .post("/oratorios/{id}/attendance/oratorianos/register-and-mark", oratorioId)
                .then()
                .extract();
        assertThat(reserved.statusCode()).isEqualTo(409);
        assertThat(oratorianoAttendanceCount(oratorioId, existingId)).isZero();
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-003 and REQ-ORATORIO-ATT-011 - present summary survives roster page and search changes")
    void presentSummaryShouldSurviveRosterNavigation() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID memberId = createActiveMember(caller, "Present-summary Member");
        UUID oratorianoId = createOratoriano(caller, "Ana", "Souza");
        assertThat(markMember(caller, oratorioId, memberId).statusCode()).isEqualTo(201);
        assertThat(markOratoriano(caller, oratorioId, oratorianoId).statusCode()).isEqualTo(201);

        ExtractableResponse<Response> memberPage = authenticatedJsonRequest(caller)
                .queryParam("page", 0)
                .queryParam("name", "does not match")
                .get("/oratorios/{id}/attendance/members", oratorioId)
                .then()
                .extract();
        ExtractableResponse<Response> oratorianoPage = authenticatedJsonRequest(caller)
                .queryParam("page", 1)
                .queryParam("name", "does not match")
                .get("/oratorios/{id}/attendance/oratorianos", oratorioId)
                .then()
                .extract();
        ExtractableResponse<Response> summary = authenticatedJsonRequest(caller)
                .get("/oratorios/{id}/attendance/present", oratorioId)
                .then()
                .extract();

        assertThat(memberPage.statusCode()).isEqualTo(200);
        assertThat(oratorianoPage.statusCode()).isEqualTo(200);
        assertThat(memberPage.<Integer>path("size")).isEqualTo(50);
        assertThat(oratorianoPage.<Integer>path("size")).isEqualTo(50);
        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(summary.<List<String>>path("members.person.id")).contains(memberId.toString());
        assertThat(summary.<List<String>>path("oratorianos.person.id")).contains(oratorianoId.toString());
        assertThat(summary.asString()).doesNotContain("forms", "health", "family", "attachments");
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-003 and REQ-ORATORIO-ATT-010 - deleted attendee remains historical but cannot receive new attendance")
    void deletedAttendeeShouldRemainHistoricalAndIneligible() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID oratorianoId = createOratoriano(caller, "Paulo", "Mendes");
        assertThat(markOratoriano(caller, oratorioId, oratorianoId).statusCode()).isEqualTo(201);
        authenticatedJsonRequest(caller)
                .body(reasonPayload("Erroneous identity record"))
                .delete("/oratorianos/{id}", oratorianoId)
                .then()
                .statusCode(204);

        ExtractableResponse<Response> summary = authenticatedJsonRequest(caller)
                .get("/oratorios/{id}/attendance/present", oratorioId)
                .then()
                .extract();

        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(summary.asString()).contains(oratorianoId.toString(), "deleted");
        assertThat(markOratoriano(caller, oratorioId, oratorianoId).statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-002 - tracker permissions are distinct from ordinary Event Presence visibility")
    void trackerPermissionsShouldBeDistinctFromCommonPresenceVisibility() {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession setup = sudoSession();
        UUID oratorioId = createOratorio(setup, OCCURRENCE_DATE);
        UUID memberId = createActiveMember(setup, "Permission-boundary Member");
        AuthSession member = newSession("MEMBER");

        assertThat(jsonRequest()
                .get("/oratorios/{id}/attendance/present", oratorioId)
                .statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(member)
                .get("/oratorios/{id}/attendance/members", oratorioId)
                .statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .get("/oratorios/{id}/attendance/oratorianos", oratorioId)
                .statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .put("/oratorios/{id}/attendance/members/{memberId}", oratorioId, memberId)
                .statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-009 and ADR-0017 - concurrent checks leave one active attendance")
    void concurrentChecksShouldLeaveOneActiveAttendance() throws Exception {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID oratorianoId = createOratoriano(caller, "Carlos", "Lima");
        clearActivities();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ExtractableResponse<Response>> first = executor.submit(
                    () -> concurrentCheck(caller, oratorioId, oratorianoId, ready, start)
            );
            Future<ExtractableResponse<Response>> second = executor.submit(
                    () -> concurrentCheck(caller, oratorioId, oratorianoId, ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS).statusCode(),
                    second.get(20, TimeUnit.SECONDS).statusCode()
            )).containsExactlyInAnyOrder(201, 200);
            assertThat(oratorianoAttendanceCount(oratorioId, oratorianoId)).isEqualTo(1);
            assertThat(activityLogCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIO-ATT-009 and ADR-0017 - in-flight attendance locks Oratoriano deletion")
    void attendanceAndOratorianoDeletionShouldSerializeOnTheOratorianoBoundary() throws Exception {
        setCurrentInstant(Instant.parse("2026-07-25T16:30:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorioId = createOratorio(caller, OCCURRENCE_DATE);
        UUID oratorianoId = createOratoriano(caller, "Serialized", "Attendee");
        long advisoryLock = 76_017_002L;
        String trigger = "test_block_oratoriano_attendance_insert";
        String function = "test_block_oratoriano_attendance_insert";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION " + function + "() RETURNS trigger "
                        + "LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF NEW.oratoriano_id = '" + oratorianoId + "'::uuid THEN "
                        + "PERFORM pg_advisory_xact_lock(" + advisoryLock + "); "
                        + "END IF; RETURN NEW; END $$"
        );
        jdbcTemplate.execute(
                "CREATE TRIGGER " + trigger + " BEFORE INSERT ON oratoriano_attendances "
                        + "FOR EACH ROW EXECUTE FUNCTION " + function + "()"
        );

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lock = blocker.prepareStatement("SELECT pg_advisory_lock(?)");
             PreparedStatement unlock = blocker.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            lock.setLong(1, advisoryLock);
            try (var ignored = lock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> attendance = executor.submit(
                    () -> markOratoriano(caller, oratorioId, oratorianoId)
            );
            awaitWaitingQuery("INSERT INTO oratoriano_attendances", 1);

            Future<ExtractableResponse<Response>> deletion = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(reasonPayload("Erroneous identity record"))
                            .delete("/oratorianos/{id}", oratorianoId)
                            .then()
                            .extract()
            );
            boolean deletedBeforeAttendanceCouldCommit =
                    awaitFutureCompletion(deletion, 2, TimeUnit.SECONDS);

            unlock.setLong(1, advisoryLock);
            try (var ignored = unlock.executeQuery()) {
                assertThat(ignored.next()).isTrue();
                assertThat(ignored.getBoolean(1)).isTrue();
            }
            ExtractableResponse<Response> attendanceResponse =
                    attendance.get(10, TimeUnit.SECONDS);
            ExtractableResponse<Response> deletionResponse =
                    deletion.get(10, TimeUnit.SECONDS);

            assertThat(deletedBeforeAttendanceCouldCommit)
                    .as("Deletion must wait behind attendance after attendance selected the Oratoriano")
                    .isFalse();
            assertThat(attendanceResponse.statusCode())
                    .as(attendanceResponse.asString())
                    .isEqualTo(201);
            assertThat(deletionResponse.statusCode())
                    .as(deletionResponse.asString())
                    .isEqualTo(204);
            assertThat(oratorianoAttendanceCount(oratorioId, oratorianoId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + trigger + " ON oratoriano_attendances");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }

    private ExtractableResponse<Response> markMember(AuthSession caller, UUID oratorioId, UUID memberId) {
        return authenticatedJsonRequest(caller)
                .put("/oratorios/{id}/attendance/members/{memberId}", oratorioId, memberId)
                .then()
                .extract();
    }

    private ExtractableResponse<Response> markOratoriano(
            AuthSession caller,
            UUID oratorioId,
            UUID oratorianoId
    ) {
        return authenticatedJsonRequest(caller)
                .put("/oratorios/{id}/attendance/oratorianos/{oratorianoId}", oratorioId, oratorianoId)
                .then()
                .extract();
    }

    private ExtractableResponse<Response> uncheckMember(
            AuthSession caller,
            UUID oratorioId,
            UUID memberId,
            String reason
    ) {
        return authenticatedJsonRequest(caller)
                .body(reason == null ? Map.of() : Map.of("reason", reason))
                .delete("/oratorios/{id}/attendance/members/{memberId}", oratorioId, memberId)
                .then()
                .extract();
    }

    private ExtractableResponse<Response> uncheckOratoriano(
            AuthSession caller,
            UUID oratorioId,
            UUID oratorianoId,
            String reason
    ) {
        return authenticatedJsonRequest(caller)
                .body(reason == null ? Map.of() : Map.of("reason", reason))
                .delete("/oratorios/{id}/attendance/oratorianos/{oratorianoId}", oratorioId, oratorianoId)
                .then()
                .extract();
    }

    private ExtractableResponse<Response> concurrentCheck(
            AuthSession caller,
            UUID oratorioId,
            UUID oratorianoId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return markOratoriano(caller, oratorioId, oratorianoId);
    }

    private long activityLogCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_logs", Long.class);
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

    private long oratorianoAttendanceCount(UUID oratorioId, UUID oratorianoId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratoriano_attendances "
                        + "WHERE oratorio_id = ? AND oratoriano_id = ? AND deleted_at IS NULL",
                Long.class,
                oratorioId,
                oratorianoId
        );
    }
}
