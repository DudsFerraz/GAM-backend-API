package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - RBAC Catalog")
class RbacCatalogApiIT extends BaseApiIntegrationTest {

    private final List<UUID> catalogRoleIds = new ArrayList<>();
    private final List<UUID> catalogPermissionIds = new ArrayList<>();
    private final List<UUID> catalogRolePermissionIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanupCatalogFixtures() {
        deleteByIds("role_permissions", catalogRolePermissionIds);
        if (!catalogRoleIds.isEmpty()) {
            jdbcTemplate.update(
                    "DELETE FROM account_roles WHERE role_id IN (" + placeholders(catalogRoleIds.size()) + ")",
                    catalogRoleIds.toArray()
            );
        }
        deleteByIds("permissions", catalogPermissionIds);
        deleteByIds("roles", catalogRoleIds);
        catalogRolePermissionIds.clear();
        catalogPermissionIds.clear();
        catalogRoleIds.clear();
    }

    @Test
    @DisplayName("REQ-RBAC-006 and REQ-RBAC-010 - ROLE_GET reads an active system role with the catalog shape")
    void roleGetPermissionShouldReturnActiveSystemRoleWithoutPersistenceMetadata() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID roleId = roleId("COORD");

        Map<String, Object> role = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}", roleId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertRoleRecord(role, roleId, "COORD", true);
    }

    @Test
    @DisplayName("REQ-RBAC-006 - ROLE_GET reads an active custom role and exposes systemManaged false")
    void roleGetPermissionShouldReturnActiveCustomRole() {
        UUID customRoleId = createRole("CATALOG_" + shortId(), "Custom catalog role", false);
        AuthSession coordinator = registerAndLogin("COORD");

        Map<String, Object> role = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}", customRoleId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertRoleRecord(role, customRoleId, (String) role.get("name"), false);
    }

    @Test
    @DisplayName("REQ-RBAC-012 and REQ-RBAC-013 - ROLE_GET lists the complete visible Role collection in deterministic order")
    void roleCollectionShouldExposeTheUnpagedVisibleCatalogInDeterministicOrder() {
        UUID laterId = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
        UUID earlierId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        createRole(laterId, "catalog_case_" + shortId(), "Later case-insensitive tie", false);
        String tiedName = roleName(laterId).toUpperCase();
        createRole(earlierId, tiedName, "Earlier UUID tie", false);
        UUID deletedRoleId = createRole("DELETED_COLLECTION_" + shortId(), "Deleted collection role", false);
        UUID staleRoleId = createRole("STALE_SYSTEM_" + shortId(), "Stale system role", true);
        jdbcTemplate.update("UPDATE roles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deletedRoleId);
        AuthSession caller = registerAndLogin("COORD");

        Map<String, Object> response = authenticatedJsonRequest(caller)
                .get("/roles")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(response).containsOnlyKeys("roles");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) response.get("roles");
        assertThat(roles).allSatisfy(role ->
                assertThat(role).containsOnlyKeys("id", "name", "description", "systemManaged"));
        assertThat(roles).extracting(role -> role.get("name"))
                .contains("COORD", "MEMBER", "VISITOR", roleName(laterId), tiedName)
                .doesNotContain("SUDO", roleName(deletedRoleId), roleName(staleRoleId));

        List<String> actualOrder = roles.stream()
                .map(role -> role.get("name") + ":" + role.get("id"))
                .toList();
        List<String> expectedOrder = roles.stream()
                .sorted(java.util.Comparator
                        .comparing((Map<String, Object> role) -> role.get("name").toString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(role -> UUID.fromString(role.get("id").toString())))
                .map(role -> role.get("name") + ":" + role.get("id"))
                .toList();
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    @DisplayName("REQ-RBAC-013 - trimmed case-insensitive accent-sensitive name search filters visible Roles")
    void roleCollectionNameSearchShouldApplyTheDocumentedMatchingRules() {
        String suffix = shortId();
        String matchingName = "EVENT_MANAGER_" + suffix;
        String accentedName = "ÁRBITRO_" + suffix;
        createRole(matchingName, "Matching role", false);
        createRole(accentedName, "Accent-sensitive role", false);
        AuthSession caller = registerAndLogin("COORD");

        authenticatedJsonRequest(caller)
                .queryParam("name", "  manager_" + suffix.toUpperCase() + "  ")
                .get("/roles")
                .then()
                .statusCode(200)
                .body("roles.name", equalTo(List.of(matchingName)));

        authenticatedJsonRequest(caller)
                .queryParam("name", "arbitro_" + suffix)
                .get("/roles")
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));

        authenticatedJsonRequest(caller)
                .queryParam("name", "unknown-role-" + suffix)
                .get("/roles")
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));

        authenticatedJsonRequest(caller)
                .queryParam("name", "SUDO")
                .get("/roles")
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));
    }

    @ParameterizedTest
    @MethodSource("blankRoleNames")
    @DisplayName("REQ-RBAC-013 - blank supplied name -> HTTP 400")
    void blankRoleCollectionNameShouldReturnBadRequest(String name) {
        AuthSession caller = registerAndLogin("COORD");

        authenticatedJsonRequest(caller)
                .queryParam("name", name)
                .get("/roles")
                .then()
                .statusCode(400)
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("REQ-RBAC-012 - ACCOUNT_ROLE_MANAGE without ROLE_GET cannot list Roles")
    void accountRoleManageShouldNotSubstituteForRoleGetOnCollection() {
        String roleName = "ACCOUNT_ROLE_ONLY_" + shortId();
        UUID roleId = createRole(roleName, "Account role manager only", false);
        linkRolePermission(roleId, permissionId("ACCOUNT_ROLE_MANAGE"));
        AuthSession caller = registerAndLogin(roleName);

        authenticatedJsonRequest(caller)
                .get("/roles")
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-RBAC-007 and REQ-RBAC-010 - PERMISSION_GET reads an active system permission with the catalog shape")
    void permissionGetPermissionShouldReturnActiveSystemPermissionWithoutPersistenceMetadata() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID permissionId = permissionId("EVENT_GET_COORD");

        Map<String, Object> permission = authenticatedJsonRequest(coordinator)
                .get("/permissions/{permissionId}", permissionId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertPermissionRecord(permission, permissionId, "EVENT_GET_COORD", true);
    }

    @Test
    @DisplayName("REQ-RBAC-008 - both catalog permissions read an active role's permissions in a top-level list")
    void bothCatalogPermissionsShouldReadRolePermissionsInTheDocumentedShape() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID roleId = roleId("MEMBER");

        Map<String, Object> response = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}/permissions", roleId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(response).containsOnlyKeys("permissions");
        assertThat(response.get("permissions")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions = (List<Map<String, Object>>) response.get("permissions");
        assertThat(permissions)
                .extracting(permission -> permission.get("code"))
                .contains("EVENT_GET_MEMBER");
        assertThat(permissions).allSatisfy(permission ->
                assertThat(permission).containsOnlyKeys("id", "code", "label", "description", "systemManaged")
        );
    }

    @Test
    @DisplayName("REQ-RBAC-008 - active role without active links returns an empty permissions list")
    void roleWithoutActivePermissionLinksShouldReturnEmptyPermissionsList() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID visitorRoleId = roleId("VISITOR");

        Map<String, Object> response = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}/permissions", visitorRoleId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(response).containsOnlyKeys("permissions");
        assertThat(response.get("permissions")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("REQ-RBAC-008 - soft-deleted links and permissions are excluded from nested catalog reads")
    void softDeletedRolePermissionLinksAndPermissionsShouldBeExcluded() {
        UUID customRoleId = createRole("CUSTOM_LINKS_" + UUID.randomUUID(), "Custom links role", false);
        UUID memberGetId = permissionId("MEMBER_GET");
        UUID linkId = linkRolePermission(customRoleId, memberGetId);
        UUID deletedPermissionId = createPermission("DELETED_LINK_" + UUID.randomUUID());
        linkRolePermission(customRoleId, deletedPermissionId);
        AuthSession coordinator = registerAndLogin("COORD");

        jdbcTemplate.update("UPDATE role_permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", linkId);
        jdbcTemplate.update("UPDATE permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deletedPermissionId);

        Map<String, Object> response = authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}/permissions", customRoleId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(response.get("permissions")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("REQ-RBAC-009 - missing and soft-deleted catalog records return HTTP 404")
    void missingAndSoftDeletedCatalogRecordsShouldReturnNotFound() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID customRoleId = createRole("DELETED_" + shortId(), "Deleted custom role", false);
        UUID customPermissionId = createPermission("CUSTOM_DELETED_" + UUID.randomUUID());

        jdbcTemplate.update("UPDATE roles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", customRoleId);
        jdbcTemplate.update("UPDATE permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", customPermissionId);

        authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}", customRoleId)
                .then()
                .statusCode(404);
        authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}/permissions", customRoleId)
                .then()
                .statusCode(404);
        authenticatedJsonRequest(coordinator)
                .get("/permissions/{permissionId}", customPermissionId)
                .then()
                .statusCode(404);
        authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}", UUID.randomUUID())
                .then()
                .statusCode(404);
        authenticatedJsonRequest(coordinator)
                .get("/permissions/{permissionId}", UUID.randomUUID())
                .then()
                .statusCode(404);
        authenticatedJsonRequest(coordinator)
                .get("/roles/{roleId}/permissions", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @MethodSource("catalogReadPaths")
    @DisplayName("REQ-RBAC-009 - unauthenticated catalog read returns HTTP 401")
    void unauthenticatedCatalogReadShouldReturnUnauthorized(String path) {
        jsonRequest()
                .get(path)
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @ParameterizedTest
    @MethodSource("catalogReadsWithMissingPermission")
    @DisplayName("REQ-RBAC-006 through REQ-RBAC-008 - authenticated caller without required catalog permission returns HTTP 403")
    void authenticatedCallerWithoutRequiredCatalogPermissionShouldBeForbidden(
            String roleName,
            String path
    ) {
        AuthSession caller = registerAndLogin(roleName);

        authenticatedJsonRequest(caller)
                .get(path)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-RBAC-008 - nested catalog read requires both ROLE_GET and PERMISSION_GET")
    void nestedCatalogReadShouldRequireBothCatalogPermissions() {
        String roleName = "ROLE_ONLY_" + shortId();
        UUID roleOnlyId = createRole(roleName, "Role catalog reader", false);
        linkRolePermission(roleOnlyId, permissionId("ROLE_GET"));
        AuthSession roleOnlyCaller = registerAndLogin(roleName);

        authenticatedJsonRequest(roleOnlyCaller)
                .get("/roles/{roleId}/permissions", roleId("COORD"))
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-RBAC-007 and REQ-RBAC-008 - PERMISSION_GET alone reads permissions but not roles")
    void permissionGetOnlyShouldReadPermissionsButNotRoles() {
        String roleName = "PERMISSION_ONLY_" + shortId();
        UUID permissionOnlyId = createRole(roleName, "Permission catalog reader", false);
        linkRolePermission(permissionOnlyId, permissionId("PERMISSION_GET"));
        AuthSession permissionOnlyCaller = registerAndLogin(roleName);

        authenticatedJsonRequest(permissionOnlyCaller)
                .get("/permissions/{permissionId}", permissionId("EVENT_GET_COORD"))
                .then()
                .statusCode(200);

        authenticatedJsonRequest(permissionOnlyCaller)
                .get("/roles/{roleId}", roleId("COORD"))
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        authenticatedJsonRequest(permissionOnlyCaller)
                .get("/roles/{roleId}/permissions", roleId("COORD"))
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-RBAC-006 and REQ-RBAC-008 - ROLE_GET alone reads roles but not permissions")
    void roleGetOnlyShouldReadRolesButNotPermissions() {
        String roleName = "ROLE_ONLY_" + shortId();
        UUID roleOnlyId = createRole(roleName, "Role catalog reader", false);
        linkRolePermission(roleOnlyId, permissionId("ROLE_GET"));
        AuthSession roleOnlyCaller = registerAndLogin(roleName);

        authenticatedJsonRequest(roleOnlyCaller)
                .get("/roles/{roleId}", roleId("COORD"))
                .then()
                .statusCode(200);

        authenticatedJsonRequest(roleOnlyCaller)
                .get("/permissions/{permissionId}", permissionId("EVENT_GET_COORD"))
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        authenticatedJsonRequest(roleOnlyCaller)
                .get("/roles/{roleId}/permissions", roleId("COORD"))
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-RBAC-007 - PERMISSION_GET reads an active custom permission")
    void permissionGetShouldReadActiveCustomPermission() {
        String customPermissionCode = "CUSTOM_READABLE_" + UUID.randomUUID();
        UUID customPermissionId = createPermission(customPermissionCode);
        String roleName = "PERMISSION_ONLY_" + shortId();
        UUID permissionOnlyId = createRole(roleName, "Permission catalog reader", false);
        linkRolePermission(permissionOnlyId, permissionId("PERMISSION_GET"));
        AuthSession permissionOnlyCaller = registerAndLogin(roleName);

        Map<String, Object> permission = authenticatedJsonRequest(permissionOnlyCaller)
                .get("/permissions/{permissionId}", customPermissionId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertPermissionRecord(
                permission,
                customPermissionId,
                customPermissionCode,
                false
        );
    }

    @Test
    @DisplayName("REQ-RBAC-011 - public event with null requiredPermissionId is visible anonymously")
    void publicEventWithNullRequiredPermissionShouldBeVisibleAnonymously() {
        UUID eventId = insertEvent(null);

        jsonRequest()
                .get("/events/{eventId}", eventId)
                .then()
                .statusCode(200)
                .body("id", equalTo(eventId.toString()));
    }

    @Test
    @DisplayName("REQ-RBAC-003 and REQ-RBAC-011 - coordinator receives both event audience permissions")
    void coordinatorShouldReadMemberAndCoordinatorEvents() {
        UUID memberEventId = insertEvent(permissionId("EVENT_GET_MEMBER"));
        UUID coordinatorEventId = insertEvent(permissionId("EVENT_GET_COORD"));
        AuthSession coordinator = registerAndLogin("COORD");

        authenticatedJsonRequest(coordinator)
                .get("/events/{eventId}", memberEventId)
                .then()
                .statusCode(200);
        authenticatedJsonRequest(coordinator)
                .get("/events/{eventId}", coordinatorEventId)
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("REQ-RBAC-003 and REQ-RBAC-011 - member cannot read an event requiring coordinator permission")
    void memberShouldNotReadCoordinatorOnlyEvent() {
        UUID memberEventId = insertEvent(permissionId("EVENT_GET_MEMBER"));
        UUID coordinatorEventId = insertEvent(permissionId("EVENT_GET_COORD"));
        AuthSession member = registerAndLogin("MEMBER");

        authenticatedJsonRequest(member)
                .get("/events/{eventId}", memberEventId)
                .then()
                .statusCode(200);
        authenticatedJsonRequest(member)
                .get("/events/{eventId}", coordinatorEventId)
                .then()
                .statusCode(404);
    }

    private static Stream<String> catalogReadPaths() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                "/roles",
                "/roles/" + id,
                "/permissions/" + id,
                "/roles/" + id + "/permissions"
        );
    }

    private static Stream<Arguments> catalogReadsWithMissingPermission() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                Arguments.of("MEMBER", "/roles"),
                Arguments.of("MEMBER", "/roles/" + id),
                Arguments.of("MEMBER", "/permissions/" + id),
                Arguments.of("MEMBER", "/roles/" + id + "/permissions")
        );
    }

    private static Stream<String> blankRoleNames() {
        return Stream.of("", " ", "\t");
    }

    private UUID roleId(String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL",
                UUID.class,
                roleName
        );
    }

    private UUID createRole(String name, String description, boolean systemManaged) {
        return createRole(UUID.randomUUID(), name, description, systemManaged);
    }

    private UUID createRole(UUID id, String name, String description, boolean systemManaged) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id,
                name,
                description,
                systemManaged,
                now,
                now
        );
        catalogRoleIds.add(id);
        return id;
    }

    private String roleName(UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM roles WHERE id = ?",
                String.class,
                roleId
        );
    }

    private UUID createPermission(String code) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO permissions (id, code, label, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, FALSE, ?, ?)",
                id,
                code,
                "Custom permission",
                "Custom permission fixture",
                now,
                now
        );
        catalogPermissionIds.add(id);
        return id;
    }

    private UUID linkRolePermission(UUID roleId, UUID permissionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO role_permissions (id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?)",
                id,
                roleId,
                permissionId,
                Timestamp.from(Instant.now())
        );
        catalogRolePermissionIds.add(id);
        return id;
    }

    private UUID insertEvent(UUID requiredPermissionId) {
        UUID eventId = UUID.randomUUID();
        UUID gamLocationId = insertGamLocation();
        Instant begin = Instant.now().plusSeconds(3600);
        jdbcTemplate.update(
                "INSERT INTO events "
                        + "(id, title, description, gam_location_id, required_permission_id, type, status, "
                        + "begin_date, end_date, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS event_type_enum), CAST(? AS event_status_enum), ?, ?, ?, ?)",
                eventId,
                "RBAC catalog event " + eventId,
                "RBAC catalog visibility fixture",
                gamLocationId,
                requiredPermissionId,
                "GENERIC",
                "SCHEDULED",
                Timestamp.from(begin),
                Timestamp.from(begin.plusSeconds(3600)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
        trackEvent(eventId);
        return eventId;
    }

    private UUID insertGamLocation() {
        UUID id = UUID.randomUUID();
        String name = "RBAC catalog location " + id;
        String identityName = "rbac catalog location " + id;
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "INSERT INTO gam_locations "
                        + "(id, name, street, city, state, postal_code, country_code, "
                        + "identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, ?, ?, NULL, ?, ?, '', ?, ?, '', ?, ?, ?)",
                id,
                name,
                "Campinas",
                "SP",
                "BR",
                identityName,
                "campinas",
                "sp",
                "br",
                now,
                now
        );
        trackGamLocation(id);
        return id;
    }

    private void assertRoleRecord(Map<String, Object> role, UUID id, String name, boolean systemManaged) {
        assertThat(role).containsOnlyKeys("id", "name", "description", "systemManaged");
        assertThat(role)
                .containsEntry("id", id.toString())
                .containsEntry("name", name)
                .containsEntry("systemManaged", systemManaged);
        assertThat(role.get("description")).isInstanceOf(String.class);
    }

    private void assertPermissionRecord(
            Map<String, Object> permission,
            UUID id,
            String code,
            boolean systemManaged
    ) {
        assertThat(permission).containsOnlyKeys("id", "code", "label", "description", "systemManaged");
        assertThat(permission)
                .containsEntry("id", id.toString())
                .containsEntry("code", code)
                .containsEntry("systemManaged", systemManaged);
        assertThat(permission.get("label")).isInstanceOf(String.class);
        assertThat(permission.get("description")).isInstanceOf(String.class);
    }

    private void deleteByIds(String table, List<UUID> ids) {
        if (!ids.isEmpty()) {
            jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE id IN (" + placeholders(ids.size()) + ")",
                    ids.toArray()
            );
        }
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
