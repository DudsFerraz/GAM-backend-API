package br.org.gam.api.rbac;

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
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
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
     * registry self-validate. When the Draft Requirement Specification changes its accepted baseline,
     * update these sets in the same change so the exact-set assertions expose any registry drift.
     */
    private static final Set<String> BASELINE_ROLES = Set.of("SUDO", "COORD", "MEMBER", "VISITOR");

    private static final Set<String> BASELINE_PERMISSIONS = Set.of(
            "MEMBER_GET",
            "MEMBER_SEARCH",
            "MEMBER_ACTIVATION",
            "MEMBER_GET_NON_ACTIVE",
            "MEMBER_MANAGE",
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
            "ROLE_GET",
            "PERMISSION_GET"
    );

    private static final Set<String> MEMBER_PERMISSIONS = Set.of(
            "MEMBER_GET",
            "ACCOUNT_GET",
            "EVENT_SEARCH",
            "EVENT_GET_PRESENCES",
            "EVENT_GET_MEMBER",
            "GAM_LOCATION_GET"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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
        assertThat(activePermissionCodesForRole("MEMBER")).containsExactlyInAnyOrderElementsOf(MEMBER_PERMISSIONS);
        assertThat(activePermissionCodesForRole("VISITOR")).isEmpty();
        assertThat(activePermissionCodesForRole("MEMBER")).doesNotContain("ACCOUNT_ROLE_MANAGE");
        assertThat(activePermissionCodesForRole("VISITOR")).doesNotContain("ACCOUNT_ROLE_MANAGE");
        assertThat(activePermissionCodesForRole("MEMBER")).doesNotContain("COORDINATOR_MANAGE");
        assertThat(activePermissionCodesForRole("VISITOR")).doesNotContain("COORDINATOR_MANAGE");
    }

    @Test
    @DisplayName("REQ-RBAC-004 - repeated seed preserves identifiers and creates no duplicate active rows")
    void repeatedSeedShouldPreserveIdentifiersAndAvoidDuplicateRows() throws Exception {
        Map<String, UUID> roleIdsBefore = activeIdsByName("roles", "name");
        Map<String, UUID> permissionIdsBefore = activeIdsByName("permissions", "code");
        long activeRolePermissionCountBefore = activeRolePermissionCount();

        invokeSeed();

        assertThat(activeIdsByName("roles", "name")).containsExactlyInAnyOrderEntriesOf(roleIdsBefore);
        assertThat(activeIdsByName("permissions", "code")).containsExactlyInAnyOrderEntriesOf(permissionIdsBefore);
        assertThat(activeRolePermissionCount()).isEqualTo(activeRolePermissionCountBefore);
        assertThat(duplicateActiveRolePermissionPairs()).isEmpty();
    }

    @Test
    @DisplayName("REQ-RBAC-004 - soft-deleted baseline link is recreated once as an active link")
    void softDeletedBaselineLinkShouldBeRecreatedOnceAsActiveLink() throws Exception {
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
                    .doesNotContain(originalLinkId);
            UUID repairedLinkId = repairedLinks.getFirst();
            assertThat(deletedAt("role_permissions", originalLinkId)).isNotNull();

            invokeSeed();

            assertThat(activeRolePermissionIds(roleId, permissionId))
                    .containsExactly(repairedLinkId);
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
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_permissions WHERE deleted_at IS NULL",
                Long.class
        );
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
