package br.org.gam.api.rbac;

import br.org.gam.api.GamApiApplication;
import br.org.gam.api.db.migration.R__SeedPermissionsAndRoles;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - RBAC Catalog Seed")
class RbacCatalogPersistenceIT extends PostgreSQLIntegrationTest {

    /*
     * These literals intentionally remain independent from PermissionEnum, SystemRole, and the seed's
     * role-permission mappings. This class is the persistence-level oracle for REQ-RBAC-001 through
     * REQ-RBAC-005; deriving the expected values from production registry code would make an incorrect
     * registry self-validate. When an accepted Requirement Specification changes the baseline,
     * update these sets in the same change so the exact-set assertions expose any registry drift.
     */
    private static final Set<String> BASELINE_ROLES =
            Set.of("SUDO", "COORD", "ORATORIO_COORD", "MEMBER", "VISITOR");

    private static final Set<String> BASELINE_PERMISSIONS = Set.of(
            "MEMBER_GET",
            "MEMBER_INFORMATION_GET",
            "MEMBER_SEARCH",
            "MEMBER_ACTIVATION",
            "MEMBER_GET_NON_ACTIVE",
            "MEMBER_MANAGE",
            "MEMBER_ACCOUNT_LINK",
            "COORDINATOR_MANAGE",
            "ACCOUNT_GET",
            "ACCOUNT_SEARCH",
            "ACCOUNT_ROLE_MANAGE",
            "EVENT_CREATE",
            "EVENT_SEARCH",
            "EVENT_GET_PRESENCES",
            "EVENT_GET_MEMBER",
            "EVENT_GET_COORD",
            "EVENT_MANAGE",
            "GAM_LOCATION_GET",
            "GAM_LOCATION_CREATE",
            "GAM_LOCATION_MANAGE",
            "PRESENCES_SEARCH",
            "PRESENCE_REGISTER",
            "PRESENCE_EDIT",
            "PRESENCE_REMOVE",
            "MISSA_GET",
            "MISSA_CREATE",
            "MISSA_MANAGE",
            "ROLE_GET",
            "PERMISSION_GET",
            "ORATORIO_GET",
            "ORATORIO_CREATE",
            "ORATORIO_MANAGE",
            "ORATORIO_ATTENDANCE_GET",
            "ORATORIO_ATTENDANCE_MANAGE",
            "ORATORIO_COORD_MANAGE",
            "ORATORIANO_GET",
            "ORATORIANO_REGISTER",
            "ORATORIANO_MANAGE",
            "ORATORIANO_FORM_GET",
            "ORATORIANO_FORM_MANAGE",
            "ORATORIANO_FORM_PDF_GENERATE",
            "ORATORIANO_FORM_ATTACHMENT_GET"
    );

    private static final Set<String> MEMBER_PERMISSIONS = Set.of(
            "MEMBER_GET",
            "ACCOUNT_GET",
            "EVENT_SEARCH",
            "EVENT_GET_PRESENCES",
            "EVENT_GET_MEMBER",
            "GAM_LOCATION_GET",
            "MISSA_GET",
            "ORATORIO_GET"
    );

