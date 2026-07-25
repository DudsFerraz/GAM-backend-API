package br.org.gam.api.api;

import br.org.gam.api.presence.application.useCases.registerPresence.PresenceConflictResolver;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.SQLException;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Member Event Presences")
class MemberEventPresencesApiIT extends MemberApiTestSupport {

    private static final String EVENTS = "/events";
    private static final String VALID_REMOVAL_REASON = "  Correcting mistaken attendance  ";
    private static final int EVENT_LOCK_GATE = 12_012_015;
    private static final Set<String> PRESENCE_FIELDS = Set.of(
            "id", "member", "event", "observations", "registeredAt"
    );
    private static final Set<String> PRESENCE_MEMBER_FIELDS = Set.of(
            "id", "firstName", "surname", "status"
    );
    private static final Set<String> PRESENCE_EVENT_FIELDS = Set.of(
            "id", "title", "beginDate", "endDate", "type", "status"
    );

    private final List<UUID> memberIds = new ArrayList<>();
    private int phoneSequence = 10_000_000;

    @Autowired
    private PresenceConflictResolver presenceConflictResolver;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanupPresenceFixtures() {
        removeEventLockGate();
        for (UUID memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM presences WHERE member_id = ?", memberId);
        }
        memberIds.clear();
    }

    @Test
    @DisplayName("REQ-PRESENCE-001, REQ-PRESENCE-003 and REQ-PRESENCE-006 - inactive Member after Event start -> confirmed attendance")
    void inactiveMemberShouldRemainEligibleForConfirmedAttendance() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createEvent(
                caller,
                "Inactive Member attendance",
                null,
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );
        UUID memberId = createMember(caller, "Ines", "Almeida");
        jdbcTemplate.update(
                "UPDATE members SET status = CAST('INACTIVE' AS member_status_enum) WHERE id = ?",
                memberId
        );
        clearActivities();

        ExtractableResponse<Response> response = registerPresence(
                caller,
                eventId,
                memberId,
                "  Confirmed at reception  "
        );

