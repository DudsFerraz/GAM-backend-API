package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - GamLocation records and lifecycle")
class GamLocationApiIT extends MemberApiTestSupport {

    private static final String GAM_LOCATION_PATH = "/gam-locations";
    private static final String VALID_REMOVAL_REASON = "Venue is no longer available";
    private static final Set<String> GAM_LOCATION_FIELDS = Set.of(
            "id", "code", "systemManaged", "name", "street", "city", "state",
            "postalCode", "countryCode", "latitude", "longitude"
    );
    private static final List<SystemLocationExpectation> SYSTEM_LOCATION_CATALOG = List.of(
            new SystemLocationExpectation(
                    "DBSM",
                    "Dom Bosco São Mário",
                    "Av. Santa Rosa, 653 - Areião",
                    "Piracicaba",
                    "SP",
                    "13414-038",
                    "BR"
            ),
            new SystemLocationExpectation(
                    "DBA",
                    "Dom Bosco Assunção",
                    "Rua Boa Morte, 1835 - Centro",
                    "Piracicaba",
                    "SP",
                    "13400-140",
                    "BR"
            ),
            new SystemLocationExpectation(
                    "DBCA",
                    "Dom Bosco Cidade Alta",
                    "Rua Alfredo Guedes, 1199 - Bairro Alto",
                    "Piracicaba",
                    "SP",
                    "13419-080",
                    "BR"
            )
    );

    @Autowired
    private JdbcTemplate gamLocationJdbcTemplate;

    private final Set<UUID> createdGamLocationIds = new LinkedHashSet<>();
    private final Set<UUID> createdEventIds = new LinkedHashSet<>();