    private static final Set<String> ORATORIO_OPERATIONS = Set.of(
            "ORATORIO_GET",
            "ORATORIO_CREATE",
            "ORATORIO_MANAGE",
            "ORATORIO_ATTENDANCE_GET",
            "ORATORIO_ATTENDANCE_MANAGE",
            "ORATORIANO_GET",
            "ORATORIANO_REGISTER",
            "ORATORIANO_MANAGE",
            "ORATORIANO_FORM_GET",
            "ORATORIANO_FORM_MANAGE",
            "ORATORIANO_FORM_PDF_GENERATE",
            "ORATORIANO_FORM_ATTACHMENT_GET"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @AfterEach
    void restoreCatalogAfterMutation() throws Exception {
        invokeSeed();
    }

    @Test
    @DisplayName("REQ-RBAC-001 and REQ-RBAC-002 - baseline roles and permissions are system-managed")
    void baselineRolesAndPermissionsShouldExistAsSystemManagedRecords() {
        assertThat(activeRoleNames()).containsExactlyInAnyOrderElementsOf(BASELINE_ROLES);
        assertThat(activePermissionCodes()).containsExactlyInAnyOrderElementsOf(BASELINE_PERMISSIONS);

        assertThat(systemManagedRoleNames()).containsExactlyInAnyOrderElementsOf(BASELINE_ROLES);
        assertThat(systemManagedPermissionCodes()).containsExactlyInAnyOrderElementsOf(BASELINE_PERMISSIONS);
    }

    @Test
    @DisplayName("REQ-RBAC-003 - baseline roles contain the documented permission bundles")
    void baselineRolesShouldContainDocumentedPermissionBundles() {
        assertThat(activePermissionCodesForRole("SUDO")).containsExactlyInAnyOrderElementsOf(BASELINE_PERMISSIONS);
        assertThat(activePermissionCodesForRole("COORD")).containsExactlyInAnyOrderElementsOf(BASELINE_PERMISSIONS);
        assertThat(activePermissionCodesForRole("ORATORIO_COORD"))
                .containsExactlyInAnyOrderElementsOf(ORATORIO_OPERATIONS);
        assertThat(activePermissionCodesForRole("MEMBER")).containsExactlyInAnyOrderElementsOf(MEMBER_PERMISSIONS);
        assertThat(activePermissionCodesForRole("VISITOR")).isEmpty();
        assertThat(activePermissionCodesForRole("MEMBER")).doesNotContain("ACCOUNT_ROLE_MANAGE");
        assertThat(activePermissionCodesForRole("VISITOR")).doesNotContain("ACCOUNT_ROLE_MANAGE");
        assertThat(activePermissionCodesForRole("MEMBER")).doesNotContain("COORDINATOR_MANAGE");
        assertThat(activePermissionCodesForRole("VISITOR")).doesNotContain("COORDINATOR_MANAGE");
        assertThat(activePermissionCodesForRole("ORATORIO_COORD")).doesNotContain("ORATORIO_COORD_MANAGE");
    }

    @Test
    @DisplayName("REQ-DATA-003 and REQ-RBAC-004 - converged seed is a complete observable no-op")
    void repeatedSeedShouldPreserveIdentifiersAndAvoidDuplicateRows() throws Exception {
        Map<String, UUID> roleIdsBefore = activeIdsByName("roles", "name");
        Map<String, UUID> permissionIdsBefore = activeIdsByName("permissions", "code");
        long activeRolePermissionCountBefore = activeRolePermissionCount();
        Instant roleUpdatedAtBefore = updatedAt("roles", activeId("roles", "name", "COORD"));
        Instant permissionUpdatedAtBefore =
                updatedAt("permissions", activeId("permissions", "code", "PERMISSION_GET"));
        long activityCountBefore = rowCount("activity_logs");
        long accountCountBefore = rowCount("accounts");
        long accountRoleCountBefore = rowCount("account_roles");
        long refreshTokenCountBefore = rowCount("refresh_tokens");

        invokeSeed();

        assertThat(activeIdsByName("roles", "name")).containsExactlyInAnyOrderEntriesOf(roleIdsBefore);
        assertThat(activeIdsByName("permissions", "code")).containsExactlyInAnyOrderEntriesOf(permissionIdsBefore);
        assertThat(activeRolePermissionCount()).isEqualTo(activeRolePermissionCountBefore);
        assertThat(duplicateActiveRolePermissionPairs()).isEmpty();
        assertThat(updatedAt("roles", activeId("roles", "name", "COORD"))).isEqualTo(roleUpdatedAtBefore);
        assertThat(updatedAt("permissions", activeId("permissions", "code", "PERMISSION_GET")))
                .isEqualTo(permissionUpdatedAtBefore);
        assertThat(rowCount("activity_logs")).isEqualTo(activityCountBefore);
        assertThat(rowCount("accounts")).isEqualTo(accountCountBefore);
        assertThat(rowCount("account_roles")).isEqualTo(accountRoleCountBefore);
        assertThat(rowCount("refresh_tokens")).isEqualTo(refreshTokenCountBefore);
    }

    @Test
    @DisplayName("REQ-DATA-002 - repeatable seed exposes a stable deterministic registry checksum")
    void repeatableSeedShouldExposeStableRegistryChecksum() {
        Integer firstChecksum = new R__SeedPermissionsAndRoles().getChecksum();
        Integer secondChecksum = new R__SeedPermissionsAndRoles().getChecksum();

        assertThat(firstChecksum)
                .as("the complete accepted registry must participate in Flyway repeatable scheduling")
                .isNotNull()
                .isEqualTo(secondChecksum);
    }

    @Test
    @DisplayName("REQ-DATA-004 - reserved-key collision fails before any catalog mutation")
    void customReservedKeyCollisionShouldFailBeforeMutation() {
        UUID coordId = activeId("roles", "name", "COORD");
        UUID permissionGetId = activeId("permissions", "code", "PERMISSION_GET");
        String acceptedRoleDescription = activeText("roles", "description", "name", "COORD");
        String acceptedPermissionLabel = activeText("permissions", "label", "code", "PERMISSION_GET");
        Instant collisionTimestamp = Instant.parse("2024-01-02T03:04:05Z");

        try {
            jdbcTemplate.update(
                    "UPDATE roles SET description = ?, system_managed = FALSE, updated_at = ? WHERE id = ?",
                    "User-managed collision",
                    Timestamp.from(collisionTimestamp),
                    coordId
            );
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ?, updated_at = ? WHERE id = ?",
                    "Unreconciled metadata",
                    Timestamp.from(collisionTimestamp),
                    permissionGetId
            );

            Throwable failure = catchThrowable(this::invokeSeed);

            assertThat(failure)
                    .as("a user-managed record under a reserved stable key must block synchronization")
                    .isNotNull();
            assertThat(activeText("roles", "description", "name", "COORD"))
                    .isEqualTo("User-managed collision");
            assertThat(systemManaged("roles", coordId)).isFalse();
            assertThat(activeText("permissions", "label", "code", "PERMISSION_GET"))
                    .isEqualTo("Unreconciled metadata");
            assertThat(updatedAt("permissions", permissionGetId)).isEqualTo(collisionTimestamp);
        } finally {
            jdbcTemplate.update(
                    "UPDATE roles SET description = ?, system_managed = TRUE WHERE id = ?",
                    acceptedRoleDescription,
                    coordId
            );
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ? WHERE id = ?",
                    acceptedPermissionLabel,
                    permissionGetId
            );
        }
    }

    @Test
    @DisplayName("REQ-DATA-006 - unexplained mandatory-link drift blocks application restart")
    void missingMandatoryLinkShouldBlockApplicationRestart() {
        UUID roleId = activeId("roles", "name", "COORD");
        UUID permissionId = activeId("permissions", "code", "PERMISSION_GET");
        UUID originalLinkId = activeRolePermissionId(roleId, permissionId);
        ConfigurableApplicationContext restartedContext = null;
        Throwable startupFailure = null;

        try {
            try (ConfigurableApplicationContext baselineContext = startApplication()) {
                assertThat(baselineContext.isActive())
                        .as("the control restart must succeed before persisted drift is introduced")
                        .isTrue();
            }

            jdbcTemplate.update(
                    "UPDATE role_permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    originalLinkId
            );

            try {
                restartedContext = startApplication();
            } catch (Throwable failure) {
                startupFailure = failure;
            }
            assertThat(deletedAt("role_permissions", originalLinkId))
                    .as("startup validation must remain read-only")
                    .isNotNull();
        } finally {
            if (restartedContext != null) {
                restartedContext.close();
            }
            jdbcTemplate.update(
                    "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ? AND id <> ?",
                    roleId,
                    permissionId,
                    originalLinkId
            );
            jdbcTemplate.update(
                    "UPDATE role_permissions SET deleted_at = NULL WHERE id = ?",
                    originalLinkId
            );
        }

        assertThat(startupFailure)
                .as("read-only startup validation must reject persisted drift without silently repairing it")
                .isNotNull();
    }

    @Test
    @DisplayName("REQ-DATA-006 - soft-deleted reserved-key collision blocks application restart")
    void softDeletedReservedKeyCollisionShouldBlockApplicationRestart() {
        UUID collisionId = UUID.randomUUID();
        Timestamp persistedAt = Timestamp.from(Instant.parse("2024-01-02T03:04:05Z"));
        ConfigurableApplicationContext restartedContext = null;
        Throwable startupFailure = null;

        try {
            jdbcTemplate.update(
                    "INSERT INTO roles "
                            + "(id, name, description, system_managed, created_at, updated_at, deleted_at) "
                            + "VALUES (?, ?, ?, FALSE, ?, ?, ?)",
                    collisionId,
                    "COORD",
                    "Soft-deleted user-managed collision",
                    persistedAt,
                    persistedAt,
                    persistedAt
            );

            try {
                restartedContext = startApplication();
            } catch (Throwable failure) {
                startupFailure = failure;
            }
            assertThat(deletedAt("roles", collisionId))
                    .as("startup validation must not repair or remove the colliding record")
                    .isNotNull();
        } finally {
            if (restartedContext != null) {
                restartedContext.close();
            }
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", collisionId);
        }

        assertThat(startupFailure)
                .as("startup must inspect active and soft-deleted matches for every reserved stable key")
                .isNotNull();
    }

    @Test
    @DisplayName("REQ-DATA-006 - duplicate persisted required link blocks application restart")
    void duplicatePersistedRequiredLinkShouldBlockApplicationRestart() {
        UUID roleId = activeId("roles", "name", "COORD");
        UUID permissionId = activeId("permissions", "code", "EVENT_GET_MEMBER");
        UUID duplicateLinkId = UUID.randomUUID();
        Timestamp persistedAt = Timestamp.from(Instant.parse("2024-01-02T03:04:05Z"));
        ConfigurableApplicationContext restartedContext = null;
        Throwable startupFailure = null;

        try {
            jdbcTemplate.update(
                    "INSERT INTO role_permissions "
                            + "(id, role_id, permission_id, created_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
                    duplicateLinkId,
                    roleId,
                    permissionId,
                    persistedAt,
                    persistedAt
            );

            try {
                restartedContext = startApplication();
            } catch (Throwable failure) {
                startupFailure = failure;
            }
            assertThat(deletedAt("role_permissions", duplicateLinkId))
                    .as("startup validation must not repair or remove the duplicate relationship")
                    .isNotNull();
        } finally {
            if (restartedContext != null) {
                restartedContext.close();
            }
            jdbcTemplate.update("DELETE FROM role_permissions WHERE id = ?", duplicateLinkId);
        }

        assertThat(startupFailure)
                .as("startup must inspect active and soft-deleted matches for every required relationship")
                .isNotNull();
    }

    @Test
    @DisplayName("REQ-DATA-003 and REQ-RBAC-004 - soft-deleted baseline link identity is restored once")
    void softDeletedBaselineLinkShouldReuseItsIdentityWhenRestored() throws Exception {
        UUID roleId = activeId("roles", "name", "COORD");
        UUID permissionId = activeId("permissions", "code", "EVENT_GET_MEMBER");
        UUID originalLinkId = activeRolePermissionId(roleId, permissionId);

        try {
            jdbcTemplate.update(
                    "UPDATE role_permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    originalLinkId
            );

            invokeSeed();

            List<UUID> repairedLinks = activeRolePermissionIds(roleId, permissionId);
            assertThat(repairedLinks)
                    .hasSize(1)
                    .containsExactly(originalLinkId);
            assertThat(deletedAt("role_permissions", originalLinkId)).isNull();

            invokeSeed();

            assertThat(activeRolePermissionIds(roleId, permissionId))
                    .containsExactly(originalLinkId);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ? AND id <> ?",
                    roleId,
                    permissionId,
                    originalLinkId
            );
            jdbcTemplate.update(
                    "UPDATE role_permissions SET deleted_at = NULL WHERE id = ?",
                    originalLinkId
            );
        }
    }

    @Test
    @DisplayName("REQ-DATA-004 and REQ-RBAC-004 - duplicate persisted baseline link is a collision")
    void duplicatePersistedBaselineLinkShouldBlockSynchronization() {
        UUID roleId = activeId("roles", "name", "COORD");
        UUID permissionId = activeId("permissions", "code", "EVENT_GET_MEMBER");
        UUID duplicateLinkId = UUID.randomUUID();

        try {
            jdbcTemplate.update(
                    "INSERT INTO role_permissions "
                            + "(id, role_id, permission_id, created_at, deleted_at) VALUES (?, ?, ?, ?, ?)",
                    duplicateLinkId,
                    roleId,
                    permissionId,
                    Timestamp.from(Instant.parse("2024-01-02T03:04:05Z")),
                    Timestamp.from(Instant.parse("2024-01-03T03:04:05Z"))
            );

            Throwable failure = catchThrowable(this::invokeSeed);

            assertThat(failure)
                    .as("multiple persisted matches for one required relationship must block synchronization")
                    .isNotNull();
            assertThat(activeRolePermissionIds(roleId, permissionId)).hasSize(1);
            assertThat(deletedAt("role_permissions", duplicateLinkId)).isNotNull();
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE id = ?", duplicateLinkId);
        }
    }

    @Test
    @DisplayName("REQ-RBAC-004 - stale registry metadata is synchronized without replacing identifiers")
    void staleRegistryMetadataShouldBeSynchronizedWithoutReplacingIdentifiers() throws Exception {
        UUID coordId = activeId("roles", "name", "COORD");
        UUID permissionGetId = activeId("permissions", "code", "PERMISSION_GET");
        String originalRoleDescription = activeText("roles", "description", "name", "COORD");
        String originalPermissionLabel = activeText("permissions", "label", "code", "PERMISSION_GET");
        String originalPermissionDescription = activeText("permissions", "description", "code", "PERMISSION_GET");

        try {
            jdbcTemplate.update("UPDATE roles SET description = ? WHERE id = ?", "stale role metadata", coordId);
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ?, description = ? WHERE id = ?",
                    "stale permission label",
                    "stale permission description",
                    permissionGetId
            );

            invokeSeed();

            assertThat(activeId("roles", "name", "COORD")).isEqualTo(coordId);
            assertThat(activeId("permissions", "code", "PERMISSION_GET")).isEqualTo(permissionGetId);
            assertThat(activeText("roles", "description", "name", "COORD"))
                    .isEqualTo("Coordinator access to GAM operational administration");
            assertThat(activeText("permissions", "label", "code", "PERMISSION_GET"))
                    .isEqualTo("View permissions");
            assertThat(activeText("permissions", "description", "code", "PERMISSION_GET"))
                    .isEqualTo("Allows reading permission catalog entries");
        } finally {
            jdbcTemplate.update("UPDATE roles SET description = ? WHERE id = ?", originalRoleDescription, coordId);
            jdbcTemplate.update(
                    "UPDATE permissions SET label = ?, description = ? WHERE id = ?",
                    originalPermissionLabel,
                    originalPermissionDescription,
                    permissionGetId
            );
        }
    }

    @Test
    @DisplayName("REQ-RBAC-005 - records outside the current registry are retained by repeatable seeding")
    void recordsOutsideCurrentRegistryShouldRemainAfterSeeding() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        try {
            jdbcTemplate.update(
                    "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                            + "VALUES (?, ?, ?, FALSE, ?, ?)",
                    roleId,
                    "PRESERVED_" + shortId(roleId),
                    "Preserved custom role",
                    now,
                    now
            );
            jdbcTemplate.update(
                    "INSERT INTO permissions (id, code, label, description, system_managed, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, FALSE, ?, ?)",
                    permissionId,
                    "PRESERVED_" + shortId(permissionId),
                    "Preserved custom permission",
                    "Permission retained for explicit maintenance",
                    now,
                    now
            );
            jdbcTemplate.update(
                    "INSERT INTO role_permissions (id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?)",
                    linkId,
                    roleId,
                    permissionId,
                    now
            );

            invokeSeed();

            assertThat(activeId("roles", "name", "PRESERVED_" + shortId(roleId))).isEqualTo(roleId);
            assertThat(activeId("permissions", "code", "PRESERVED_" + shortId(permissionId))).isEqualTo(permissionId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM role_permissions WHERE id = ? AND deleted_at IS NULL",
                    Long.class,
                    linkId
            )).isEqualTo(1L);
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE id = ?", linkId);
            jdbcTemplate.update("DELETE FROM permissions WHERE id = ?", permissionId);
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        }
    }

    private void invokeSeed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new R__SeedPermissionsAndRoles().migrate(context);
        }
    }

    private Set<String> activeRoleNames() {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT name FROM roles WHERE deleted_at IS NULL",
                (rs, rowNum) -> rs.getString("name")
        ));
    }

    private Set<String> systemManagedRoleNames() {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT name FROM roles WHERE deleted_at IS NULL AND system_managed = TRUE",
                (rs, rowNum) -> rs.getString("name")
        ));
    }

    private Set<String> activePermissionCodes() {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT code FROM permissions WHERE deleted_at IS NULL",
                (rs, rowNum) -> rs.getString("code")
        ));
    }

    private Set<String> systemManagedPermissionCodes() {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT code FROM permissions WHERE deleted_at IS NULL AND system_managed = TRUE",
                (rs, rowNum) -> rs.getString("code")
        ));
    }

    private Set<String> activePermissionCodesForRole(String roleName) {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT p.code "
                        + "FROM role_permissions rp "
                        + "JOIN roles r ON r.id = rp.role_id "
                        + "JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE r.name = ? AND r.deleted_at IS NULL "
                        + "AND rp.deleted_at IS NULL AND p.deleted_at IS NULL",
                (rs, rowNum) -> rs.getString("code"),
                roleName
        ));
    }

    private Map<String, UUID> activeIdsByName(String table, String keyColumn) {
        return jdbcTemplate.query(
                "SELECT id, " + keyColumn + " FROM " + table + " WHERE deleted_at IS NULL",
                rs -> {
                    Map<String, UUID> result = new java.util.HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString(keyColumn), rs.getObject("id", UUID.class));
                    }
                    return result;
                }
        );
    }

    private UUID activeId(String table, String keyColumn, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM " + table + " WHERE " + keyColumn + " = ? AND deleted_at IS NULL",
                UUID.class,
                value
        );
    }

    private String activeText(String table, String textColumn, String keyColumn, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT " + textColumn + " FROM " + table + " WHERE " + keyColumn + " = ? AND deleted_at IS NULL",
                String.class,
                value
        );
    }

    private long activeRolePermissionCount() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE deleted_at IS NULL",
                Long.class
        ), "Expected active role-permission count");
    }

    private UUID activeRolePermissionId(UUID roleId, UUID permissionId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM role_permissions "
                        + "WHERE role_id = ? AND permission_id = ? AND deleted_at IS NULL",
                UUID.class,
                roleId,
                permissionId
        );
    }

    private List<UUID> activeRolePermissionIds(UUID roleId, UUID permissionId) {
        return jdbcTemplate.query(
                "SELECT id FROM role_permissions "
                        + "WHERE role_id = ? AND permission_id = ? AND deleted_at IS NULL",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                roleId,
                permissionId
        );
    }

    private java.time.Instant deletedAt(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM " + table + " WHERE id = ?",
                java.time.Instant.class,
                id
        );
    }

    private Instant updatedAt(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM " + table + " WHERE id = ?",
                Instant.class,
                id
        );
    }

    private boolean systemManaged(String table, UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT system_managed FROM " + table + " WHERE id = ?",
                Boolean.class,
                id
        ));
    }

    private long rowCount(String table) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class),
                "Expected row count for " + table
        );
    }

    private String requiredProperty(String name) {
        return Objects.requireNonNull(environment.getProperty(name), "Missing test property " + name);
    }

    private ConfigurableApplicationContext startApplication() {
        SpringApplication application = new SpringApplication(GamApiApplication.class);
        application.setAdditionalProfiles("test");
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=" + requiredProperty("spring.datasource.url"),
                "--spring.datasource.username=" + requiredProperty("spring.datasource.username"),
                "--spring.datasource.password=" + requiredProperty("spring.datasource.password")
        );
    }

    private List<String> duplicateActiveRolePermissionPairs() {
        return jdbcTemplate.query(
                "SELECT role_id || ':' || permission_id AS pair "
                        + "FROM role_permissions WHERE deleted_at IS NULL "
                        + "GROUP BY role_id, permission_id HAVING COUNT(*) > 1",
                (rs, rowNum) -> rs.getString("pair")
        );
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