        assertThat(response.statusCode()).isEqualTo(201);
        UUID presenceId = UUID.fromString(response.path("id"));
        assertUuidV7(presenceId);
        assertCompactPresence(response.jsonPath().getMap("$"), presenceId, memberId, eventId);
        assertThat(response.<String>path("member.status")).isEqualTo("INACTIVE");
        assertThat(response.<String>path("observations")).isEqualTo("Confirmed at reception");
        assertPublicApiLocation(response, EVENTS + "/" + eventId + "/presences/" + memberId);
        assertThat(activePresenceCount(eventId, memberId)).isEqualTo(1);
        assertThat(activityCountFor("PRESENCE_REGISTERED", presenceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-003 - registration before beginDate -> intent-specific conflict without Presence or activity")
    void registrationBeforeEventBeginsShouldBeRejectedWithoutMutation() {
        AuthSession caller = newSession("SUDO");
        Instant beginDate = Instant.now().plusSeconds(3_600);
        UUID eventId = createEvent(
                caller,
                "Future attendance",
                null,
                beginDate,
                beginDate.plusSeconds(3_600)
        );
        UUID memberId = createMember(caller, "Bento", "Moura");
        clearActivities();

        ExtractableResponse<Response> response = registerPresence(caller, eventId, memberId, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("PRESENCE_REGISTRATION_NOT_ALLOWED");
        assertThat(response.<String>path("details.eventId")).isEqualTo(eventId.toString());
        assertThat(response.<String>path("details.status")).isEqualTo("SCHEDULED");
        assertThat(response.<String>path("details.beginDate"))
                .isEqualTo(storedEventBeginDate(eventId).toString());
        assertThat(response.<String>path("details.evaluationInstant")).isNotBlank();
        assertThat(activePresenceCount(eventId, memberId)).isZero();
        assertThat(activityCount("PRESENCE_REGISTERED")).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-007 and REQ-PRESENCE-008 - visible active pair lookup -> compact Presence representation")
    void pairLookupShouldReturnTheCompleteCompactPresence() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Pair lookup");
        UUID memberId = createMember(caller, "Caio", "Ferreira");
        ExtractableResponse<Response> registration = registerPresence(
                caller,
                eventId,
                memberId,
                "Checked in"
        );
        UUID presenceId = UUID.fromString(registration.path("id"));

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertCompactPresence(response.jsonPath().getMap("$"), presenceId, memberId, eventId);
        assertThat(response.<String>path("observations")).isEqualTo("Checked in");
    }

    @Test
    @DisplayName("REQ-PRESENCE-008 and REQ-PRESENCE-009 - roster -> active-only name filtering, deterministic order, and inactive Members included")
    void eventRosterShouldFilterAndOrderActivePresencesWithoutHidingInactiveMembers() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Ordered roster");
        UUID brunoId = createMember(caller, "Bruno", "Alves");
        UUID anaId = createMember(caller, "Ana", "Silva");
        UUID carlaId = createMember(caller, "Carla", "Mendes");
        jdbcTemplate.update(
                "UPDATE members SET status = CAST('INACTIVE' AS member_status_enum) WHERE id = ?",
                brunoId
        );
        registerPresence(caller, eventId, carlaId, null);
        registerPresence(caller, eventId, brunoId, null);
        registerPresence(caller, eventId, anaId, null);

        ExtractableResponse<Response> roster = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?size=100", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> filtered = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?name=  BRu  &size=100", eventId)
                .then()
                .extract();

        assertThat(roster.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = roster.jsonPath().getList("items");
        assertThat(items).extracting(item -> nestedString(item, "member", "firstName"))
                .containsExactly("Ana", "Bruno", "Carla");
        assertThat(items).allSatisfy(item -> assertCompactPresence(
                item,
                UUID.fromString((String) item.get("id")),
                UUID.fromString(nestedString(item, "member", "id")),
                eventId
        ));
        Map<String, Object> inactivePresence = items.stream()
                .filter(item -> brunoId.toString().equals(nestedString(item, "member", "id")))
                .findFirst()
                .orElseThrow();
        assertThat(nestedString(inactivePresence, "member", "status")).isEqualTo("INACTIVE");

        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.<List<String>>path("items.member.id")).containsExactly(brunoId.toString());
    }

    @Test
    @DisplayName("REQ-MEMBER-015 and REQ-PRESENCE-010 - linked Account history -> all audiences, active-only, newest Event first")
    void linkedMemberHistoryShouldIgnoreEventAudienceAndUseDocumentedOrdering() {
        AuthSession setup = newSession("SUDO");
        String linkedEmail = "linked-presence-" + UUID.randomUUID() + "@example.com";
        UUID linkedAccountId = newAccount(linkedEmail, "Linked Presence Account");
        UUID memberId = registerMember(setup, linkedAccountId);
        memberIds.add(memberId);
        ExtractableResponse<Response> linkedLogin = login(linkedEmail, TEST_PASSWORD);
        AuthSession linkedAccount = new AuthSession(
                linkedAccountId,
                linkedEmail,
                TEST_PASSWORD,
                linkedLogin.path("token"),
                linkedLogin.cookie("refreshToken")
        );
        UUID olderEvent = createEvent(
                setup,
                "Older restricted Event",
                permissionId("EVENT_GET_COORD"),
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T12:00:00Z")
        );
        UUID newerEvent = createEvent(
                setup,
                "Newer restricted Event",
                permissionId("EVENT_GET_MEMBER"),
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z")
        );
        registerPresence(setup, olderEvent, memberId, "Older");
        registerPresence(setup, newerEvent, memberId, "Newer");

        ExtractableResponse<Response> response = authenticatedJsonRequest(linkedAccount)
                .get("/members/{memberId}/presences?size=100", memberId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = response.jsonPath().getList("items");
        assertThat(items).extracting(item -> nestedString(item, "event", "id"))
                .containsExactly(newerEvent.toString(), olderEvent.toString());
        assertThat(items).allSatisfy(item -> assertCompactPresence(
                item,
                UUID.fromString((String) item.get("id")),
                memberId,
                UUID.fromString(nestedString(item, "event", "id"))
        ));
    }

    @Test
    @DisplayName("REQ-PRESENCE-011 and REQ-PRESENCE-012 - changed observation on CANCELLED Event -> normalized response and one activity")
    void changedObservationShouldUpdateCancelledEventPresenceAndAuditOnce() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Cancelled observation edit");
        UUID memberId = createMember(caller, "Davi", "Nunes");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Before").path("id")
        );
        jdbcTemplate.update(
                "UPDATE events SET status = CAST('CANCELLED' AS event_status_enum) WHERE id = ?",
                eventId
        );
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "  After correction  "))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertCompactPresence(response.jsonPath().getMap("$"), presenceId, memberId, eventId);
        assertThat(response.<String>path("observations")).isEqualTo("After correction");
        assertThat(storedObservations(presenceId)).isEqualTo("After correction");
        Map<String, Object> activity = activityFor("PRESENCE_UPDATED", presenceId);
        assertThat(activity).containsEntry("reason", null);
        assertThat(activity.get("metadata").toString())
                .contains(
                        memberId.toString(),
                        eventId.toString(),
                        "Before",
                        "After correction"
                );
        assertThat(activityCountFor("PRESENCE_UPDATED", presenceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-012 - normalized observation no-op -> unchanged row and no activity")
    void normalizedObservationNoOpShouldNotPersistOrAudit() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Observation no-op");
        UUID memberId = createMember(caller, "Eva", "Ramos");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Arrived late").path("id")
        );
        Timestamp updatedAt = presenceUpdatedAt(presenceId);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "  Arrived late  "))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("observations")).isEqualTo("Arrived late");
        assertThat(presenceUpdatedAt(presenceId)).isEqualTo(updatedAt);
        assertThat(activityCountFor("PRESENCE_UPDATED", presenceId)).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-011 - omitted observations or an extra mutable field -> HTTP 400 without mutation")
    void invalidObservationEditShapeShouldBeRejectedWithoutMutation() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Invalid observation edit");
        UUID memberId = createMember(caller, "Fabio", "Costa");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Original").path("id")
        );
        clearActivities();

        ExtractableResponse<Response> omitted = authenticatedJsonRequest(caller)
                .body(Map.of())
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        ExtractableResponse<Response> extraField = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "Changed", "memberId", UUID.randomUUID().toString()))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(omitted.statusCode()).isEqualTo(400);
        assertThat(extraField.statusCode()).isEqualTo(400);
        assertThat(storedObservations(presenceId)).isEqualTo("Original");
        assertThat(activityCountFor("PRESENCE_UPDATED", presenceId)).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-013 and REQ-PRESENCE-014 - removal -> hidden active record, audited reason, and re-registration with new UUID")
    void removalShouldReleaseActiveIdentityAndPreserveRemovedHistory() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Presence correction");
        UUID memberId = createMember(caller, "Giulia", "Lima");
        UUID removedPresenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Final observation").path("id")
        );
        clearActivities();

        ExtractableResponse<Response> removal = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(removal.statusCode()).isEqualTo(204);
        assertThat(presenceIsRemoved(removedPresenceId)).isTrue();
        assertThat(activePresenceCount(eventId, memberId)).isZero();
        Map<String, Object> activity = activityFor("PRESENCE_REMOVED", removedPresenceId);
        assertThat(activity).containsEntry("reason", VALID_REMOVAL_REASON.trim());
        assertThat(activity.get("metadata").toString())
                .contains(memberId.toString(), eventId.toString(), "Final observation");

        ExtractableResponse<Response> lookup = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        assertPresenceNotFound(lookup, eventId, memberId);
        assertThat(authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?size=100", eventId)
                .then()
                .extract()
                .<List<String>>path("items.id")).doesNotContain(removedPresenceId.toString());
        assertThat(authenticatedJsonRequest(caller)
                .get("/members/{memberId}/presences?size=100", memberId)
                .then()
                .extract()
                .<List<String>>path("items.id")).doesNotContain(removedPresenceId.toString());

        ExtractableResponse<Response> registration = registerPresence(
                caller,
                eventId,
                memberId,
                "Registered again"
        );
        assertThat(registration.statusCode()).isEqualTo(201);
        UUID replacementPresenceId = UUID.fromString(registration.path("id"));
        assertThat(replacementPresenceId).isNotEqualTo(removedPresenceId);
        assertThat(activePresenceCount(eventId, memberId)).isEqualTo(1);
        assertThat(totalPresenceCount(eventId, memberId)).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRemovalReasons")
    @DisplayName("REQ-PRESENCE-013 - invalid removal reason -> HTTP 400 without mutation or activity")
    void invalidRemovalReasonShouldBeRejected(
            String scenario,
            boolean includeReason,
            String reason
    ) {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Invalid removal " + scenario);
        UUID memberId = createMember(caller, "Helena", "Rocha");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, null).path("id")
        );
        Map<String, Object> payload = new HashMap<>();
        if (includeReason) {
            payload.put("reason", reason);
        }
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .delete(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(presenceIsRemoved(presenceId)).isFalse();
        assertThat(activityCountFor("PRESENCE_REMOVED", presenceId)).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-011 and REQ-PRESENCE-013 - LOCKED Event -> intent-specific edit and removal conflicts without mutation")
    void lockedEventShouldRejectObservationEditAndRemovalWithoutMutation() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Locked attendance correction");
        UUID memberId = createMember(caller, "Igor", "Pires");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Unchanged").path("id")
        );
        jdbcTemplate.update(
                "UPDATE events SET status = CAST('LOCKED' AS event_status_enum) WHERE id = ?",
                eventId
        );
        clearActivities();

        ExtractableResponse<Response> edit = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "Changed"))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        ExtractableResponse<Response> removal = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();

        assertMutationConflict(edit, "PRESENCE_EDIT_NOT_ALLOWED", eventId, presenceId, "LOCKED");
        assertMutationConflict(
                removal,
                "PRESENCE_REMOVAL_NOT_ALLOWED",
                eventId,
                presenceId,
                "LOCKED"
        );
        assertThat(storedObservations(presenceId)).isEqualTo("Unchanged");
        assertThat(presenceIsRemoved(presenceId)).isFalse();
        assertThat(activityCountFor("PRESENCE_UPDATED", presenceId)).isZero();
        assertThat(activityCountFor("PRESENCE_REMOVED", presenceId)).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-004, REQ-PRESENCE-008, REQ-PRESENCE-011 and REQ-PRESENCE-013 - authentication and permission failures remain distinct")
    void presenceRoutesShouldDistinguishAuthenticationFromPermissionFailure() {
        AuthSession setup = newSession("SUDO");
        AuthSession noPermissions = newSessionWithPermissions();
        UUID eventId = createCompletedEvent(setup, "Presence route security");
        UUID memberId = createMember(setup, "Julia", "Araujo");
        registerPresence(setup, eventId, memberId, null);

        List<Integer> anonymousStatuses = List.of(
                jsonRequest().body(Map.of("memberId", memberId.toString()))
                        .post(EVENTS + "/{eventId}/presences", eventId).statusCode(),
                jsonRequest().get(EVENTS + "/{eventId}/presences", eventId).statusCode(),
                jsonRequest().get(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode(),
                jsonRequest().body(Map.of("observations", "Changed")).patch(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode(),
                jsonRequest().body(reasonPayload(VALID_REMOVAL_REASON)).delete(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode()
        );
        List<Integer> forbiddenStatuses = List.of(
                authenticatedJsonRequest(noPermissions).body(Map.of("memberId", memberId.toString()))
                        .post(EVENTS + "/{eventId}/presences", eventId).statusCode(),
                authenticatedJsonRequest(noPermissions)
                        .get(EVENTS + "/{eventId}/presences", eventId).statusCode(),
                authenticatedJsonRequest(noPermissions).get(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode(),
                authenticatedJsonRequest(noPermissions).body(Map.of("observations", "Changed")).patch(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode(),
                authenticatedJsonRequest(noPermissions).body(reasonPayload(VALID_REMOVAL_REASON)).delete(
                        EVENTS + "/{eventId}/presences/{memberId}",
                        eventId,
                        memberId
                ).statusCode()
        );

        assertThat(anonymousStatuses).containsOnly(401);
        assertThat(forbiddenStatuses).containsOnly(403);
    }

    @Test
    @DisplayName("REQ-PRESENCE-005 and REQ-PRESENCE-015 - concurrent duplicate registrations -> one Presence, one conflict, and one activity")
    void concurrentDuplicateRegistrationsShouldCreateExactlyOneActivePresence() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent duplicate registration");
        UUID memberId = createMember(caller, "Karen", "Duarte");
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> first = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> registerPresence(caller, eventId, memberId, "First request")
            );
            Future<ExtractableResponse<Response>> second = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> registerPresence(caller, eventId, memberId, "Second request")
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(201, 409);
            ExtractableResponse<Response> conflict = responses.stream()
                    .filter(response -> response.statusCode() == 409)
                    .findFirst()
                    .orElseThrow();
            ExtractableResponse<Response> created = responses.stream()
                    .filter(response -> response.statusCode() == 201)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflict.<String>path("code")).isEqualTo("PRESENCE_ALREADY_REGISTERED");
            assertThat(conflict.<String>path("details.eventId")).isEqualTo(eventId.toString());
            assertThat(conflict.<String>path("details.memberId")).isEqualTo(memberId.toString());
            assertThat(conflict.<String>path("details.presenceId"))
                    .isEqualTo(created.<String>path("id"));
            assertThat(activePresenceCount(eventId, memberId)).isEqualTo(1);
            assertThat(activityCount("PRESENCE_REGISTERED")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-PRESENCE-015 - concurrent edit and removal -> removal wins without overwrite or resurrection")
    void concurrentObservationEditAndRemovalShouldNotResurrectPresence() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent edit and removal");
        UUID memberId = createMember(caller, "Lucas", "Melo");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Original").path("id")
        );
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> edit = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(Map.of("observations", "Concurrent correction"))
                            .patch(
                                    EVENTS + "/{eventId}/presences/{memberId}",
                                    eventId,
                                    memberId
                            )
                            .then()
                            .extract()
            );
            Future<ExtractableResponse<Response>> removal = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(reasonPayload(VALID_REMOVAL_REASON))
                            .delete(
                                    EVENTS + "/{eventId}/presences/{memberId}",
                                    eventId,
                                    memberId
                            )
                            .then()
                            .extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> editResponse = edit.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> removalResponse = removal.get(20, TimeUnit.SECONDS);
            assertThat(editResponse.statusCode()).isIn(200, 404);
            assertThat(removalResponse.statusCode()).isEqualTo(204);
            if (editResponse.statusCode() == 404) {
                assertThat(editResponse.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
            }
            assertThat(presenceIsRemoved(presenceId)).isTrue();
            assertThat(activePresenceCount(eventId, memberId)).isZero();
            assertThat(activityCountFor("PRESENCE_UPDATED", presenceId))
                    .isEqualTo(editResponse.statusCode() == 200 ? 1 : 0);
            assertThat(activityCountFor("PRESENCE_REMOVED", presenceId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-PRESENCE-015 - concurrent removals -> at most one success and exactly one removal activity")
    void concurrentRemovalsShouldCommitAtMostOnce() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent removals");
        UUID memberId = createMember(caller, "Marina", "Souza");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, null).path("id")
        );
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Supplier<ExtractableResponse<Response>> removal = () -> authenticatedJsonRequest(caller)
                    .body(reasonPayload(VALID_REMOVAL_REASON))
                    .delete(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                    .then()
                    .extract();
            Future<ExtractableResponse<Response>> first = concurrentRequest(
                    executor,
                    ready,
                    start,
                    removal
            );
            Future<ExtractableResponse<Response>> second = concurrentRequest(
                    executor,
                    ready,
                    start,
                    removal
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(204, 404);
            ExtractableResponse<Response> rejected = responses.stream()
                    .filter(response -> response.statusCode() == 404)
                    .findFirst()
                    .orElseThrow();
            assertThat(rejected.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
            assertThat(presenceIsRemoved(presenceId)).isTrue();
            assertThat(activityCountFor("PRESENCE_REMOVED", presenceId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-PRESENCE-001 and REQ-PRESENCE-015 - concurrent removal and re-registration -> at most one active Presence")
    void concurrentRemovalAndReregistrationShouldLeaveAtMostOneActivePresence() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent removal and re-registration");
        UUID memberId = createMember(caller, "Nina", "Barros");
        UUID originalPresenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Original").path("id")
        );
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> removal = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> authenticatedJsonRequest(caller)
                            .body(reasonPayload(VALID_REMOVAL_REASON))
                            .delete(
                                    EVENTS + "/{eventId}/presences/{memberId}",
                                    eventId,
                                    memberId
                            )
                            .then()
                            .extract()
            );
            Future<ExtractableResponse<Response>> registration = concurrentRequest(
                    executor,
                    ready,
                    start,
                    () -> registerPresence(caller, eventId, memberId, "Replacement")
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> removalResponse = removal.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> registrationResponse = registration.get(
                    20,
                    TimeUnit.SECONDS
            );
            assertThat(removalResponse.statusCode()).isEqualTo(204);
            assertThat(registrationResponse.statusCode()).isIn(201, 409);
            if (registrationResponse.statusCode() == 409) {
                assertThat(registrationResponse.<String>path("code"))
                        .isEqualTo("PRESENCE_ALREADY_REGISTERED");
            }
            long expectedActiveCount = registrationResponse.statusCode() == 201 ? 1 : 0;
            assertThat(activePresenceCount(eventId, memberId)).isEqualTo(expectedActiveCount);
            assertThat(activePresenceCount(eventId, memberId)).isLessThanOrEqualTo(1);
            assertThat(presenceIsRemoved(originalPresenceId)).isTrue();
            assertThat(activityCountFor("PRESENCE_REMOVED", originalPresenceId)).isEqualTo(1);
            assertThat(activityCount("PRESENCE_REGISTERED")).isEqualTo(expectedActiveCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-PRESENCE-015 and ADR-0012 - Event lock wins controlled interleaving -> edit waits and rejects latest state")
    void concurrentEventLockAndObservationEditShouldSerializeLatestState() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent Event lock and Presence edit");
        UUID memberId = createMember(caller, "Otavio", "Freitas");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Before lock").path("id")
        );
        clearActivities();

        EventLockRaceResult result = eventLockWins(
                caller,
                eventId,
                () -> authenticatedJsonRequest(caller)
                        .body(Map.of("observations", "Must observe lock"))
                        .patch(
                                EVENTS + "/{eventId}/presences/{memberId}",
                                eventId,
                                memberId
                        )
                        .then()
                        .extract()
        );

        assertThat(result.eventLock().statusCode()).isEqualTo(200);
        assertThat(result.presenceMutation().statusCode()).isEqualTo(409);
        assertThat(result.presenceMutation().<String>path("code"))
                .isEqualTo("PRESENCE_EDIT_NOT_ALLOWED");
        assertThat(storedObservations(presenceId)).isEqualTo("Before lock");
        assertThat(storedEventStatus(eventId)).isEqualTo("LOCKED");
        assertThat(activityCountFor("PRESENCE_UPDATED", presenceId)).isZero();
        assertThat(activityCountFor("EVENT_LOCKED", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-003/015 and ADR-0012 - Event lock wins controlled interleaving -> registration waits and rejects latest state")
    void concurrentEventLockAndRegistrationShouldSerializeLatestState() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent Event lock and registration");
        UUID memberId = createMember(caller, "Pedro", "Azevedo");
        clearActivities();

        EventLockRaceResult result = eventLockWins(
                caller,
                eventId,
                () -> registerPresence(caller, eventId, memberId, "Must observe lock")
        );

        assertThat(result.eventLock().statusCode()).isEqualTo(200);
        assertThat(result.presenceMutation().statusCode()).isEqualTo(409);
        assertThat(result.presenceMutation().<String>path("code"))
                .isEqualTo("PRESENCE_REGISTRATION_NOT_ALLOWED");
        assertThat(activePresenceCount(eventId, memberId)).isZero();
        assertThat(activityCount("PRESENCE_REGISTERED")).isZero();
        assertThat(activityCountFor("EVENT_LOCKED", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-013/015 and ADR-0012 - Event lock wins controlled interleaving -> removal waits and rejects latest state")
    void concurrentEventLockAndRemovalShouldSerializeLatestState() throws Exception {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Concurrent Event lock and removal");
        UUID memberId = createMember(caller, "Renata", "Macedo");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Must remain").path("id")
        );
        clearActivities();

        EventLockRaceResult result = eventLockWins(
                caller,
                eventId,
                () -> authenticatedJsonRequest(caller)
                        .body(reasonPayload(VALID_REMOVAL_REASON))
                        .delete(
                                EVENTS + "/{eventId}/presences/{memberId}",
                                eventId,
                                memberId
                        )
                        .then()
                        .extract()
        );

        assertThat(result.eventLock().statusCode()).isEqualTo(200);
        assertThat(result.presenceMutation().statusCode()).isEqualTo(409);
        assertThat(result.presenceMutation().<String>path("code"))
                .isEqualTo("PRESENCE_REMOVAL_NOT_ALLOWED");
        assertThat(presenceIsRemoved(presenceId)).isFalse();
        assertThat(activityCountFor("PRESENCE_REMOVED", presenceId)).isZero();
        assertThat(activityCountFor("EVENT_LOCKED", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-006/012/014 - activity failure -> registration, edit, and removal roll back atomically")
    void activityFailureShouldRollBackPresenceMutations() {
        AuthSession caller = newSession("SUDO");
        UUID registrationEventId = createCompletedEvent(caller, "Registration rollback");
        UUID registrationMemberId = createMember(caller, "Olivia", "Nogueira");
        clearActivities();
        failActivityWritesFor("PRESENCE_REGISTERED");

        try {
            ExtractableResponse<Response> registration = registerPresence(
                    caller,
                    registrationEventId,
                    registrationMemberId,
                    "Must roll back"
            );
            assertThat(registration.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(activePresenceCount(registrationEventId, registrationMemberId)).isZero();
        assertThat(totalPresenceCount(registrationEventId, registrationMemberId)).isZero();
        assertThat(activityCount("PRESENCE_REGISTERED")).isZero();

        UUID editEventId = createCompletedEvent(caller, "Edit rollback");
        UUID editMemberId = createMember(caller, "Paula", "Teixeira");
        UUID editPresenceId = UUID.fromString(
                registerPresence(caller, editEventId, editMemberId, "Original").path("id")
        );
        clearActivities();
        failActivityWritesFor("PRESENCE_UPDATED");

        try {
            ExtractableResponse<Response> edit = authenticatedJsonRequest(caller)
                    .body(Map.of("observations", "Must roll back"))
                    .patch(
                            EVENTS + "/{eventId}/presences/{memberId}",
                            editEventId,
                            editMemberId
                    )
                    .then()
                    .extract();
            assertThat(edit.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(storedObservations(editPresenceId)).isEqualTo("Original");
        assertThat(activityCountFor("PRESENCE_UPDATED", editPresenceId)).isZero();

        UUID removalEventId = createCompletedEvent(caller, "Removal rollback");
        UUID removalMemberId = createMember(caller, "Rafael", "Vieira");
        UUID removalPresenceId = UUID.fromString(
                registerPresence(caller, removalEventId, removalMemberId, "Retained").path("id")
        );
        clearActivities();
        failActivityWritesFor("PRESENCE_REMOVED");

        try {
            ExtractableResponse<Response> removal = authenticatedJsonRequest(caller)
                    .body(reasonPayload(VALID_REMOVAL_REASON))
                    .delete(
                            EVENTS + "/{eventId}/presences/{memberId}",
                            removalEventId,
                            removalMemberId
                    )
                    .then()
                    .extract();
            assertThat(removal.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(presenceIsRemoved(removalPresenceId)).isFalse();
        assertThat(activePresenceCount(removalEventId, removalMemberId)).isEqualTo(1);
        assertThat(activityCountFor("PRESENCE_REMOVED", removalPresenceId)).isZero();
    }

    @Test
    @DisplayName("REQ-PRESENCE-002, REQ-PRESENCE-011 and REQ-PRESENCE-013 - normalized text boundaries are enforced after trimming")
    void normalizedObservationAndRemovalReasonBoundariesShouldBeEnforcedAfterTrimming() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Normalized Presence boundaries");
        UUID memberId = createMember(caller, "Sara", "Gomes");
        UUID presenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, "Initial").path("id")
        );
        String maximumObservation = "o".repeat(2_000);

        ExtractableResponse<Response> maximumEdit = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "  " + maximumObservation + "  "))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        assertThat(maximumEdit.statusCode()).isEqualTo(200);
        assertThat(maximumEdit.<String>path("observations")).isEqualTo(maximumObservation);

        ExtractableResponse<Response> oversizedEdit = authenticatedJsonRequest(caller)
                .body(Map.of("observations", "o".repeat(2_001)))
                .patch(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        assertThat(oversizedEdit.statusCode()).isEqualTo(400);
        assertThat(storedObservations(presenceId)).isEqualTo(maximumObservation);

        String maximumReason = "r".repeat(2_000);
        ExtractableResponse<Response> removal = authenticatedJsonRequest(caller)
                .body(reasonPayload("  " + maximumReason + "  "))
                .delete(EVENTS + "/{eventId}/presences/{memberId}", eventId, memberId)
                .then()
                .extract();
        assertThat(removal.statusCode()).isEqualTo(204);
        assertThat(activityFor("PRESENCE_REMOVED", presenceId))
                .containsEntry("reason", maximumReason);
    }

    @Test
    @DisplayName("REQ-PRESENCE-009 - accent-sensitive and component-wise name matching avoids concatenated full-name behavior")
    void rosterNameFilterShouldRemainAccentSensitiveAndComponentWise() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Roster filter boundaries");
        UUID accentedMemberId = createMember(caller, "Alvaro", "Ávila");
        UUID ordinaryMemberId = createMember(caller, "Ana", "Silva");
        registerPresence(caller, eventId, accentedMemberId, null);
        registerPresence(caller, eventId, ordinaryMemberId, null);

        ExtractableResponse<Response> accented = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?name=áv&size=100", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> unaccented = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?name=av&size=100", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> concatenated = authenticatedJsonRequest(caller)
                .get(EVENTS + "/{eventId}/presences?name=Ana%20Silva&size=100", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> blank = authenticatedJsonRequest(caller)
                .queryParam("name", "   ")
                .queryParam("size", 100)
                .get(EVENTS + "/{eventId}/presences", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> percent = authenticatedJsonRequest(caller)
                .queryParam("name", "%")
                .queryParam("size", 100)
                .get(EVENTS + "/{eventId}/presences", eventId)
                .then()
                .extract();
        ExtractableResponse<Response> underscore = authenticatedJsonRequest(caller)
                .queryParam("name", "_")
                .queryParam("size", 100)
                .get(EVENTS + "/{eventId}/presences", eventId)
                .then()
                .extract();

        assertThat(accented.statusCode()).isEqualTo(200);
        assertThat(accented.<List<String>>path("items.member.id"))
                .containsExactly(accentedMemberId.toString());
        assertThat(unaccented.statusCode()).isEqualTo(200);
        assertThat(unaccented.<List<String>>path("items.member.id")).isEmpty();
        assertThat(concatenated.statusCode()).isEqualTo(200);
        assertThat(concatenated.<List<String>>path("items.member.id")).isEmpty();
        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(percent.statusCode()).isEqualTo(200);
        assertThat(percent.<List<String>>path("items.member.id")).isEmpty();
        assertThat(underscore.statusCode()).isEqualTo(200);
        assertThat(underscore.<List<String>>path("items.member.id")).isEmpty();
    }

    @Test
    @DisplayName("REQ-PRESENCE-009 - requested sort always appends Presence UUID ascending as deterministic tie-breaker")
    void requestedRosterSortShouldAppendPresenceIdTieBreaker() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Roster sort tie-breaker");
        UUID firstMemberId = createMember(caller, "Tiago", "Amaral");
        UUID secondMemberId = createMember(caller, "Ursula", "Amaral");
        UUID firstPresenceId = UUID.fromString(
                registerPresence(caller, eventId, firstMemberId, null).path("id")
        );
        UUID secondPresenceId = UUID.fromString(
                registerPresence(caller, eventId, secondMemberId, null).path("id")
        );
        Timestamp sharedRegisteredAt = Timestamp.from(Instant.parse("2026-07-01T12:00:00Z"));
        jdbcTemplate.update(
                "UPDATE presences SET created_at = ? WHERE id IN (?, ?)",
                sharedRegisteredAt,
                firstPresenceId,
                secondPresenceId
        );

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get(
                        EVENTS + "/{eventId}/presences?sort=registeredAt,desc&size=100",
                        eventId
                )
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id")).containsExactlyElementsOf(
                Stream.of(firstPresenceId, secondPresenceId)
                        .map(UUID::toString)
                        .sorted()
                        .toList()
        );
    }

    @Test
    @br.org.gam.api.testing.annotation.PersistenceTest
    @DisplayName("REQ-PRESENCE-001 and ADR-0012 - database uniqueness applies only to active Event-Member pairs")
    void databaseShouldEnforceActiveOnlyPresenceUniqueness() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Presence uniqueness safeguard");
        UUID memberId = createMember(caller, "Vitor", "Campos");
        UUID firstPresenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, null).path("id")
        );
        Timestamp now = Timestamp.from(Instant.now());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO presences (id, member_id, event_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                memberId,
                eventId,
                now,
                now
        )).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                "UPDATE presences SET deleted_at = ?, updated_at = ? WHERE id = ?",
                now,
                now,
                firstPresenceId
        );
        UUID replacementPresenceId = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO presences (id, member_id, event_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                replacementPresenceId,
                memberId,
                eventId,
                now,
                now
        );

        assertThat(inserted).isEqualTo(1);
        assertThat(activePresenceCount(eventId, memberId)).isEqualTo(1);
        assertThat(totalPresenceCount(eventId, memberId)).isEqualTo(2);
    }

    @Test
    @br.org.gam.api.testing.annotation.PersistenceTest
    @DisplayName("REQ-PRESENCE-005 - aborted losing transaction -> REQUIRES_NEW resolver still reads the committed winner")
    void conflictResolverShouldReadWinnerAfterLosingTransactionIsAborted() {
        AuthSession caller = newSession("SUDO");
        UUID eventId = createCompletedEvent(caller, "Presence conflict transaction boundary");
        UUID memberId = createMember(caller, "William", "Pereira");
        UUID winningPresenceId = UUID.fromString(
                registerPresence(caller, eventId, memberId, null).path("id")
        );
        Timestamp now = Timestamp.from(Instant.now());
        AtomicReference<UUID> resolvedPresenceId = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO presences (id, member_id, event_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    memberId,
                    eventId,
                    now,
                    now
            )).isInstanceOf(DataIntegrityViolationException.class);

            resolvedPresenceId.set(
                    presenceConflictResolver.findWinningPresenceId(memberId, eventId).orElseThrow()
            );
            status.setRollbackOnly();
        });

        assertThat(resolvedPresenceId).hasValue(winningPresenceId);
        assertThat(activePresenceCount(eventId, memberId)).isEqualTo(1);
    }

    private void installEventLockGate() {
        removeEventLockGate();
        jdbcTemplate.execute(("""
                CREATE OR REPLACE FUNCTION block_presence_event_lock_test() RETURNS trigger AS $$
                BEGIN
                    IF NEW.status = 'LOCKED' AND OLD.status IS DISTINCT FROM NEW.status THEN
                        PERFORM pg_advisory_xact_lock(%d);
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """).formatted(EVENT_LOCK_GATE));
        try {
            jdbcTemplate.execute("""
                    CREATE TRIGGER block_presence_event_lock_test_trigger
                    BEFORE UPDATE ON events
                    FOR EACH ROW EXECUTE FUNCTION block_presence_event_lock_test()
                    """);
        } catch (RuntimeException exception) {
            removeEventLockGate();
            throw exception;
        }
    }

    private void removeEventLockGate() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS block_presence_event_lock_test_trigger ON events");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS block_presence_event_lock_test()");
    }

    private static void acquireEventLockGate(Connection gate) throws SQLException {
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(" + EVENT_LOCK_GATE + ")");
        }
    }

    private static void releaseEventLockGate(Connection gate) throws SQLException {
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_advisory_unlock(" + EVENT_LOCK_GATE + ")");
        }
    }

    private void awaitEventLockGateWait() {
        awaitDatabaseCondition(
                "SELECT EXISTS (SELECT 1 FROM pg_locks "
                        + "WHERE locktype = 'advisory' AND classid = 0 AND objid = ? "
                        + "AND objsubid = 1 AND granted = FALSE)",
                "Event lock advisory gate",
                EVENT_LOCK_GATE
        );
    }

    private void awaitEventRowLockWait() {
        awaitDatabaseCondition(
                "SELECT EXISTS (SELECT 1 FROM pg_locks l "
                        + "JOIN pg_stat_activity a ON a.pid = l.pid "
                        + "WHERE l.locktype = 'transactionid' AND l.granted = FALSE "
                        + "AND a.state = 'active' AND a.query ILIKE '%events%')",
                "Event row lock"
        );
    }

    private void awaitDatabaseCondition(String query, String description, Object... arguments) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(query, Boolean.class, arguments);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }

        throw new AssertionError("Timed out waiting for blocked " + description);
    }

    private EventLockRaceResult eventLockWins(
            AuthSession caller,
            UUID eventId,
            Supplier<ExtractableResponse<Response>> presenceMutation
    ) throws Exception {
        installEventLockGate();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection gate = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            acquireEventLockGate(gate);
            boolean gateReleased = false;

            try {
                Future<ExtractableResponse<Response>> eventLock = executor.submit(
                        () -> authenticatedJsonRequest(caller)
                                .patch(EVENTS + "/{eventId}/lock", eventId)
                                .then()
                                .extract()
                );
                awaitEventLockGateWait();

                Future<ExtractableResponse<Response>> mutation = executor.submit(presenceMutation::get);
                awaitEventRowLockWait();
                assertThat(mutation.isDone()).isFalse();

                releaseEventLockGate(gate);
                gateReleased = true;

                return new EventLockRaceResult(
                        eventLock.get(20, TimeUnit.SECONDS),
                        mutation.get(20, TimeUnit.SECONDS)
                );
            } finally {
                if (!gateReleased) {
                    releaseEventLockGate(gate);
                }
            }
        } finally {
            executor.shutdownNow();
            removeEventLockGate();
        }
    }

    private record EventLockRaceResult(
            ExtractableResponse<Response> eventLock,
            ExtractableResponse<Response> presenceMutation
    ) {
    }

    private UUID createCompletedEvent(AuthSession caller, String title) {
        return createEvent(
                caller,
                title,
                null,
                Instant.now().minusSeconds(7_200),
                Instant.now().minusSeconds(3_600)
        );
    }

    private UUID createEvent(
            AuthSession caller,
            String title,
            UUID requiredPermissionId,
            Instant beginDate,
            Instant endDate
    ) {
        ExtractableResponse<Response> location = authenticatedJsonRequest(caller)
                .body(gamLocationPayload("Location for " + title))
                .post("/gam-locations")
                .then()
                .statusCode(201)
                .extract();
        UUID locationId = UUID.fromString(location.path("id"));
        trackGamLocation(locationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("description", "Member Presence integration fixture");
        payload.put("gamLocationId", locationId.toString());
        payload.put(
                "requiredPermissionId",
                requiredPermissionId == null ? null : requiredPermissionId.toString()
        );
        payload.put("beginDate", beginDate.toString());
        payload.put("endDate", endDate.toString());

        ExtractableResponse<Response> event = authenticatedJsonRequest(caller)
                .body(payload)
                .post(EVENTS)
                .then()
                .statusCode(201)
                .extract();
        UUID eventId = UUID.fromString(event.path("id"));
        trackEvent(eventId);
        return eventId;
    }

    private UUID createMember(AuthSession caller, String firstName, String surname) {
        UUID accountId = newAccount(firstName + " " + surname);
        String phoneNumber = "+55119" + phoneSequence++;
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(memberPayload(
                        accountId,
                        firstName,
                        surname,
                        LocalDate.now().minusYears(20),
                        phoneNumber,
                        VALID_REASON
                ))
                .post("/members")
                .then()
                .statusCode(201)
                .extract();
        UUID memberId = UUID.fromString(response.path("id"));
        memberIds.add(memberId);
        return memberId;
    }

    private ExtractableResponse<Response> registerPresence(
            AuthSession caller,
            UUID eventId,
            UUID memberId,
            String observations
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", memberId.toString());
        payload.put("observations", observations);
        return authenticatedJsonRequest(caller)
                .body(payload)
                .post(EVENTS + "/{eventId}/presences", eventId)
                .then()
                .extract();
    }

    private long activePresenceCount(UUID eventId, UUID memberId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM presences "
                        + "WHERE event_id = ? AND member_id = ? AND deleted_at IS NULL",
                Long.class,
                eventId,
                memberId
        ));
    }

    private long totalPresenceCount(UUID eventId, UUID memberId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM presences WHERE event_id = ? AND member_id = ?",
                Long.class,
                eventId,
                memberId
        ));
    }

    private String storedObservations(UUID presenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT observations FROM presences WHERE id = ?",
                String.class,
                presenceId
        );
    }

    private Instant storedEventBeginDate(UUID eventId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT begin_date FROM events WHERE id = ?",
                Timestamp.class,
                eventId
        )).toInstant();
    }

    private Timestamp presenceUpdatedAt(UUID presenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM presences WHERE id = ?",
                Timestamp.class,
                presenceId
        );
    }

    private boolean presenceIsRemoved(UUID presenceId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM presences WHERE id = ?",
                Boolean.class,
                presenceId
        ));
    }

    private String storedEventStatus(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text FROM events WHERE id = ?",
                String.class,
                eventId
        );
    }

    private long activityCountFor(String action, UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = ? AND target_id = ?",
                Long.class,
                action,
                targetId
        ));
    }

    private Map<String, Object> activityFor(String action, UUID targetId) {
        return jdbcTemplate.queryForMap(
                "SELECT reason, metadata FROM activity_logs WHERE action = ? AND target_id = ?",
                action,
                targetId
        );
    }

    @SuppressWarnings("unchecked")
    private static void assertCompactPresence(
            Map<String, Object> presence,
            UUID presenceId,
            UUID memberId,
            UUID eventId
    ) {
        assertThat(presence)
                .containsOnlyKeys(PRESENCE_FIELDS)
                .containsEntry("id", presenceId.toString());
        Map<String, Object> member = (Map<String, Object>) presence.get("member");
        Map<String, Object> event = (Map<String, Object>) presence.get("event");
        assertThat(member)
                .containsOnlyKeys(PRESENCE_MEMBER_FIELDS)
                .containsEntry("id", memberId.toString());
        assertThat(event)
                .containsOnlyKeys(PRESENCE_EVENT_FIELDS)
                .containsEntry("id", eventId.toString());
        assertThat(presence.get("registeredAt")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static String nestedString(
            Map<String, Object> record,
            String nestedField,
            String field
    ) {
        return (String) ((Map<String, Object>) record.get(nestedField)).get(field);
    }

    private static void assertPresenceNotFound(
            ExtractableResponse<Response> response,
            UUID eventId,
            UUID memberId
    ) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.<String>path("details.resource")).isEqualTo("Presence");
        assertThat(response.<String>path("details.identifier"))
                .isEqualTo(eventId + ":" + memberId);
    }

    private static void assertMutationConflict(
            ExtractableResponse<Response> response,
            String code,
            UUID eventId,
            UUID presenceId,
            String status
    ) {
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo(code);
        assertThat(response.<String>path("details.eventId")).isEqualTo(eventId.toString());
        assertThat(response.<String>path("details.presenceId")).isEqualTo(presenceId.toString());
        assertThat(response.<String>path("details.status")).isEqualTo(status);
    }

    private static Stream<Arguments> invalidRemovalReasons() {
        return Stream.of(
                Arguments.of("EP - omitted", false, null),
                Arguments.of("EP - explicit null", true, null),
                Arguments.of("EP - blank", true, " \n\t "),
                Arguments.of("BVA - 2,001 normalized characters", true, "r".repeat(2_001))
        );
    }

    private static Future<ExtractableResponse<Response>> concurrentRequest(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Supplier<ExtractableResponse<Response>> request
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent Presence request start timed out.");
            }
            return request.get();
        });
    }
}
