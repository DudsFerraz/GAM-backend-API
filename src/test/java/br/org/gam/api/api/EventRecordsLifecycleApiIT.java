package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.SoftAssertions;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Event records and Generic Event lifecycle")
class EventRecordsLifecycleApiIT extends MemberApiTestSupport {

    private static final String EVENTS = "/events";
    private static final String VALID_REASON = "  Correcting the Event lifecycle  ";
    private static final Set<String> EVENT_FIELDS = Set.of(
            "id", "title", "description", "gamLocation", "requiredPermission",
            "beginDate", "endDate", "type", "status", "cancellationReason"
    );

    private final List<UUID> presenceIds = new ArrayList<>();
    private final List<UUID> memberIds = new ArrayList<>();

    @AfterEach
    void cleanupEventFixtures() {
        for (UUID presenceId : presenceIds) {
            jdbcTemplate.update("DELETE FROM presences WHERE id = ?", presenceId);
        }
        for (UUID memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
        }
        presenceIds.clear();
        memberIds.clear();
    }

    @Test
    @DisplayName("REQ-EVENT-001, REQ-EVENT-002, REQ-EVENT-003, REQ-EVENT-006 and REQ-EVENT-007 - normalized public creation -> complete Generic Event and one activity")
    void normalizedPublicCreationShouldReturnCompleteGenericEventAndAuditIt() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID locationId = createLocation(caller, "Generic Event creation");
        clearActivities();