    @AfterEach
    void cleanupGamLocationFixtures() {
        for (UUID eventId : createdEventIds) {
            gamLocationJdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }

        String locationTable = locationTable();
        if (locationTable != null) {
            for (UUID locationId : createdGamLocationIds) {
                gamLocationJdbcTemplate.update("DELETE FROM " + locationTable + " WHERE id = ?", locationId);
            }
        }

        createdEventIds.clear();
        createdGamLocationIds.clear();
        gamLocationJdbcTemplate.update("DELETE FROM activity_logs");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 and REQ-GAM-LOCATION-011 - protected routes without authentication -> HTTP 401")
    void protectedGamLocationRoutesShouldRejectUnauthenticatedRequests() {
        UUID id = UUID.randomUUID();

        assertStatus(jsonRequest().body(validPayload("Unauthenticated create")).post(GAM_LOCATION_PATH), 401);
        assertStatus(jsonRequest().get(GAM_LOCATION_PATH), 401);
        assertStatus(jsonRequest().get(GAM_LOCATION_PATH + "/{id}", id), 401);
        assertStatus(jsonRequest().body(validPayload("Unauthenticated update"))
                .put(GAM_LOCATION_PATH + "/{id}", id), 401);
        assertStatus(jsonRequest().body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", id), 401);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-011 - authenticated caller without operation permission -> HTTP 403")
    void callerWithoutGamLocationPermissionShouldBeForbidden() {
        AuthSession visitor = newSession("VISITOR");
        UUID id = UUID.randomUUID();

        assertStatus(authenticatedJsonRequest(visitor).get(GAM_LOCATION_PATH), 403);
        assertStatus(authenticatedJsonRequest(visitor).get(GAM_LOCATION_PATH + "/{id}", id), 403);
        assertStatus(authenticatedJsonRequest(visitor).body(validPayload("Forbidden create"))
                .post(GAM_LOCATION_PATH), 403);
        assertStatus(authenticatedJsonRequest(visitor).body(validPayload("Forbidden update"))
                .put(GAM_LOCATION_PATH + "/{id}", id), 403);
        assertStatus(authenticatedJsonRequest(visitor).body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", id), 403);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-011 - MEMBER reads active GamLocations while lacking create and manage permissions")
    void memberShouldReadButNotChangeGamLocations() {
        AuthSession coordinator = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(coordinator, "Member-visible location");
        AuthSession member = newSession("MEMBER");

        assertStatus(authenticatedJsonRequest(member).get(GAM_LOCATION_PATH + "/{id}", id), 200);
        assertStatus(authenticatedJsonRequest(member).get(GAM_LOCATION_PATH), 200);
        assertStatus(authenticatedJsonRequest(member).body(validPayload("Member create"))
                .post(GAM_LOCATION_PATH), 403);
        assertStatus(authenticatedJsonRequest(member).body(validPayload("Member update"))
                .put(GAM_LOCATION_PATH + "/{id}", id), 403);
        assertStatus(authenticatedJsonRequest(member).body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", id), 403);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-001, REQ-GAM-LOCATION-002, REQ-GAM-LOCATION-003, REQ-GAM-LOCATION-006 and REQ-GAM-LOCATION-012 - normalized create -> complete record and one audit event")
    void normalizedCreateShouldReturnCompleteGamLocationAndAuditIt() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE");
        clearActivities();

        Map<String, Object> payload = validPayload("  Colégio Dom Bosco — Quadra  ");
        payload.put("street", "  Rua São José, 123  ");
        payload.put("city", "  Campinas  ");
        payload.put("state", "  Ontario  ");
        payload.put("postalCode", "  SW1A 1AA  ");
        payload.put("countryCode", "br");
        payload.remove("latitude");
        payload.remove("longitude");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(GAM_LOCATION_PATH)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(response.path("id"));
        createdGamLocationIds.add(id);
        assertPublicApiLocation(response, GAM_LOCATION_PATH + "/" + id);
        assertUuidV7(id);
        assertThat(response.jsonPath().getMap("$"))
                .containsOnlyKeys(GAM_LOCATION_FIELDS.toArray());
        assertThat(response.jsonPath().getMap("$")).containsEntry("id", id.toString());
        assertThat(response.<Object>path("code")).isNull();
        assertThat(response.<Boolean>path("systemManaged")).isFalse();
        assertThat(response.<String>path("name")).isEqualTo("Colégio Dom Bosco — Quadra");
        assertThat(response.<String>path("street")).isEqualTo("Rua São José, 123");
        assertThat(response.<String>path("city")).isEqualTo("Campinas");
        assertThat(response.<String>path("state")).isEqualTo("Ontario");
        assertThat(response.<String>path("postalCode")).isEqualTo("SW1A 1AA");
        assertThat(response.<String>path("countryCode")).isEqualTo("BR");
        assertThat(response.<Object>path("latitude")).isNull();
        assertThat(response.<Object>path("longitude")).isNull();

        assertThat(activityCount("GAM_LOCATION_CREATED")).isEqualTo(1);
        Map<String, Object> activity = activity("GAM_LOCATION_CREATED");
        assertThat(activity)
                .containsEntry("target_id", id)
                .containsEntry("reason", null);
        assertThat(activity.get("metadata").toString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-001 and REQ-GAM-LOCATION-CATALOG-003 - list -> exact current system catalog with ownership metadata")
    void currentSystemCatalogShouldBeListedWithExactApplicationOwnedMetadata() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_GET");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .get(GAM_LOCATION_PATH)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> systemLocations = response
                .<List<Map<String, Object>>>path("items")
                .stream()
                .filter(location -> Boolean.TRUE.equals(location.get("systemManaged")))
                .toList();
        assertThat(systemLocations).hasSize(SYSTEM_LOCATION_CATALOG.size());
        assertThat(systemLocations)
                .extracting(location -> location.get("code"))
                .containsExactlyInAnyOrder("DBSM", "DBA", "DBCA");

        for (SystemLocationExpectation expected : SYSTEM_LOCATION_CATALOG) {
            Map<String, Object> actual = systemLocations.stream()
                    .filter(location -> expected.code().equals(location.get("code")))
                    .findFirst()
                    .orElseThrow();
            assertThat(actual).containsOnlyKeys(GAM_LOCATION_FIELDS);
            assertThat(actual)
                    .containsEntry("code", expected.code())
                    .containsEntry("systemManaged", true)
                    .containsEntry("name", expected.name())
                    .containsEntry("street", expected.street())
                    .containsEntry("city", expected.city())
                    .containsEntry("state", expected.state())
                    .containsEntry("postalCode", expected.postalCode())
                    .containsEntry("countryCode", expected.countryCode());
            assertThat(actual.get("latitude")).isNull();
            assertThat(actual.get("longitude")).isNull();
        }
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-004 - current system record update and removal -> forbidden, unchanged, and unaudited")
    void productMutationShouldNotChangeOrAuditCurrentSystemLocation() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_GET", "GAM_LOCATION_MANAGE");
        Map<String, Object> before = currentSystemLocation(caller, "DBSM");
        UUID id = UUID.fromString(before.get("id").toString());
        clearActivities();

        ExtractableResponse<Response> updateResponse = authenticatedJsonRequest(caller)
                .body(validPayload("Attempted system location replacement"))
                .put(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();
        ExtractableResponse<Response> removalResponse = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(updateResponse.statusCode()).isEqualTo(403);
        assertThat(updateResponse.<String>path("code")).isEqualTo("FORBIDDEN_OPERATION");
        assertThat(removalResponse.statusCode()).isEqualTo(403);
        assertThat(removalResponse.<String>path("code")).isEqualTo("FORBIDDEN_OPERATION");
        assertThat(currentSystemLocation(caller, "DBSM")).isEqualTo(before);
        assertThat(activityCount("GAM_LOCATION_UPDATED")).isZero();
        assertThat(activityCount("GAM_LOCATION_REMOVED")).isZero();
    }

    @Test
    @DisplayName("ADR-0009 - persisted duplicate identity removes accents and canonicalizes whitespace")
    void persistedDuplicateIdentityShouldBeAccentInsensitive() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE");
        Map<String, Object> payload = validPayload("  Salão  São José  ");
        payload.put("street", "  Rua  São José, 123  ");
        payload.put("city", "  São Paulo  ");
        payload.put("state", "  São Paulo  ");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(GAM_LOCATION_PATH)
                .then()
                .statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        createdGamLocationIds.add(id);

        Map<String, Object> identity = gamLocationJdbcTemplate.queryForMap(
                "SELECT identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code "
                        + "FROM gam_locations WHERE id = ?",
                id
        );

        assertThat(identity)
                .containsEntry("identity_name", "salao sao jose")
                .containsEntry("identity_street", "rua sao jose, 123")
                .containsEntry("identity_city", "sao paulo")
                .containsEntry("identity_state", "sao paulo")
                .containsEntry("identity_postal_code", "01000-000")
                .containsEntry("identity_country_code", "br");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreatePayloads")
    @DisplayName("REQ-GAM-LOCATION-002, REQ-GAM-LOCATION-003 and REQ-GAM-LOCATION-004 - invalid create input -> HTTP 400 and no record")
    void invalidCreatePayloadShouldBeRejected(String scenario, Map<String, Object> payload) {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(GAM_LOCATION_PATH)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(activityCount("GAM_LOCATION_CREATED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - legacy Location route -> no compatibility alias")
    void legacyLocationRouteShouldNotRemainAnAlias() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_GET");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get("/locations")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("REQ-EVENT-010 - gamLocationId Event search filter -> matching Events only")
    void gamLocationIdEventSearchFilterShouldMatchOnlyLinkedEvents() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_SEARCH"
        );
        UUID targetGamLocationId = createGamLocation(caller, "Search target location");
        UUID otherGamLocationId = createGamLocation(caller, "Search other location");
        UUID matchingEventId = createGenericEvent(caller, targetGamLocationId, "Matching location event");
        UUID otherEventId = createGenericEvent(caller, otherGamLocationId, "Other location event");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("gamLocationId", targetGamLocationId.toString(), "EQUALS")))
                .post("/events/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id"))
                .contains(matchingEventId.toString())
                .doesNotContain(otherEventId.toString());
    }

    @Test
    @DisplayName("REQ-EVENT-010 - gamLocationId IN filter -> Events linked to any selected GamLocation")
    void gamLocationIdInEventSearchFilterShouldMatchEventsLinkedToAnySelectedLocation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "EVENT_CREATE", "EVENT_SEARCH"
        );
        UUID firstGamLocationId = createGamLocation(caller, "First IN search location");
        UUID secondGamLocationId = createGamLocation(caller, "Second IN search location");
        UUID excludedGamLocationId = createGamLocation(caller, "Excluded IN search location");
        UUID firstEventId = createGenericEvent(caller, firstGamLocationId, "First IN search event");
        UUID secondEventId = createGenericEvent(caller, secondGamLocationId, "Second IN search event");
        UUID excludedEventId = createGenericEvent(caller, excludedGamLocationId, "Excluded IN search event");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter(
                        "gamLocationId",
                        List.of(firstGamLocationId.toString(), secondGamLocationId.toString()),
                        "IN"
                )))
                .post("/events/search?size=100")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<List<String>>path("items.id"))
                .contains(firstEventId.toString(), secondEventId.toString())
                .doesNotContain(excludedEventId.toString());
    }

    @Test
    @DisplayName("REQ-EVENT-010 - legacy locationId Event search filter -> HTTP 400")
    void legacyLocationIdEventSearchFilterShouldBeRejected() {
        AuthSession caller = newSessionWithPermissions("EVENT_SEARCH");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(searchPayload(filter("locationId", UUID.randomUUID().toString(), "EQUALS")))
                .post("/events/search")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 and REQ-GAM-LOCATION-CATALOG-003 - read-only or unknown request properties -> HTTP 400")
    void unknownPropertiesShouldBeRejected() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_MANAGE"
        );
        Map<String, Object> createPayload = validPayload("Unknown property create");
        createPayload.put("id", UUID.randomUUID());

        assertStatus(authenticatedJsonRequest(caller).body(createPayload).post(GAM_LOCATION_PATH), 400);
        assertStatus(authenticatedJsonRequest(caller)
                .body(withValue(validPayload("System code create"), "code", "DBSM"))
                .post(GAM_LOCATION_PATH), 400);
        assertStatus(authenticatedJsonRequest(caller)
                .body(withValue(validPayload("System ownership create"), "systemManaged", true))
                .post(GAM_LOCATION_PATH), 400);

        UUID id = createGamLocation(caller, "Unknown property update");
        Map<String, Object> updatePayload = validPayload("Unknown property update");
        updatePayload.put("events", List.of());

        assertStatus(authenticatedJsonRequest(caller).body(updatePayload)
                .put(GAM_LOCATION_PATH + "/{id}", id), 400);
        assertStatus(authenticatedJsonRequest(caller)
                .body(withValue(validPayload("System code update"), "code", "DBA"))
                .put(GAM_LOCATION_PATH + "/{id}", id), 400);
        assertStatus(authenticatedJsonRequest(caller)
                .body(withValue(validPayload("System ownership update"), "systemManaged", true))
                .put(GAM_LOCATION_PATH + "/{id}", id), 400);
        assertThat(activityCount("GAM_LOCATION_UPDATED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 - case, accent, and repeated-whitespace duplicate -> conflict identifying existing UUID")
    void normalizedDuplicateShouldReturnExistingIdentifier() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET"
        );
        Map<String, Object> original = validPayload("Salão  São José");
        original.put("street", "Rua A, 10");
        UUID existingId = createGamLocation(caller, original);

        Map<String, Object> duplicate = validPayload("salao são josé");
        duplicate.put("street", "Rua A, 10");
        duplicate.put("latitude", BigDecimal.ZERO);
        duplicate.put("longitude", BigDecimal.ZERO);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(duplicate)
                .post(GAM_LOCATION_PATH)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("GAM_LOCATION_ALREADY_EXISTS");
        assertThat(response.asString()).contains(existingId.toString());
        assertThat(activityCount("GAM_LOCATION_CREATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-006 and REQ-GAM-LOCATION-007 - ordinary create matching a retired system row -> duplicate conflict")
    void retiredSystemLocationIdentityShouldRemainReservedFromOrdinaryCreation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET"
        );
        SystemLocationExpectation retired = SYSTEM_LOCATION_CATALOG.stream()
                .filter(location -> "DBA".equals(location.code()))
                .findFirst()
                .orElseThrow();
        UUID retiredId = UUID.fromString(currentSystemLocation(caller, retired.code())
                .get("id").toString());
        gamLocationJdbcTemplate.update(
                "UPDATE gam_locations SET catalog_current = FALSE WHERE id = ?",
                retiredId
        );
        clearActivities();

        try {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(systemLocationPayload(retired))
                    .post(GAM_LOCATION_PATH)
                    .then()
                    .extract();

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.<String>path("code"))
                    .isEqualTo("GAM_LOCATION_ALREADY_EXISTS");
            assertThat(response.asString()).contains(retiredId.toString());
            assertThat(activityCount("GAM_LOCATION_CREATED")).isZero();
        } finally {
            gamLocationJdbcTemplate.update(
                    "UPDATE gam_locations SET catalog_current = TRUE WHERE id = ?",
                    retiredId
            );
        }
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 - punctuation remains significant in duplicate identity")
    void punctuationDifferenceShouldAllowASecondActiveGamLocation() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE");
        Map<String, Object> commaAddress = validPayload("Punctuation-sensitive place");
        commaAddress.put("street", "Rua A, 10");
        createGamLocation(caller, commaAddress);

        Map<String, Object> spaceAddress = validPayload("Punctuation-sensitive place");
        spaceAddress.put("street", "Rua A 10");
        UUID secondId = createGamLocation(caller, spaceAddress);

        assertThat(secondId).isNotNull();
        assertThat(activityCount("GAM_LOCATION_CREATED")).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 - soft-deleted identity -> reusable for a new active record")
    void softDeletedIdentityShouldNotBlockRecreation() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_MANAGE", "GAM_LOCATION_GET"
        );
        Map<String, Object> payload = validPayload("Reusable after removal");
        UUID deletedId = createGamLocation(caller, payload);

        assertStatus(authenticatedJsonRequest(caller).body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", deletedId), 204);
        UUID recreatedId = createGamLocation(caller, payload);

        assertThat(recreatedId).isNotEqualTo(deletedId);
        assertStatus(authenticatedJsonRequest(caller).get(GAM_LOCATION_PATH + "/{id}", deletedId), 404);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-008 and REQ-GAM-LOCATION-011 - active get and deterministic list -> complete active-only page")
    void getAndListShouldExposeOnlyActiveRecordsInDeterministicOrder() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID firstId = createGamLocation(caller, "A list location");
        UUID secondId = createGamLocation(caller, "B list location");
        UUID removedId = createGamLocation(caller, "C removed list location");
        assertStatus(authenticatedJsonRequest(caller).body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", removedId), 204);

        ExtractableResponse<Response> getResponse = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", firstId)
                .then()
                .extract();
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.jsonPath().getMap("$")).containsOnlyKeys(GAM_LOCATION_FIELDS.toArray());

        ExtractableResponse<Response> deletedGet = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", removedId)
                .then()
                .extract();
        assertNotFoundGamLocation(deletedGet, removedId);

        UUID missingId = UUID.randomUUID();
        ExtractableResponse<Response> missingGet = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", missingId)
                .then()
                .extract();
        assertNotFoundGamLocation(missingGet, missingId);

        ExtractableResponse<Response> listResponse = authenticatedJsonRequest(caller)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .get(GAM_LOCATION_PATH)
                .then()
                .extract();
        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.jsonPath().getMap("$")).containsOnlyKeys(
                "items", "page", "size", "totalElements", "totalPages", "first", "last"
        );
        List<String> names = listResponse.jsonPath().getList("items.name");
        assertThat(names).contains("A list location", "B list location").doesNotContain("C removed list location");
        assertThat(names).isSorted();
        assertThat(names).containsSubsequence("A list location", "B list location");
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-009 and REQ-GAM-LOCATION-012 - changed full replacement -> clears optional fields and audits changed names")
    void changedUpdateShouldReplaceAllMutableFieldsAndAuditChangedNames() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(caller, "Update before");
        clearActivities();

        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("name", "  Update after  ");
        replacement.put("city", "  Toronto  ");
        replacement.put("state", "  Ontario  ");
        replacement.put("countryCode", "ca");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(replacement)
                .put(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("id")).isEqualTo(id.toString());
        assertThat(response.<String>path("name")).isEqualTo("Update after");
        assertThat(response.<String>path("city")).isEqualTo("Toronto");
        assertThat(response.<String>path("state")).isEqualTo("Ontario");
        assertThat(response.<String>path("countryCode")).isEqualTo("CA");
        assertThat(response.<Object>path("street")).isNull();
        assertThat(response.<Object>path("postalCode")).isNull();
        assertThat(response.<Object>path("latitude")).isNull();
        assertThat(response.<Object>path("longitude")).isNull();
        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys(GAM_LOCATION_FIELDS.toArray());

        assertThat(activityCount("GAM_LOCATION_UPDATED")).isEqualTo(1);
        Map<String, Object> activity = activity("GAM_LOCATION_UPDATED");
        assertThat(activity).containsEntry("target_id", id).containsEntry("reason", null);
        assertThat(activity.get("metadata").toString()).contains("name", "city", "state", "countryCode");
        assertThat(activity.get("metadata").toString()).doesNotContain("Rua São José", "01000-000");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-009 and REQ-GAM-LOCATION-012 - equivalent normalized replacement -> no mutation and no update audit")
    void equivalentNormalizedUpdateShouldBeIdempotentWithoutAuditNoise() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        Map<String, Object> original = validPayload("Idempotent location");
        UUID id = createGamLocation(caller, original);
        clearActivities();

        Map<String, Object> equivalent = validPayload("  Idempotent location  ");
        equivalent.put("street", "  Rua São José, 123  ");
        equivalent.put("countryCode", "br");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(equivalent)
                .put(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.<String>path("id")).isEqualTo(id.toString());
        assertThat(activityCount("GAM_LOCATION_UPDATED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 and REQ-GAM-LOCATION-009 - duplicate update -> conflict and unchanged target")
    void duplicateUpdateShouldLeaveTargetUnchanged() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID firstId = createGamLocation(caller, "First update target");
        UUID secondId = createGamLocation(caller, "Second update target");
        Map<String, Object> before = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", secondId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        Map<String, Object> duplicate = validPayload("First update target");

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(duplicate)
                .put(GAM_LOCATION_PATH + "/{id}", secondId)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("GAM_LOCATION_ALREADY_EXISTS");
        assertThat(response.asString()).contains(firstId.toString());
        Map<String, Object> after = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", secondId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        assertThat(after).isEqualTo(before);
        assertThat(activityCount("GAM_LOCATION_UPDATED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-009 - missing and soft-deleted update target -> RESOURCE_NOT_FOUND")
    void updateOfMissingOrDeletedTargetShouldReturnNotFound() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID missingId = UUID.randomUUID();
        assertNotFoundGamLocation(authenticatedJsonRequest(caller).body(validPayload("Missing update"))
                .put(GAM_LOCATION_PATH + "/{id}", missingId).then().extract(), missingId);

        UUID deletedId = createGamLocation(caller, "Deleted update target");
        assertStatus(authenticatedJsonRequest(caller).body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", deletedId), 204);
        assertNotFoundGamLocation(authenticatedJsonRequest(caller).body(validPayload("Deleted update"))
                .put(GAM_LOCATION_PATH + "/{id}", deletedId).then().extract(), deletedId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRemovalBodies")
    @DisplayName("REQ-GAM-LOCATION-010 - invalid removal body -> HTTP 400 and active record remains")
    void invalidRemovalBodyShouldNotRemoveLocation(String scenario, Object body) {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(caller, "Invalid removal body");
        clearActivities();

        var request = authenticatedJsonRequest(caller);
        if (body != null) {
            request.body(body);
        }

        ExtractableResponse<Response> response = request
                .delete(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(400);
        assertStatus(authenticatedJsonRequest(caller).get(GAM_LOCATION_PATH + "/{id}", id), 200);
        assertThat(activityCount("GAM_LOCATION_REMOVED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-010 and REQ-GAM-LOCATION-012 - remove unused location -> soft delete, normalized reason, and one audit event")
    void removingUnusedLocationShouldSoftDeleteAndAuditNormalizedReason() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(caller, "Removable location");
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Venue is no longer available  "))
                .delete(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.asString()).isEmpty();
        assertNotFoundGamLocation(authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", id).then().extract(), id);
        assertThat(activityCount("GAM_LOCATION_REMOVED")).isEqualTo(1);
        Map<String, Object> activity = activity("GAM_LOCATION_REMOVED");
        assertThat(activity)
                .containsEntry("target_id", id)
                .containsEntry("reason", "Venue is no longer available");
        assertThat(activity.get("metadata").toString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-010 - any Event reference, including a soft-deleted Event, blocks removal")
    void referencedLocationShouldNotBeRemoved() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE", "EVENT_CREATE"
        );
        UUID id = createGamLocation(caller, "Referenced location");
        UUID activeEventId = createGenericEvent(caller, id, "Active reference");
        UUID historicalEventId = createGenericEvent(caller, id, "Historical reference");
        softDeleteEvent(historicalEventId);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload(VALID_REMOVAL_REASON))
                .delete(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.<String>path("code")).isEqualTo("GAM_LOCATION_IN_USE");
        Map<String, Object> details = response.path("details");
        assertThat(details)
                .containsEntry("resource", "GamLocation")
                .containsEntry("identifier", id.toString());
        assertThat(details.values()).contains(2);
        assertStatus(authenticatedJsonRequest(caller).get(GAM_LOCATION_PATH + "/{id}", id), 200);
        assertThat(activityCount("GAM_LOCATION_REMOVED")).isZero();
        assertThat(activeEventId).isNotEqualTo(historicalEventId);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-001 and REQ-EVENT-004 - updating a referenced location -> Event reads corrected shared values")
    void referencedLocationUpdateShouldBeVisibleFromEventRead() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE", "EVENT_CREATE", "EVENT_GET_MEMBER"
        );
        UUID id = createGamLocation(caller, "Shared place before");
        UUID eventId = createGenericEvent(caller, id, "Shared place event");
        Map<String, Object> update = validPayload("Shared place after");

        assertStatus(authenticatedJsonRequest(caller).body(update)
                .put(GAM_LOCATION_PATH + "/{id}", id), 200);

        ExtractableResponse<Response> eventResponse = authenticatedJsonRequest(caller)
                .get("/events/{id}", eventId)
                .then()
                .extract();
        assertThat(eventResponse.statusCode()).isEqualTo(200);
        assertThat(eventResponse.asString()).contains(id.toString(), "Shared place after");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-012 - create audit persistence failure -> business mutation rolls back")
    void createActivityFailureShouldRollBackGamLocation() {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE", "GAM_LOCATION_GET");
        String uniqueName = "Create audit rollback " + UUID.randomUUID();
        clearActivities();
        failActivityWritesFor("GAM_LOCATION_CREATED");

        try {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(validPayload(uniqueName))
                    .post(GAM_LOCATION_PATH)
                    .then()
                    .extract();
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        ExtractableResponse<Response> list = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH)
                .then()
                .extract();
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.asString()).doesNotContain(uniqueName);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-012 - update audit persistence failure -> replacement rolls back")
    void updateActivityFailureShouldRollBackReplacement() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(caller, "Update audit before");
        clearActivities();
        failActivityWritesFor("GAM_LOCATION_UPDATED");

        try {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(validPayload("Update audit after"))
                    .put(GAM_LOCATION_PATH + "/{id}", id)
                    .then()
                    .extract();
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        ExtractableResponse<Response> get = authenticatedJsonRequest(caller)
                .get(GAM_LOCATION_PATH + "/{id}", id)
                .then()
                .extract();
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.<String>path("name")).isEqualTo("Update audit before");
        assertThat(activityCount("GAM_LOCATION_UPDATED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-012 - remove audit persistence failure -> soft delete rolls back")
    void removeActivityFailureShouldRollBackSoftDelete() {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID id = createGamLocation(caller, "Remove audit rollback");
        clearActivities();
        failActivityWritesFor("GAM_LOCATION_REMOVED");

        try {
            ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                    .body(reasonPayload(VALID_REMOVAL_REASON))
                    .delete(GAM_LOCATION_PATH + "/{id}", id)
                    .then()
                    .extract();
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }

        assertStatus(authenticatedJsonRequest(caller).get(GAM_LOCATION_PATH + "/{id}", id), 200);
        assertThat(activityCount("GAM_LOCATION_REMOVED")).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 and ADR-0009 - concurrent equivalent creates -> exactly one record and one duplicate conflict")
    void concurrentEquivalentCreatesShouldCommitExactlyOneGamLocation() throws Exception {
        AuthSession caller = newSessionWithPermissions("GAM_LOCATION_CREATE");
        Map<String, Object> firstPayload = validPayload("Concurrent Salão");
        Map<String, Object> secondPayload = validPayload("concurrent salão");
        secondPayload.put("street", "  " + secondPayload.get("street") + "  ");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<ExtractableResponse<Response>>> attempts = List.of(
                    submitConcurrentCreate(executor, ready, start, caller, firstPayload),
                    submitConcurrentCreate(executor, ready, start, caller, secondPayload)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = attempts.stream()
                    .map(this::getConcurrentResponse)
                    .toList();

            assertThat(responses).extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(201, 409);
            responses.stream()
                    .filter(response -> response.statusCode() == 201)
                    .map(response -> UUID.fromString(response.path("id")))
                    .forEach(createdGamLocationIds::add);
            ExtractableResponse<Response> conflictResponse = responses.stream()
                    .filter(response -> response.statusCode() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflictResponse.<String>path("code")).isEqualTo("GAM_LOCATION_ALREADY_EXISTS");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-013 and ADR-0010 - concurrent Event linking and user-managed location removal -> no invalid committed reference")
    void concurrentEventLinkingAndRemovalShouldSerialize() throws Exception {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE", "EVENT_CREATE"
        );
        UUID locationId = createGamLocation(caller, "Concurrent link and removal");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> eventAttempt = submitConcurrentEventCreate(
                    executor, ready, start, caller, locationId
            );
            Future<ExtractableResponse<Response>> removalAttempt = submitConcurrentRemoval(
                    executor, ready, start, caller, locationId
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> eventResponse = getConcurrentResponse(eventAttempt);
            ExtractableResponse<Response> removalResponse = getConcurrentResponse(removalAttempt);
            int eventStatus = eventResponse.statusCode();
            int removalStatus = removalResponse.statusCode();

            assertThat(eventStatus).isIn(201, 404);
            assertThat(removalStatus).isIn(204, 409);
            assertThat(eventStatus == 201 && removalStatus == 204).isFalse();

            if (eventStatus == 201) {
                UUID eventId = UUID.fromString(eventResponse.path("id"));
                createdEventIds.add(eventId);
                assertThat(removalStatus).isEqualTo(409);
                assertStatus(authenticatedJsonRequest(caller).get(GAM_LOCATION_PATH + "/{id}", locationId), 200);
            } else {
                assertThat(removalStatus).isEqualTo(204);
                assertNotFoundGamLocation(authenticatedJsonRequest(caller)
                        .get(GAM_LOCATION_PATH + "/{id}", locationId).then().extract(), locationId);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-013 and ADR-0010 - concurrent update and removal -> removal remains final")
    void concurrentUpdateAndRemovalShouldNotResurrectLocation() throws Exception {
        AuthSession caller = newSessionWithPermissions(
                "GAM_LOCATION_CREATE", "GAM_LOCATION_GET", "GAM_LOCATION_MANAGE"
        );
        UUID locationId = createGamLocation(caller, "Concurrent update before");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ExtractableResponse<Response>> updateAttempt = submitConcurrentUpdate(
                    executor, ready, start, caller, locationId
            );
            Future<ExtractableResponse<Response>> removalAttempt = submitConcurrentRemoval(
                    executor, ready, start, caller, locationId
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ExtractableResponse<Response> updateResponse = getConcurrentResponse(updateAttempt);
            ExtractableResponse<Response> removalResponse = getConcurrentResponse(removalAttempt);

            assertThat(removalResponse.statusCode()).isEqualTo(204);
            assertThat(updateResponse.statusCode()).isIn(200, 404);
            assertNotFoundGamLocation(authenticatedJsonRequest(caller)
                    .get(GAM_LOCATION_PATH + "/{id}", locationId).then().extract(), locationId);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Stream<Arguments> invalidCreatePayloads() {
        return Stream.of(
                Arguments.of("blank required name", withValue(validPayload("valid"), "name", "   ")),
                Arguments.of("required city with line break", withValue(validPayload("valid"), "city", "Campinas\nCentro")),
                Arguments.of("required state beyond 50 characters", withValue(validPayload("valid"), "state", "x".repeat(51))),
                Arguments.of("name beyond 255 characters", withValue(validPayload("valid"), "name", "x".repeat(256))),
                Arguments.of("street beyond 255 characters", withValue(validPayload("valid"), "street", "x".repeat(256))),
                Arguments.of("postal code beyond 20 characters", withValue(validPayload("valid"), "postalCode", "x".repeat(21))),
                Arguments.of("alpha-3 country code", withValue(validPayload("valid"), "countryCode", "BRA")),
                Arguments.of("country name", withValue(validPayload("valid"), "countryCode", "Brazil")),
                Arguments.of("unknown country code", withValue(validPayload("valid"), "countryCode", "ZZ")),
                Arguments.of("latitude without longitude", without(validPayload("valid"), "longitude")),
                Arguments.of("longitude without latitude", without(validPayload("valid"), "latitude")),
                Arguments.of("latitude above upper bound", withValue(validPayload("valid"), "latitude", new BigDecimal("90.00000001"))),
                Arguments.of("longitude below lower bound", withValue(validPayload("valid"), "longitude", new BigDecimal("-180.00000001"))),
                Arguments.of("latitude with excess precision", withValue(validPayload("valid"), "latitude", new BigDecimal("1.123456789"))),
                Arguments.of("quoted latitude string", withValue(validPayload("valid"), "latitude", "-22.9068"))
        );
    }

    private static Stream<Arguments> invalidRemovalBodies() {
        return Stream.of(
                Arguments.of("missing body", null),
                Arguments.of("null reason", reasonPayload(null)),
                Arguments.of("blank reason", reasonPayload("   ")),
                Arguments.of("reason beyond 2,000 characters", reasonPayload("x".repeat(2001))),
                Arguments.of("unknown property", Map.of("reason", VALID_REMOVAL_REASON, "extra", "not allowed"))
        );
    }

    private UUID createGamLocation(AuthSession caller, String name) {
        return createGamLocation(caller, validPayload(name));
    }

    private UUID createGamLocation(AuthSession caller, Map<String, Object> payload) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post(GAM_LOCATION_PATH)
                .then()
                .statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        createdGamLocationIds.add(id);
        return id;
    }

    private Map<String, Object> currentSystemLocation(AuthSession caller, String code) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .get(GAM_LOCATION_PATH)
                .then()
                .extract();
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> location = response
                .<List<Map<String, Object>>>path("items")
                .stream()
                .filter(candidate -> code.equals(candidate.get("code")))
                .findFirst()
                .orElse(null);
        assertThat(location)
                .as("Expected current system GamLocation with code %s", code)
                .isNotNull();
        return location;
    }

    private UUID createGenericEvent(AuthSession caller, UUID locationId, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("description", "Event referencing a shared GamLocation");
        payload.put("gamLocationId", locationId.toString());
        payload.put("beginDate", Instant.now().plusSeconds(3600).toString());
        payload.put("endDate", Instant.now().plusSeconds(7200).toString());

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(payload)
                .post("/events")
                .then()
                .statusCode(201)
                .extract();
        UUID eventId = UUID.fromString(response.path("id"));
        createdEventIds.add(eventId);
        return eventId;
    }

    private Future<ExtractableResponse<Response>> submitConcurrentCreate(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            AuthSession caller,
            Map<String, Object> payload
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return authenticatedJsonRequest(caller).body(payload).post(GAM_LOCATION_PATH).then().extract();
        });
    }

    private Future<ExtractableResponse<Response>> submitConcurrentEventCreate(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            AuthSession caller,
            UUID locationId
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", "Concurrent Event reference");
            payload.put("description", "Concurrent reference test");
            payload.put("gamLocationId", locationId.toString());
            payload.put("beginDate", Instant.now().plusSeconds(3600).toString());
            payload.put("endDate", Instant.now().plusSeconds(7200).toString());
            return authenticatedJsonRequest(caller).body(payload).post("/events").then().extract();
        });
    }

    private Future<ExtractableResponse<Response>> submitConcurrentRemoval(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            AuthSession caller,
            UUID locationId
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return authenticatedJsonRequest(caller).body(reasonPayload(VALID_REMOVAL_REASON))
                    .delete(GAM_LOCATION_PATH + "/{id}", locationId).then().extract();
        });
    }

    private Future<ExtractableResponse<Response>> submitConcurrentUpdate(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            AuthSession caller,
            UUID locationId
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return authenticatedJsonRequest(caller).body(validPayload("Concurrent update after"))
                    .put(GAM_LOCATION_PATH + "/{id}", locationId).then().extract();
        });
    }

    private ExtractableResponse<Response> getConcurrentResponse(
            Future<ExtractableResponse<Response>> future
    ) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent GamLocation request failed", exception);
        }
    }

    private void softDeleteEvent(UUID eventId) {
        gamLocationJdbcTemplate.update(
                "UPDATE events SET deleted_at = ?, deleted_by = NULL WHERE id = ?",
                Timestamp.from(Instant.now()),
                eventId
        );
    }

    private void assertNotFoundGamLocation(ExtractableResponse<Response> response, UUID expectedId) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.<String>path("code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.<String>path("details.resource")).isEqualTo("GamLocation");
        if (expectedId != null) {
            assertThat(response.<String>path("details.identifier")).isEqualTo(expectedId.toString());
        }
    }

    private static void assertStatus(Response response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }

    private static Map<String, Object> validPayload(String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("street", "Rua São José, 123");
        payload.put("city", "Campinas");
        payload.put("state", "SP");
        payload.put("postalCode", "01000-000");
        payload.put("countryCode", "BR");
        payload.put("latitude", new BigDecimal("-22.90684670"));
        payload.put("longitude", new BigDecimal("-47.06158810"));
        return payload;
    }

    private static Map<String, Object> systemLocationPayload(
            SystemLocationExpectation location
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", location.name());
        payload.put("street", location.street());
        payload.put("city", location.city());
        payload.put("state", location.state());
        payload.put("postalCode", location.postalCode());
        payload.put("countryCode", location.countryCode());
        payload.put("latitude", null);
        payload.put("longitude", null);
        return payload;
    }

    private static Map<String, Object> withValue(Map<String, Object> source, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.put(key, value);
        return copy;
    }

    private static Map<String, Object> without(Map<String, Object> source, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.remove(key);
        return copy;
    }

    private String locationTable() {
        if (tableExists("gam_locations")) {
            return "gam_locations";
        }
        if (tableExists("locations")) {
            return "locations";
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        Boolean exists = gamLocationJdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)",
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    private record SystemLocationExpectation(
            String code,
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode
    ) {
    }
}