        Map<String, Object> payload = eventPayload(
                "  Encontro de Oração  ", locationId, null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600)
        );
        payload.put("description", "  Texto com espaços internos  ");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload).post(EVENTS).then().extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID eventId = UUID.fromString(response.path("id"));
        trackEvent(eventId);
        assertPublicApiLocation(response, EVENTS + "/" + eventId);
        assertUuidV7(eventId);
        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys(EVENT_FIELDS.toArray());
        assertThat(response.<String>path("title")).isEqualTo("Encontro de Oração");
        assertThat(response.<String>path("description")).isEqualTo("Texto com espaços internos");
        assertThat(response.<String>path("type")).isEqualTo("GENERIC");
        assertThat(response.<String>path("status")).isEqualTo("COMPLETED");
        assertThat(response.<Object>path("requiredPermission")).isNull();
        assertThat(response.<Object>path("cancellationReason")).isNull();
        assertThat(response.<String>path("gamLocation.id")).isEqualTo(locationId.toString());

        assertThat(activityCountFor("EVENT_CREATED", eventId)).isEqualTo(1);
        Map<String, Object> activity = eventActivity("EVENT_CREATED", eventId);
        assertThat(activity).containsEntry("target_id", eventId).containsEntry("reason", null);
        Map<String, Object> metadata = JsonPath.from(activity.get("metadata").toString()).getMap("$");
        assertThat(metadata)
                .containsOnlyKeys("type", "status", "gamLocationId", "requiredPermissionId")
                .containsEntry("type", "GENERIC")
                .containsEntry("status", "COMPLETED")
                .containsEntry("gamLocationId", locationId.toString())
                .containsEntry("requiredPermissionId", null);
        assertThat(metadata.toString())
                .doesNotContain("Encontro de Oração", "Texto com espaços internos");
    }

    @Test
    @DisplayName("REQ-EVENT-004 and REQ-GAM-LOCATION-CATALOG-001/003 - Generic Event selects and embeds the singleton Remote GamLocation")
    void genericEventShouldSelectAndEmbedTheRemoteGamLocation() {
        AuthSession caller = newSessionWithPermissions("EVENT_CREATE", "GAM_LOCATION_GET");
        ExtractableResponse<Response> locations = authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .get("/gam-locations")
                .then()
                .statusCode(200)
                .extract();
        Map<String, Object> remote = locations
                .<List<Map<String, Object>>>path("items")
                .stream()
                .filter(location -> "REMOTE".equals(location.get("code")))
                .findFirst()
                .orElseThrow();
        UUID remoteId = UUID.fromString(remote.get("id").toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(eventPayload(
                        "Remote planning meeting",
                        remoteId,
                        null,
                        Instant.now().plusSeconds(3_600),
                        Instant.now().plusSeconds(7_200)
                ))
                .post(EVENTS)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID eventId = UUID.fromString(response.path("id"));
        trackEvent(eventId);
        assertThat(response.<Map<String, Object>>path("gamLocation"))
                .containsOnlyKeys(
                        "id", "code", "systemManaged", "name", "street", "city", "state",
                        "postalCode", "countryCode", "latitude", "longitude"
                )
                .containsEntry("id", remoteId.toString())
                .containsEntry("code", "REMOTE")
                .containsEntry("systemManaged", true)
                .containsEntry("name", "Remoto")
                .containsEntry("street", null)
                .containsEntry("city", null)
                .containsEntry("state", null)
                .containsEntry("postalCode", null)
                .containsEntry("countryCode", null)
                .containsEntry("latitude", null)
                .containsEntry("longitude", null);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-003/007 and REQ-EVENT-002/004 - retired system location -> hidden directly but embedded in historical Event")
    void retiredSystemLocationShouldRemainEmbeddedInHistoricalEvent() {
        AuthSession caller = newSessionWithPermissions("EVENT_CREATE", "GAM_LOCATION_GET");
        UUID locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM gam_locations WHERE code = 'DBA'",
                UUID.class
        );
        Map<String, Object> payload = eventPayload(
                "Historical system-location Event",
                locationId,
                null,
                Instant.now().minusSeconds(7_200),
                Instant.now().minusSeconds(3_600)
        );
        ExtractableResponse<Response> creation = authenticatedJsonRequest(caller)
                .body(payload)
                .post(EVENTS)
                .then()
                .statusCode(201)
                .extract();
        UUID eventId = UUID.fromString(creation.path("id"));
        trackEvent(eventId);

        try {
            jdbcTemplate.update(
                    "UPDATE gam_locations SET catalog_current = FALSE WHERE id = ?",
                    locationId
            );

            ExtractableResponse<Response> historicalEvent = authenticatedJsonRequest(caller)
                    .get(EVENTS + "/" + eventId)
                    .then()
                    .statusCode(200)
                    .extract();
            assertThat(historicalEvent.<String>path("gamLocation.id"))
                    .isEqualTo(locationId.toString());
            assertThat(historicalEvent.<String>path("gamLocation.code")).isEqualTo("DBA");
            assertThat(historicalEvent.<Boolean>path("gamLocation.systemManaged")).isTrue();
            assertThat(historicalEvent.<String>path("gamLocation.name"))
                    .isEqualTo("Dom Bosco Assunção");

            ExtractableResponse<Response> directLocation = authenticatedJsonRequest(caller)
                    .get("/gam-locations/" + locationId)
                    .then()
                    .extract();
            assertThat(directLocation.statusCode()).isEqualTo(404);
            assertThat(directLocation.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        } finally {
            jdbcTemplate.update(
                    "UPDATE gam_locations SET catalog_current = TRUE WHERE id = ?",
                    locationId
            );
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreationPayloads")
    @DisplayName("REQ-EVENT-003 - invalid creation equivalence classes -> HTTP 400 and no Event activity")
    void invalidCreationInputShouldBeRejected(String scenario, Map<String, Object> changes) {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID locationId = createLocation(caller, "Invalid Event input");
        Map<String, Object> payload = eventPayload(
                "Valid title", locationId, null, Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)
        );
        payload.putAll(changes);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload).post(EVENTS).then().extract();

        trackUnexpectedlyCreatedEvent(response);
        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(activityCount("EVENT_CREATED")).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-003 - inclusive text maxima and a one-microsecond Event duration are accepted")
    void creationBoundaryValuesShouldBeAccepted() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID locationId = createLocation(caller, "Event boundary values");
        Instant beginDate = Instant.parse("2030-01-01T10:00:00Z");
        Map<String, Object> payload = eventPayload(
                "t".repeat(255), locationId, null, beginDate, beginDate.plusNanos(1_000)
        );
        payload.put("description", "d".repeat(10_000));

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload).post(EVENTS).then().extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID eventId = UUID.fromString(response.path("id"));
        trackEvent(eventId);
        assertThat(response.<String>path("title")).hasSize(255);
        assertThat(response.<String>path("description")).hasSize(10_000);
        assertThat(response.<String>path("beginDate")).isEqualTo(beginDate.toString());
        assertThat(response.<String>path("endDate")).isEqualTo(beginDate.plusNanos(1_000).toString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"create", "replace"})
    @DisplayName("REQ-EVENT-003 and REQ-EVENT-014 - text maxima are measured after trimming")
    void normalizedTextBoundaryShouldBeAcceptedAfterTrimming(String operation) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID locationId = createLocation(caller, "Normalized Event text boundary");
        String normalizedTitle = "t".repeat(255);
        String normalizedDescription = "d".repeat(10_000);
        Map<String, Object> payload = eventPayload(
                "  " + normalizedTitle + "  ", locationId, null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)
        );
        payload.put("description", "  " + normalizedDescription + "  ");

        ExtractableResponse<Response> response;
        if ("create".equals(operation)) {
            response = authenticatedJsonRequest(caller).body(payload).post(EVENTS).then().extract();
            trackUnexpectedlyCreatedEvent(response);
        } else {
            UUID eventId = createEvent(caller, "Before normalized replacement", null,
                    Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
            response = authenticatedJsonRequest(caller).body(payload)
                    .put(EVENTS + "/{id}", eventId).then().extract();
        }

        assertThat(response.statusCode()).isEqualTo("create".equals(operation) ? 201 : 200);
        assertThat(response.<String>path("title")).isEqualTo(normalizedTitle);
        assertThat(response.<String>path("description")).isEqualTo(normalizedDescription);
    }

    @Test
    @DisplayName("REQ-EVENT-004 and REQ-EVENT-005 - inactive related resources and invalid audience permissions -> documented errors without creation")
    void invalidCreationRelationshipsShouldBeRejectedWithoutMutation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_GET_COORD"
        );
        UUID deletedLocation = createLocation(caller, "Removed Event location");
        jdbcTemplate.update("UPDATE gam_locations SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deletedLocation);
        clearActivities();

        ExtractableResponse<Response> missingLocation = authenticatedJsonRequest(caller)
                .body(eventPayload("Missing location", deletedLocation, null,
                        Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)))
                .post(EVENTS).then().extract();
        assertResourceNotFound(missingLocation, "GamLocation");

        UUID activeLocation = createLocation(caller, "Invalid audience location");
        UUID unrelatedPermission = permissionId("ACCOUNT_GET");
        ExtractableResponse<Response> invalidAudience = authenticatedJsonRequest(caller)
                .body(eventPayload("Invalid audience", activeLocation, unrelatedPermission,
                        Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)))
                .post(EVENTS).then().extract();
        trackUnexpectedlyCreatedEvent(invalidAudience);
        assertThat(invalidAudience.statusCode()).isEqualTo(400);
        assertThat(invalidAudience.<String>path("code")).isEqualTo("EVENT_AUDIENCE_PERMISSION_INVALID");
        assertThat(activityCount("EVENT_CREATED")).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-005 - restricted creation requires both EVENT_CREATE and the exact audience permission")
    void restrictedCreationShouldRequireExactAudiencePermission() {
        AuthSession setup = newSessionWithPermissions("GAM_LOCATION_CREATE");
        UUID locationId = createLocation(setup, "Restricted creation");
        AuthSession caller = newSessionWithPermissions("EVENT_CREATE", "EVENT_GET_MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(eventPayload("Coordinator Event", locationId, permissionId("EVENT_GET_COORD"),
                        Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)))
                .post(EVENTS).then().extract();

        trackUnexpectedlyCreatedEvent(response);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(activityCount("EVENT_CREATED")).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-002, REQ-EVENT-006 and REQ-EVENT-008 - anonymous common read exposes specialized public Event with time-derived status and no activity")
    void anonymousReadShouldExposeVisibleSpecializedEventAndDeriveStatusWithoutAudit() {
        AuthSession setup = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID eventId = createEvent(setup, "Specialized common read", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        jdbcTemplate.update("UPDATE events SET type = CAST('ORATORIO' AS event_type_enum), status = CAST('SCHEDULED' AS event_status_enum) WHERE id = ?", eventId);
        clearActivities();

        ExtractableResponse<Response> response = jsonRequest().get(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("type")).isEqualTo("ORATORIO");
        assertThat(response.<String>path("status")).isEqualTo("COMPLETED");
        assertThat(activityCountForTarget(eventId)).isZero();
        assertThat(eventStoredStatus(eventId)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("REQ-EVENT-008 - inaccessible, missing, and soft-deleted Events share the same not-found response")
    void inaccessibleAndAbsentEventsShouldBeIndistinguishable() {
        AuthSession creator = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_GET_COORD"
        );
        UUID restrictedId = createEvent(creator, "Restricted read", permissionId("EVENT_GET_COORD"),
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
        UUID deletedId = createEvent(creator, "Deleted read", null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
        jdbcTemplate.update("UPDATE events SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deletedId);
        AuthSession member = newSessionWithPermissions("EVENT_GET_MEMBER");

        List<ExtractableResponse<Response>> responses = List.of(
                authenticatedJsonRequest(member).get(EVENTS + "/{id}", restrictedId).then().extract(),
                authenticatedJsonRequest(member).get(EVENTS + "/{id}", deletedId).then().extract(),
                authenticatedJsonRequest(member).get(EVENTS + "/{id}", UUID.randomUUID()).then().extract()
        );

        responses.forEach(response -> assertResourceNotFound(response, "Event"));
    }

    @Test
    @DisplayName("REQ-EVENT-008, REQ-EVENT-009, REQ-EVENT-012 and REQ-EVENT-015 - stale audience permission hides Event from reads, search, and mutation")
    void staleAudiencePermissionShouldHideEventAcrossPublicSeams() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_SEARCH", "EVENT_MANAGE", "EVENT_GET_COORD"
        );
        UUID permissionId = permissionId("EVENT_GET_COORD");
        UUID eventId = createEvent(caller, "Stale audience Event", permissionId,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        Map<String, Object> originalPermission = jdbcTemplate.queryForMap(
                "SELECT label, description FROM permissions WHERE id = ?", permissionId
        );
        clearActivities();

        try {
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ?, description = ? WHERE id = ?",
                    "Stale coordinator audience", "Stale permission metadata", permissionId
            );

            ExtractableResponse<Response> getResponse = authenticatedJsonRequest(caller)
                    .get(EVENTS + "/{id}", eventId).then().extract();
            ExtractableResponse<Response> searchResponse = authenticatedJsonRequest(caller)
                    .body(searchPayload(filter("id", eventId.toString(), "EQUALS")))
                    .post(EVENTS + "/search").then().extract();
            ExtractableResponse<Response> mutationResponse = authenticatedJsonRequest(caller)
                    .patch(EVENTS + "/{id}/finalize", eventId).then().extract();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(getResponse.statusCode()).as("get status").isEqualTo(404);
            softly.assertThat(getResponse.<String>path("code")).as("get error code")
                    .isEqualTo("RESOURCE_NOT_FOUND");
            softly.assertThat(searchResponse.statusCode()).as("search status").isEqualTo(200);
            softly.assertThat(searchResponse.<List<String>>path("items.id")).as("search visibility")
                    .doesNotContain(eventId.toString());
            softly.assertThat(mutationResponse.statusCode()).as("mutation status").isEqualTo(404);
            softly.assertThat(mutationResponse.<String>path("code")).as("mutation error code")
                    .isEqualTo("RESOURCE_NOT_FOUND");
            softly.assertThat(eventStoredStatus(eventId)).as("stored status").isEqualTo("COMPLETED");
            softly.assertThat(activityCountForTarget(eventId)).as("mutation activities").isZero();
            softly.assertAll();
        } finally {
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ?, description = ? WHERE id = ?",
                    originalPermission.get("label"), originalPermission.get("description"), permissionId
            );
        }
    }

    @Test
    @DisplayName("REQ-EVENT-009 and REQ-EVENT-010 - search applies audience visibility and effective-status filtering")
    void searchShouldApplyAudienceVisibilityAndEffectiveStatus() {
        AuthSession creator = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_GET_MEMBER", "EVENT_GET_COORD"
        );
        UUID publicEvent = createEvent(creator, "Public completed", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID memberEvent = createEvent(creator, "Member completed", permissionId("EVENT_GET_MEMBER"),
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID coordEvent = createEvent(creator, "Coordinator completed", permissionId("EVENT_GET_COORD"),
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        jdbcTemplate.update("UPDATE events SET status = CAST('SCHEDULED' AS event_status_enum) WHERE id IN (?, ?, ?)",
                publicEvent, memberEvent, coordEvent);
        AuthSession caller = newSessionWithPermissions("EVENT_SEARCH", "EVENT_GET_MEMBER");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("status", "COMPLETED", "EQUALS")))
                .post(EVENTS + "/search?size=100").then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id"))
                .contains(publicEvent.toString(), memberEvent.toString())
                .doesNotContain(coordEvent.toString());
        assertThat(response.<List<String>>path("items.status")).containsOnly("COMPLETED");
        assertThat(activityCountForTarget(publicEvent) + activityCountForTarget(memberEvent)
                + activityCountForTarget(coordEvent)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-009 - search distinguishes authentication from EVENT_SEARCH authorization")
    void searchShouldDistinguishAuthenticationFromAuthorization() {
        AuthSession caller = newSessionWithPermissions("EVENT_GET_MEMBER");
        Map<String, Object> emptySearch = searchPayload();

        assertThat(jsonRequest().body(emptySearch).post(EVENTS + "/search").statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(caller).body(emptySearch).post(EVENTS + "/search").statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("REQ-EVENT-010 - exact public filter catalog and every allowed comparison -> find the Event")
    void documentedEventSearchFiltersShouldFindTheTarget() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_SEARCH", "EVENT_GET_MEMBER"
        );
        String title = "Event search target " + UUID.randomUUID();
        UUID permissionId = permissionId("EVENT_GET_MEMBER");
        Instant begin = Instant.now().minusSeconds(7_200);
        Instant end = Instant.now().minusSeconds(3_600);
        UUID eventId = createEvent(caller, title, permissionId, begin, end);
        UUID locationId = jdbcTemplate.queryForObject(
                "SELECT gam_location_id FROM events WHERE id = ?",
                UUID.class,
                eventId
        );
        Instant storedBegin = jdbcTemplate.queryForObject(
                "SELECT begin_date FROM events WHERE id = ?",
                Timestamp.class,
                eventId
        ).toInstant();
        Instant storedEnd = jdbcTemplate.queryForObject(
                "SELECT end_date FROM events WHERE id = ?",
                Timestamp.class,
                eventId
        ).toInstant();

        List<Map<String, Object>> filters = List.of(
                filter("id", eventId.toString(), "EQUALS"),
                filter("id", List.of(UUID.randomUUID().toString(), eventId.toString()), "IN"),
                filter("title", title, "EQUALS"),
                filter("title", "SEARCH TARGET", "LIKE"),
                filter("description", "integration fixture", "LIKE"),
                filter("gamLocationId", locationId.toString(), "EQUALS"),
                filter("gamLocationId", List.of(locationId.toString()), "IN"),
                filter("requiredPermissionId", permissionId.toString(), "EQUALS"),
                filter("requiredPermissionId", List.of(permissionId.toString()), "IN"),
                filter("requiredPermissionCode", "EVENT_GET_MEMBER", "EQUALS"),
                filter("requiredPermissionCode", List.of("EVENT_GET_MEMBER", "EVENT_GET_COORD"), "IN"),
                filter("type", "GENERIC", "EQUALS"),
                filter("type", List.of("ORATORIO", "GENERIC"), "IN"),
                filter("status", "COMPLETED", "EQUALS"),
                filter("status", List.of("SCHEDULED", "COMPLETED"), "IN"),
                filter("beginDate", storedBegin.toString(), "GREATER_THAN_OR_EQUAL"),
                filter("beginDate", storedBegin.toString(), "LESS_THAN_OR_EQUAL"),
                filter("endDate", storedEnd.toString(), "GREATER_THAN_OR_EQUAL"),
                filter("endDate", storedEnd.toString(), "LESS_THAN_OR_EQUAL")
        );

        for (Map<String, Object> searchFilter : filters) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(searchPayload(searchFilter))
                    .post(EVENTS + "/search?size=100")
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("%s %s: %s",
                            searchFilter.get("field"),
                            searchFilter.get("comparisonMethod"),
                            response.asString())
                    .isEqualTo(200);
            assertThat(response.<List<String>>path("items.id"))
                    .as("%s %s", searchFilter.get("field"), searchFilter.get("comparisonMethod"))
                    .contains(eventId.toString());
        }
    }

    @Test
    @DisplayName("REQ-EVENT-010 and REQ-SEARCH-008/010 - unsupported methods and non-public fields -> safe errors")
    void invalidEventSearchCatalogEntriesShouldReturnSafeErrors() {
        AuthSession caller = newSessionWithPermissions("EVENT_SEARCH");
        List<Map<String, Object>> unsupported = List.of(
                filter("id", UUID.randomUUID().toString(), "LIKE"),
                filter("title", List.of("Title"), "IN"),
                filter("description", "Description", "EQUALS"),
                filter("gamLocationId", UUID.randomUUID().toString(), "LESS_THAN_OR_EQUAL"),
                filter("requiredPermissionId", UUID.randomUUID().toString(), "LIKE"),
                filter("requiredPermissionCode", "EVENT_GET_MEMBER", "LIKE"),
                filter("type", "GENERIC", "GREATER_THAN_OR_EQUAL"),
                filter("status", "SCHEDULED", "LIKE"),
                filter("beginDate", "2026-01-01T00:00:00Z", "EQUALS"),
                filter("endDate", "2026-01-01T00:00:00Z", "IN")
        );

        for (Map<String, Object> searchFilter : unsupported) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(searchPayload(searchFilter))
                    .post(EVENTS + "/search")
                    .then()
                    .extract();
            assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
            assertThat(response.<String>path("code")).isEqualTo("INVALID_SEARCH_FILTER");
            assertThat(response.<Map<String, Object>>path("details"))
                    .containsEntry("filterIndex", 0)
                    .containsEntry("field", searchFilter.get("field"))
                    .containsEntry("comparisonMethod", searchFilter.get("comparisonMethod"));
        }

        String persistenceField = "createdAt";
        ExtractableResponse<Response> unknown = authenticatedJsonRequest(caller)
                .body(searchPayload(filter(
                        persistenceField,
                        "2026-01-01T00:00:00Z",
                        "GREATER_THAN_OR_EQUAL"
                )))
                .post(EVENTS + "/search")
                .then()
                .extract();
        assertThat(unknown.statusCode()).as(unknown.asString()).isEqualTo(400);
        assertThat(unknown.<String>path("code")).isEqualTo("INVALID_SEARCH_FILTER");
        assertThat(unknown.<String>path("message")).isEqualTo("Unknown filter field.");
        assertThat(unknown.<Map<String, Object>>path("details"))
                .containsExactly(Map.entry("filterIndex", 0));
        assertThat(unknown.asString()).doesNotContain(persistenceField);
    }

    @Test
    @DisplayName("REQ-EVENT-006 and REQ-EVENT-010 - requested status sort uses effective rather than stored status")
    void statusSortShouldUseEffectiveStatus() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_SEARCH"
        );
        String titlePrefix = "Effective status sort " + UUID.randomUUID();
        UUID effectivelyCompleted = createEvent(
                caller, titlePrefix + " completed", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600)
        );
        UUID effectivelyScheduled = createEvent(
                caller, titlePrefix + " scheduled", null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)
        );
        jdbcTemplate.update(
                "UPDATE events SET status = CAST('SCHEDULED' AS event_status_enum) WHERE id = ?",
                effectivelyCompleted
        );
        jdbcTemplate.update(
                "UPDATE events SET status = CAST('COMPLETED' AS event_status_enum) WHERE id = ?",
                effectivelyScheduled
        );

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("title", titlePrefix, "LIKE")))
                .post(EVENTS + "/search?size=100&sort=status,asc").then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id"))
                .containsExactly(effectivelyScheduled.toString(), effectivelyCompleted.toString());
        assertThat(response.<List<String>>path("items.status"))
                .containsExactly("SCHEDULED", "COMPLETED");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("validLifecycleTransitions")
    @DisplayName("REQ-EVENT-011, REQ-EVENT-012 and REQ-EVENT-013 - every allowed command transition succeeds and audits once")
    void allowedLifecycleTransitionShouldSucceedAndAuditOnce(
            String source, String target, String route, String action, boolean reasonRequired
    ) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, source);
        clearActivities();

        ExtractableResponse<Response> response = lifecycleRequest(caller, eventId, route, target, reasonRequired);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("status")).isEqualTo(target);
        assertThat(activityCountFor(action, eventId)).isEqualTo(1);
        Map<String, Object> activity = eventActivity(action, eventId);
        Map<String, Object> metadata = JsonPath.from(activity.get("metadata").toString()).getMap("$");
        assertThat(metadata)
                .containsOnlyKeys("fromStatus", "toStatus")
                .containsEntry("fromStatus", source)
                .containsEntry("toStatus", target);
        assertThat(activity.get("reason")).isEqualTo(reasonRequired ? VALID_REASON.trim() : null);
        if ("CANCELLED".equals(target)) {
            assertThat(response.<String>path("cancellationReason")).isEqualTo(VALID_REASON.trim());
        } else {
            assertThat(response.<Object>path("cancellationReason")).isNull();
        }
    }

    @Test
    @DisplayName("REQ-EVENT-011 - unsupported and no-op transitions -> structured conflict without mutation or activity")
    void unsupportedLifecycleTransitionsShouldReturnStructuredConflict() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID scheduledId = createEventForStatus(caller, "SCHEDULED");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .patch(EVENTS + "/{id}/lock", scheduledId).then().extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");
        assertThat(response.<String>path("details.eventId")).isEqualTo(scheduledId.toString());
        assertThat(response.<String>path("details.currentStatus")).isEqualTo("SCHEDULED");
        assertThat(response.<String>path("details.requestedStatus")).isEqualTo("LOCKED");
        assertThat(eventStoredStatus(scheduledId)).isEqualTo("SCHEDULED");
        assertThat(activityCountForTarget(scheduledId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-011 and REQ-EVENT-018 - lock command cannot reopen a finalized Event")
    void lockCommandShouldRejectFinalizedEvent() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "FINALIZED");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .patch(EVENTS + "/{id}/lock", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");
        assertThat(response.<String>path("details.eventId")).isEqualTo(eventId.toString());
        assertThat(response.<String>path("details.currentStatus")).isEqualTo("FINALIZED");
        assertThat(response.<String>path("details.requestedStatus")).isEqualTo("LOCKED");
        assertThat(eventStoredStatus(eventId)).isEqualTo("FINALIZED");
        assertThat(activityCountForTarget(eventId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReasonBodies")
    @DisplayName("REQ-API-ERROR-002/003/004/005 and REQ-EVENT-013 - invalid cancellation reasons -> structured HTTP 400 before mutation")
    void invalidLifecycleReasonShouldBeRejected(
            String scenario,
            Map<String, Object> body,
            String expectedTopLevelCode,
            String expectedViolationCode,
            String rejectedValue
    ) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "SCHEDULED");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(body).patch(EVENTS + "/{id}/cancel", eventId).then().extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(400);
        assertThat(response.<String>path("code")).as(scenario).isEqualTo(expectedTopLevelCode);
        assertThat(response.<String>path("message"))
                .as(scenario + " safe top-level message")
                .isNotBlank()
                .doesNotContain("InvalidCommandException", "RequiredReason");
        assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        if (expectedViolationCode != null) {
            assertSingleValidationViolation(
                    response,
                    "body",
                    "/reason",
                    expectedViolationCode,
                    rejectedValue
            );
        } else {
            assertThat(response.jsonPath().getMap("details"))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "reason", "UNKNOWN_FIELD",
                            "location", "body"
                    ));
            assertThat(response.asString()).doesNotContain(rejectedValue);
        }
        assertThat(eventStoredStatus(eventId)).isEqualTo("SCHEDULED");
        assertThat(activityCountForTarget(eventId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unicodeReasonContracts")
    @DisplayName("REQ-ACTIVITY-008 and REQ-EVENT-013 - cancellation reason -> exact code-point normalization reused by domain and activity")
    void cancellationShouldReuseTheExactNormalizedUnicodeReason(
            String scenario,
            String submittedReason,
            String expectedReason,
            int expectedCodePoints
    ) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "SCHEDULED");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload(submittedReason))
                .patch(EVENTS + "/{id}/cancel", eventId)
                .then()
                .extract();

        assertThat(response.statusCode()).as(scenario).isEqualTo(200);
        assertThat(expectedReason.codePointCount(0, expectedReason.length()))
                .isEqualTo(expectedCodePoints);
        assertThat(response.<String>path("cancellationReason")).isEqualTo(expectedReason);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancellation_reason FROM events WHERE id = ?",
                String.class,
                eventId
        )).isEqualTo(expectedReason);
        assertThat(eventActivity("EVENT_CANCELLED", eventId).get("reason"))
                .isEqualTo(expectedReason);
    }

    @Test
    @DisplayName("REQ-EVENT-014 and REQ-EVENT-015 - audience-changing full replacement normalizes data, changes effective status, and audits changed fields")
    void audienceChangingReplacementShouldRequireVisibilityAndAuditChanges() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE", "EVENT_GET_COORD"
        );
        UUID eventId = createEvent(caller, "Before update", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID replacementLocation = createLocation(caller, "Replacement Event location");
        Map<String, Object> replacement = eventPayload(
                "  After update  ", replacementLocation, permissionId("EVENT_GET_COORD"),
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200)
        );
        replacement.put("reason", VALID_REASON);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(replacement).put(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("id")).isEqualTo(eventId.toString());
        assertThat(response.<String>path("title")).isEqualTo("After update");
        assertThat(response.<String>path("status")).isEqualTo("SCHEDULED");
        assertThat(response.<String>path("gamLocation.id")).isEqualTo(replacementLocation.toString());
        assertThat(response.<String>path("requiredPermission.code")).isEqualTo("EVENT_GET_COORD");
        Map<String, Object> activity = eventActivity("EVENT_UPDATED", eventId);
        assertThat(activity.get("reason")).isEqualTo(VALID_REASON.trim());
        Map<String, Object> metadata = JsonPath.from(activity.get("metadata").toString()).getMap("$");
        assertThat(metadata)
                .containsOnlyKeys("changedFields", "fromStatus", "toStatus")
                .containsEntry("fromStatus", "COMPLETED")
                .containsEntry("toStatus", "SCHEDULED");
        assertThat(metadata.get("changedFields"))
                .isEqualTo(List.of("title", "gamLocationId", "requiredPermissionId", "beginDate", "endDate"));
    }

    @Test
    @DisplayName("REQ-EVENT-015 and REQ-EVENT-020 - non-audience update -> optional reason and exact changedFields metadata")
    void nonAudienceUpdateShouldAllowOmittedReasonAndAuditOnlyStableChangedFields() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEvent(caller, "Before title edit", null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
        Map<String, Object> replacement = currentReplacement(eventId);
        replacement.put("title", "After title edit");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(replacement).put(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> activity = eventActivity("EVENT_UPDATED", eventId);
        assertThat(activity.get("reason")).isNull();
        Map<String, Object> metadata = JsonPath.from(activity.get("metadata").toString()).getMap("$");
        assertThat(metadata).containsOnlyKeys("changedFields");
        assertThat(metadata.get("changedFields")).isEqualTo(List.of("title"));
    }

    @Test
    @DisplayName("REQ-EVENT-015 - audience change without reason or exact new visibility -> rejection without mutation")
    void audienceChangeShouldRequireReasonAndExactNewVisibility() {
        AuthSession creator = newSessionWithPermissions("GAM_LOCATION_CREATE", "EVENT_CREATE");
        UUID eventId = createEvent(creator, "Public before audience change", null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
        AuthSession manager = newSessionWithPermissions("EVENT_MANAGE", "EVENT_GET_MEMBER");
        Map<String, Object> replacement = currentReplacement(eventId);
        replacement.put("requiredPermissionId", permissionId("EVENT_GET_COORD").toString());

        ExtractableResponse<Response> forbidden = authenticatedJsonRequest(manager)
                .body(withValue(replacement, "reason", VALID_REASON))
                .put(EVENTS + "/{id}", eventId).then().extract();
        assertThat(forbidden.statusCode()).isEqualTo(403);

        AuthSession visibleManager = newSessionWithPermissions("EVENT_MANAGE", "EVENT_GET_COORD");
        ExtractableResponse<Response> missingReason = authenticatedJsonRequest(visibleManager)
                .body(replacement).put(EVENTS + "/{id}", eventId).then().extract();
        assertThat(missingReason.statusCode()).isEqualTo(400);
        assertThat(eventRequiredPermission(eventId)).isNull();
        assertThat(activityCountFor("EVENT_UPDATED", eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-014 and REQ-EVENT-015 - normalized no-op replacement -> unchanged timestamp and no activity")
    void normalizedNoOpReplacementShouldNotPersistOrAudit() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEvent(caller, "No-op title", null,
                Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200));
        Timestamp updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM events WHERE id = ?", Timestamp.class, eventId);
        Map<String, Object> replacement = currentReplacement(eventId);
        replacement.put("title", "  No-op title  ");
        replacement.put("description", "  Event integration fixture  ");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(replacement).put(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("id")).isEqualTo(eventId.toString());
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM events WHERE id = ?", Timestamp.class, eventId))
                .isEqualTo(updatedAt);
        assertThat(activityCountFor("EVENT_UPDATED", eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-014 - LOCKED Event cannot be rescheduled and FINALIZED or CANCELLED Event cannot be edited")
    void administrativelyClosedEventsShouldEnforceEditingRules() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID locked = createEventForStatus(caller, "LOCKED");
        Map<String, Object> lockedReplacement = currentReplacement(locked);
        lockedReplacement.put("endDate", Instant.now().plusSeconds(7_200).toString());
        UUID finalized = createEventForStatus(caller, "FINALIZED");
        clearActivities();

        ExtractableResponse<Response> reschedule = authenticatedJsonRequest(caller)
                .body(lockedReplacement).put(EVENTS + "/{id}", locked).then().extract();
        ExtractableResponse<Response> immutable = authenticatedJsonRequest(caller)
                .body(currentReplacement(finalized)).put(EVENTS + "/{id}", finalized).then().extract();

        assertTransitionConflict(reschedule);
        assertTransitionConflict(immutable);
        assertThat(eventStoredStatus(locked)).isEqualTo("LOCKED");
        assertThat(eventStoredStatus(finalized)).isEqualTo("FINALIZED");
        assertThat(activityCount("EVENT_UPDATED")).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-019 - active Presence blocks deletion with structured count and no activity")
    void activePresenceShouldBlockDeletion() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "COMPLETED");
        UUID memberId = createMember(caller.accountId());
        createPresence(eventId, memberId, false);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON)).delete(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("EVENT_HAS_PRESENCES");
        assertThat(response.<String>path("details.eventId")).isEqualTo(eventId.toString());
        assertThat(response.<Number>path("details.activePresenceCount").longValue()).isEqualTo(1);
        assertThat(eventIsActive(eventId)).isTrue();
        assertThat(activityCountFor("EVENT_DELETED", eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-019 - blank deletion reason -> HTTP 400 without deletion or activity")
    void deletionShouldRequireNormalizedReason() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "COMPLETED");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload("   ")).delete(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(eventIsActive(eventId)).isTrue();
        assertThat(activityCountFor("EVENT_DELETED", eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-019 and ADR-0012 - removed Presences remain historical while eligible Event deletion succeeds and audits once")
    void removedPresenceShouldAllowDeletionAndRemainHistorical() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "COMPLETED");
        UUID memberId = createMember(caller.accountId());
        UUID presenceId = createPresence(eventId, memberId, true);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REASON)).delete(EVENTS + "/{id}", eventId).then().extract();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(eventIsActive(eventId)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM presences WHERE id = ?", Long.class, presenceId))
                .isEqualTo(1);
        Map<String, Object> activity = eventActivity("EVENT_DELETED", eventId);
        assertThat(activity.get("reason")).isEqualTo(VALID_REASON.trim());
        Map<String, Object> metadata = JsonPath.from(activity.get("metadata").toString()).getMap("$");
        assertThat(metadata)
                .containsOnlyKeys("type", "fromStatus", "gamLocationId")
                .containsEntry("type", "GENERIC")
                .containsEntry("fromStatus", "COMPLETED");
        assertThat(metadata.get("gamLocationId")).isNotNull();
        assertThat(metadata.toString()).doesNotContain("COMPLETED Event");
        assertResourceNotFound(jsonRequest().get(EVENTS + "/{id}", eventId).then().extract(), "Event");
    }

    @Test
    @DisplayName("REQ-EVENT-017 - every Generic mutation route rejects a specialized Event without mutation or activity")
    void specializedEventShouldRejectEveryGenericMutationRoute() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "COMPLETED");
        jdbcTemplate.update("UPDATE events SET type = CAST('MISSA' AS event_type_enum) WHERE id = ?", eventId);
        clearActivities();

        List<ExtractableResponse<Response>> responses = List.of(
                authenticatedJsonRequest(caller).body(currentReplacement(eventId))
                        .put(EVENTS + "/{id}", eventId).then().extract(),
                authenticatedJsonRequest(caller).body(reasonPayload(VALID_REASON))
                        .delete(EVENTS + "/{id}", eventId).then().extract(),
                authenticatedJsonRequest(caller).patch(EVENTS + "/{id}/lock", eventId).then().extract(),
                authenticatedJsonRequest(caller).patch(EVENTS + "/{id}/finalize", eventId).then().extract(),
                authenticatedJsonRequest(caller).body(Map.of("targetStatus", "COMPLETED", "reason", VALID_REASON))
                        .patch(EVENTS + "/{id}/reopen", eventId).then().extract(),
                authenticatedJsonRequest(caller).body(reasonPayload(VALID_REASON))
                        .patch(EVENTS + "/{id}/cancel", eventId).then().extract()
        );

        responses.forEach(response -> {
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.<String>path("code")).isEqualTo("EVENT_TYPE_NOT_MANAGEABLE");
        });
        assertThat(eventIsActive(eventId)).isTrue();
        assertThat(eventStoredType(eventId)).isEqualTo("MISSA");
        assertThat(activityCountForTarget(eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-012, REQ-EVENT-015 and REQ-EVENT-019 - mutations require EVENT_MANAGE and current audience visibility")
    void mutationsShouldRequireManagePermissionAndCurrentVisibility() {
        AuthSession creator = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_GET_COORD"
        );
        UUID eventId = createEvent(creator, "Restricted mutation", permissionId("EVENT_GET_COORD"),
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        AuthSession noManage = newSessionWithPermissions("EVENT_GET_COORD");
        AuthSession hiddenManager = newSessionWithPermissions("EVENT_MANAGE", "EVENT_GET_MEMBER");

        ExtractableResponse<Response> forbidden = authenticatedJsonRequest(noManage)
                .patch(EVENTS + "/{id}/finalize", eventId).then().extract();
        ExtractableResponse<Response> hidden = authenticatedJsonRequest(hiddenManager)
                .patch(EVENTS + "/{id}/finalize", eventId).then().extract();

        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertResourceNotFound(hidden, "Event");
        assertThat(eventStoredStatus(eventId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("REQ-EVENT-013 - status activity persistence failure rolls back the lifecycle mutation")
    void lifecycleActivityFailureShouldRollBackMutation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "SCHEDULED");
        clearActivities();
        failActivityWritesFor("EVENT_CANCELLED");

        try {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(reasonPayload(VALID_REASON)).patch(EVENTS + "/{id}/cancel", eventId).then().extract();
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        assertThat(eventStoredStatus(eventId)).isEqualTo("SCHEDULED");
        assertThat(activityCountFor("EVENT_CANCELLED", eventId)).isZero();
    }

    @Test
    @DisplayName("REQ-EVENT-018 and ADR-0012 - concurrent lock and finalize evaluate serialized latest state")
    void concurrentLifecycleCommandsShouldSerializeLatestStateAndActivities() throws Exception {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE"
        );
        UUID eventId = createEventForStatus(caller, "COMPLETED");
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> lock = concurrentRequest(
                    executor, ready, start,
                    () -> authenticatedJsonRequest(caller).patch(EVENTS + "/{id}/lock", eventId).then().extract()
            );
            Future<ExtractableResponse<Response>> finalize = concurrentRequest(
                    executor, ready, start,
                    () -> authenticatedJsonRequest(caller).patch(EVENTS + "/{id}/finalize", eventId).then().extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = List.of(lock.get(20, TimeUnit.SECONDS), finalize.get(20, TimeUnit.SECONDS));
            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .satisfies(statuses -> assertThat(statuses).isIn(List.of(200, 200), List.of(409, 200)));
            assertThat(responses.stream().filter(response -> response.statusCode() == 200).count()).isIn(1L, 2L);
            assertThat(eventStoredStatus(eventId)).isEqualTo("FINALIZED");
            assertThat(activityCountFor("EVENT_FINALIZED", eventId)).isEqualTo(1);
            assertThat(activityCountFor("EVENT_LOCKED", eventId))
                    .isEqualTo(responses.stream().filter(response -> response.statusCode() == 200)
                            .map(response -> response.<String>path("status")).filter("LOCKED"::equals).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-PRESENCE-006 and REQ-PRESENCE-007 - registration returns the complete compact Presence representation")
    void presenceRegistrationShouldReturnCompleteRepresentation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "PRESENCE_REGISTER"
        );
        UUID eventId = createEvent(
                caller, "Complete Presence response", null,
                Instant.parse("2026-07-01T17:00:00Z"), Instant.parse("2026-07-01T20:00:00Z")
        );
        UUID memberId = createMember(caller.accountId());

        ExtractableResponse<Response> response = registerPresence(
                caller, eventId, memberId, "  Arrived after the opening prayer  "
        );

        assertThat(response.statusCode()).isEqualTo(201);
        UUID presenceId = UUID.fromString(response.path("id"));
        presenceIds.add(presenceId);
        Map<String, Object> body = response.path("");
        Map<String, Object> member = response.path("member");
        Map<String, Object> event = response.path("event");
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(body).containsOnlyKeys("id", "member", "event", "observations", "registeredAt");
        softly.assertThat(member)
                .containsOnlyKeys("id", "firstName", "surname", "status")
                .containsEntry("id", memberId.toString())
                .containsEntry("firstName", "Ana")
                .containsEntry("surname", "Silva")
                .containsEntry("status", "ACTIVE");
        softly.assertThat(event)
                .containsOnlyKeys("id", "title", "beginDate", "endDate", "type", "status")
                .containsEntry("id", eventId.toString())
                .containsEntry("title", "Complete Presence response")
                .containsEntry("beginDate", "2026-07-01T17:00:00Z")
                .containsEntry("endDate", "2026-07-01T20:00:00Z")
                .containsEntry("type", "GENERIC")
                .containsEntry("status", "COMPLETED");
        softly.assertThat(response.<String>path("observations"))
                .isEqualTo("Arrived after the opening prayer");
        softly.assertThat(response.<String>path("registeredAt")).isNotBlank();
        softly.assertAll();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presenceObservationMetadataCases")
    @DisplayName("REQ-PRESENCE-006 - registration activity contains only the observation-presence indicator")
    void presenceRegistrationActivityShouldContainOnlyObservationPresenceIndicator(
            String scenario, String submittedObservations, boolean expectedObservationsPresent
    ) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "PRESENCE_REGISTER"
        );
        UUID eventId = createEvent(
                caller, "Presence observation activity " + scenario, null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600)
        );
        UUID memberId = createMember(caller.accountId());
        clearActivities();

        ExtractableResponse<Response> response = registerPresence(
                caller, eventId, memberId, submittedObservations
        );

        assertThat(response.statusCode()).isEqualTo(201);
        UUID presenceId = UUID.fromString(response.path("id"));
        presenceIds.add(presenceId);
        Map<String, Object> activity = jdbcTemplate.queryForMap(
                "SELECT metadata ->> 'memberId' AS \"memberId\", "
                        + "metadata ->> 'eventId' AS \"eventId\", "
                        + "(metadata ->> 'observationsPresent')::boolean AS \"observationsPresent\", "
                        + "jsonb_exists(metadata, 'observations') AS \"hasObservations\" "
                        + "FROM activity_logs WHERE action = 'PRESENCE_REGISTERED' AND target_id = ?",
                presenceId
        );
        assertThat(activity)
                .containsEntry("memberId", memberId.toString())
                .containsEntry("eventId", eventId.toString())
                .containsEntry("observationsPresent", expectedObservationsPresent)
                .containsEntry("hasObservations", false);
    }

    @Test
    @DisplayName("REQ-PRESENCE-005 - duplicate registration identifies the Event, Member, and active Presence")
    void duplicatePresenceRegistrationShouldReturnStructuredIdentifiers() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "PRESENCE_REGISTER"
        );
        UUID eventId = createEvent(
                caller, "Duplicate Presence identifiers", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600)
        );
        UUID memberId = createMember(caller.accountId());
        ExtractableResponse<Response> created = registerPresence(caller, eventId, memberId, null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID presenceId = UUID.fromString(created.path("id"));
        presenceIds.add(presenceId);

        ExtractableResponse<Response> duplicate = registerPresence(caller, eventId, memberId, null);

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.<String>path("code")).isEqualTo("PRESENCE_ALREADY_REGISTERED");
        assertThat(duplicate.<String>path("details.eventId")).isEqualTo(eventId.toString());
        assertThat(duplicate.<String>path("details.memberId")).isEqualTo(memberId.toString());
        assertThat(duplicate.<String>path("details.presenceId")).isEqualTo(presenceId.toString());
        assertThat(activityCountFor("PRESENCE_REGISTERED", presenceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-PRESENCE-002 - normalized observation at 2,000 characters is accepted")
    void normalizedMaximumObservationShouldBeAccepted() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "PRESENCE_REGISTER"
        );
        UUID eventId = createEvent(
                caller, "Presence normalized boundary", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600)
        );
        UUID memberId = createMember(caller.accountId());

        ExtractableResponse<Response> response = registerPresence(
                caller, eventId, memberId, " " + "x".repeat(2_000) + " "
        );

        assertThat(response.statusCode()).isEqualTo(201);
        if (response.path("id") != null) {
            presenceIds.add(UUID.fromString(response.path("id")));
        }
    }

    @Test
    @DisplayName("REQ-EVENT-018, REQ-EVENT-019 and ADR-0012 - Presence registration and Event deletion cannot commit incompatible history")
    void concurrentPresenceRegistrationAndEventDeletionShouldSerialize() throws Exception {
        AuthSession setup = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_MANAGE", "PRESENCE_REGISTER"
        );
        UUID eventId = createEvent(setup, "Concurrent Presence deletion", null,
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID memberId = createMember(setup.accountId());
        clearActivities();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> registration = concurrentRequest(
                    executor, ready, start,
                    () -> authenticatedJsonRequest(setup).body(Map.of("memberId", memberId.toString()))
                            .post(EVENTS + "/{id}/presences", eventId).then().extract()
            );
            Future<ExtractableResponse<Response>> deletion = concurrentRequest(
                    executor, ready, start,
                    () -> authenticatedJsonRequest(setup).body(reasonPayload(VALID_REASON))
                            .delete(EVENTS + "/{id}", eventId).then().extract()
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> registrationResponse = registration.get(20, TimeUnit.SECONDS);
            ExtractableResponse<Response> deletionResponse = deletion.get(20, TimeUnit.SECONDS);
            assertThat(List.of(registrationResponse.statusCode(), deletionResponse.statusCode()))
                    .isIn(List.of(201, 409), List.of(404, 204));
            if (registrationResponse.statusCode() == 201) {
                UUID presenceId = UUID.fromString(registrationResponse.path("id"));
                presenceIds.add(presenceId);
                assertThat(deletionResponse.<String>path("code")).isEqualTo("EVENT_HAS_PRESENCES");
            } else {
                assertResourceNotFound(registrationResponse, "Event");
            }
            assertThat(activePresenceOnDeletedEventCount(eventId)).isZero();
            assertThat(activityCount("PRESENCE_REGISTERED") + activityCountFor("EVENT_DELETED", eventId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Stream<Arguments> invalidCreationPayloads() {
        return Stream.of(
                Arguments.of("blank normalized title", Map.of("title", "   ")),
                Arguments.of("title above 255 characters", Map.of("title", "x".repeat(256))),
                Arguments.of("description above 10,000 characters", Map.of("description", "x".repeat(10_001))),
                Arguments.of("end equals begin", Map.of(
                        "beginDate", "2030-01-01T10:00:00Z", "endDate", "2030-01-01T10:00:00Z")),
                Arguments.of("client supplies type", Map.of("type", "ORATORIO")),
                Arguments.of("client supplies status", Map.of("status", "FINALIZED")),
                Arguments.of("client supplies cancellation reason", Map.of("cancellationReason", "not allowed"))
        );
    }

    private static Stream<Arguments> validLifecycleTransitions() {
        return Stream.of(
                Arguments.of("SCHEDULED", "CANCELLED", "cancel", "EVENT_CANCELLED", true),
                Arguments.of("COMPLETED", "LOCKED", "lock", "EVENT_LOCKED", false),
                Arguments.of("COMPLETED", "FINALIZED", "finalize", "EVENT_FINALIZED", false),
                Arguments.of("LOCKED", "COMPLETED", "reopen", "EVENT_REOPENED", true),
                Arguments.of("LOCKED", "FINALIZED", "finalize", "EVENT_FINALIZED", false),
                Arguments.of("FINALIZED", "LOCKED", "reopen", "EVENT_REOPENED", true),
                Arguments.of("FINALIZED", "COMPLETED", "reopen", "EVENT_REOPENED", true)
        );
    }

    private static Stream<Arguments> invalidReasonBodies() {
        String oversizedReason = "reason-secret-".repeat(155);
        return Stream.of(
                Arguments.of(
                        "missing reason",
                        Map.of(),
                        "VALIDATION_ERROR",
                        "REQUIRED",
                        null
                ),
                Arguments.of(
                        "null reason",
                        nullableReason(null),
                        "VALIDATION_ERROR",
                        "REQUIRED",
                        null
                ),
                Arguments.of(
                        "blank reason",
                        Map.of("reason", "   "),
                        "VALIDATION_ERROR",
                        "NOT_BLANK",
                        null
                ),
                Arguments.of(
                        "reason above 2,000 characters",
                        Map.of("reason", oversizedReason),
                        "VALIDATION_ERROR",
                        "SIZE",
                        oversizedReason
                ),
                Arguments.of(
                        "unknown property",
                        Map.of("reason", "valid", "unexpectedReasonProperty", true),
                        "MALFORMED_JSON",
                        null,
                        "unexpectedReasonProperty"
                )
        );
    }

    private static Stream<Arguments> unicodeReasonContracts() {
        String supplementaryReason = new String(Character.toChars(0x1F64F)).repeat(2_000);
        return Stream.of(
                Arguments.of(
                        "Unicode White_Space is removed",
                        "\u0085\u00A0\u202Fintent\u0085\u00A0\u202F",
                        "intent",
                        6
                ),
                Arguments.of(
                        "non-White_Space control characters are retained",
                        " \u001Cintent\u001C ",
                        "\u001Cintent\u001C",
                        8
                ),
                Arguments.of(
                        "2,000 supplementary code points are accepted",
                        "\u0085" + supplementaryReason + "\u0085",
                        supplementaryReason,
                        2_000
                )
        );
    }

    private void assertSingleValidationViolation(
            ExtractableResponse<Response> response,
            String location,
            String field,
            String code,
            String rejectedValue
    ) {
        assertThat(response.jsonPath().getMap("details")).containsOnlyKeys("violations");
        List<Map<String, Object>> violations = response.jsonPath().getList("details.violations");
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation)
                    .containsOnlyKeys("location", "field", "code", "message")
                    .containsEntry("location", location)
                    .containsEntry("field", field)
                    .containsEntry("code", code);
            assertThat(String.valueOf(violation.get("message"))).isNotBlank();
        });
        if (rejectedValue != null) {
            assertThat(response.asString()).doesNotContain(rejectedValue);
        }
    }

    private static Stream<Arguments> presenceObservationMetadataCases() {
        return Stream.of(
                Arguments.of("trimmed value", "  Arrived at the entrance  ", true),
                Arguments.of("explicit null", null, false)
        );
    }

    private UUID createLocation(AuthSession caller, String name) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(gamLocationPayload(name)).post("/gam-locations").then().statusCode(201).extract();
        UUID id = UUID.fromString(response.path("id"));
        trackGamLocation(id);
        return id;
    }

    private UUID createEvent(
            AuthSession caller, String title, UUID requiredPermissionId, Instant beginDate, Instant endDate
    ) {
        UUID locationId = createLocation(caller, "Location for " + title);
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(eventPayload(title, locationId, requiredPermissionId, beginDate, endDate))
                .post(EVENTS).then().statusCode(201).extract();
        UUID id = UUID.fromString(response.path("id"));
        trackEvent(id);
        return id;
    }

    private UUID createEventForStatus(AuthSession caller, String status) {
        boolean scheduled = "SCHEDULED".equals(status);
        UUID id = createEvent(caller, status + " Event", null,
                scheduled ? Instant.now().plusSeconds(3_600) : Instant.now().minusSeconds(7_200),
                scheduled ? Instant.now().plusSeconds(7_200) : Instant.now().minusSeconds(3_600));
        jdbcTemplate.update("UPDATE events SET status = CAST(? AS event_status_enum) WHERE id = ?", status, id);
        return id;
    }

    private static Map<String, Object> eventPayload(
            String title, UUID locationId, UUID permissionId, Instant beginDate, Instant endDate
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("description", "Event integration fixture");
        payload.put("gamLocationId", locationId.toString());
        payload.put("requiredPermissionId", permissionId == null ? null : permissionId.toString());
        payload.put("beginDate", beginDate.toString());
        payload.put("endDate", endDate.toString());
        return payload;
    }

    private ExtractableResponse<Response> lifecycleRequest(
            AuthSession caller, UUID eventId, String route, String target, boolean reasonRequired
    ) {
        if ("reopen".equals(route)) {
            return authenticatedJsonRequest(caller)
                    .body(Map.of("targetStatus", target, "reason", VALID_REASON))
                    .patch(EVENTS + "/{id}/" + route, eventId).then().extract();
        }
        if (reasonRequired) {
            return authenticatedJsonRequest(caller).body(reasonPayload(VALID_REASON))
                    .patch(EVENTS + "/{id}/" + route, eventId).then().extract();
        }
        return authenticatedJsonRequest(caller)
                .patch(EVENTS + "/{id}/" + route, eventId).then().extract();
    }

    private Map<String, Object> currentReplacement(UUID eventId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, description, gam_location_id AS \"gamLocationId\", "
                        + "required_permission_id AS \"requiredPermissionId\", "
                        + "begin_date AS \"beginDate\", end_date AS \"endDate\" FROM events WHERE id = ?",
                eventId
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

    private UUID createMember(UUID accountId) {
        UUID memberId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO members (id, account_id, first_name, surname, birth_date, phone_number, "
                        + "gam_entry_date, residential_city, contact_email, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Ana', 'Silva', DATE '2000-01-01', '+5519999999999', "
                        + "DATE '2020-01-01', 'Synthetic City', 'ana.fixture@example.com', "
                        + "CAST('ACTIVE' AS member_status_enum), ?, ?)",
                memberId, accountId, now, now
        );
        memberIds.add(memberId);
        return memberId;
    }

    private UUID createPresence(UUID eventId, UUID memberId, boolean removed) {
        UUID presenceId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO presences (id, member_id, event_id, observations, created_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, ?, 'Fixture', ?, ?, ?)",
                presenceId, memberId, eventId, now, now, removed ? now : null
        );
        presenceIds.add(presenceId);
        return presenceId;
    }

    private ExtractableResponse<Response> registerPresence(
            AuthSession caller, UUID eventId, UUID memberId, String observations
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", memberId.toString());
        payload.put("observations", observations);
        return authenticatedJsonRequest(caller)
                .body(payload).post(EVENTS + "/{id}/presences", eventId).then().extract();
    }

    private long activityCountFor(String action, UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = ? AND target_id = ?", Long.class, action, targetId
        ), "Expected activity count");
    }

    private void trackUnexpectedlyCreatedEvent(ExtractableResponse<Response> response) {
        if (response.statusCode() == 201 && response.path("id") != null) {
            trackEvent(UUID.fromString(response.path("id")));
        }
    }

    private long activityCountForTarget(UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE target_id = ?", Long.class, targetId
        ), "Expected target activity count");
    }

    private Map<String, Object> eventActivity(String action, UUID targetId) {
        return jdbcTemplate.queryForMap(
                "SELECT target_id, reason, metadata FROM activity_logs WHERE action = ? AND target_id = ?",
                action, targetId
        );
    }

    private String eventStoredStatus(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM events WHERE id = ?", String.class, eventId);
    }

    private String eventStoredType(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT type::text FROM events WHERE id = ?", String.class, eventId);
    }

    private UUID eventRequiredPermission(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT required_permission_id FROM events WHERE id = ?", UUID.class, eventId
        );
    }

    private boolean eventIsActive(UUID eventId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM events WHERE id = ?", Boolean.class, eventId
        ));
    }

    private long activePresenceOnDeletedEventCount(UUID eventId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM presences p JOIN events e ON e.id = p.event_id "
                        + "WHERE p.event_id = ? AND p.deleted_at IS NULL AND e.deleted_at IS NOT NULL",
                Long.class, eventId
        ), "Expected deleted-event presence count");
    }

    private void assertResourceNotFound(ExtractableResponse<Response> response, String resource) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.<String>path("details.resource")).isEqualTo(resource);
    }

    private static void assertTransitionConflict(ExtractableResponse<Response> response) {
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("EVENT_STATUS_TRANSITION_NOT_ALLOWED");
    }

    private static Map<String, Object> nullableReason(String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        return body;
    }

    private static Map<String, Object> withValue(Map<String, Object> source, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.put(key, value);
        return copy;
    }

    private Future<ExtractableResponse<Response>> concurrentRequest(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingRequest request
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return request.execute();
        });
    }

    @FunctionalInterface
    private interface ThrowingRequest {
        ExtractableResponse<Response> execute() throws Exception;
    }
}
