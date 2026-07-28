package br.org.gam.api.db.migration;

import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class R__SeedPermissionsAndRoles extends BaseJavaMigration {
    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        checksum.update(registryDefinition().getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());

        preflightRegistry(connection);
        Map<SystemRole, UUID> roleIds = seedSystemRoles(connection, now);
        Map<PermissionEnum, UUID> permissionIds = seedSystemPermissions(connection, now);
        seedSystemRolePermissions(connection, roleIds, permissionIds, now);
    }

    private void preflightRegistry(Connection connection) throws Exception {
        try (PreparedStatement roleStmt = connection.prepareStatement(
                     "SELECT id, system_managed FROM roles WHERE name = ?");
             PreparedStatement permissionStmt = connection.prepareStatement(
                     "SELECT id, system_managed FROM permissions WHERE code = ?");
             PreparedStatement rolePermissionStmt = connection.prepareStatement(
                     "SELECT id FROM role_permissions WHERE role_id = ? AND permission_id = ?")) {
            Map<SystemRole, UUID> roleIds = new EnumMap<>(SystemRole.class);
            for (SystemRole role : SystemRole.values()) {
                roleIds.put(
                        role,
                        findApplicationOwnedMatch("Role", role.getCode(), roleStmt)
                );
            }
            Map<PermissionEnum, UUID> permissionIds = new EnumMap<>(PermissionEnum.class);
            for (PermissionEnum permission : PermissionEnum.values()) {
                permissionIds.put(
                        permission,
                        findApplicationOwnedMatch("Permission", permission.getCode(), permissionStmt)
                );
            }
            for (SystemRole role : SystemRole.values()) {
                for (PermissionEnum permission : PermissionEnum.values()) {
                    if (role.includes(permission)
                            && roleIds.get(role) != null
                            && permissionIds.get(permission) != null) {
                        rejectRelationshipCollision(
                                role,
                                permission,
                                roleIds.get(role),
                                permissionIds.get(permission),
                                rolePermissionStmt
                        );
                    }
                }
            }
        }
    }

    private UUID findApplicationOwnedMatch(
            String recordType,
            String stableKey,
            PreparedStatement statement
    ) throws Exception {
        statement.setString(1, stableKey);
        int matches = 0;
        boolean systemManaged = true;
        UUID id = null;
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                matches++;
                systemManaged &= resultSet.getBoolean("system_managed");
                id = resultSet.getObject("id", UUID.class);
            }
        }
        if (matches > 1 || (matches == 1 && !systemManaged)) {
            throw new IllegalStateException(
                    recordType + " reference-data collision for reserved key " + stableKey
            );
        }
        return id;
    }

    private void rejectRelationshipCollision(
            SystemRole role,
            PermissionEnum permission,
            UUID roleId,
            UUID permissionId,
            PreparedStatement statement
    ) throws Exception {
        statement.setObject(1, roleId);
        statement.setObject(2, permissionId);
        int matches = 0;
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                matches++;
            }
        }
        if (matches > 1) {
            throw new IllegalStateException(
                    "Role-Permission reference-data collision for required relationship "
                            + role.getCode() + ":" + permission.getCode()
            );
        }
    }

    private Map<SystemRole, UUID> seedSystemRoles(Connection connection, Timestamp now) throws Exception {
        try (PreparedStatement selectRoleStmt = connection.prepareStatement(
                     "SELECT id FROM roles WHERE name = ?");
             PreparedStatement insertRoleStmt = connection.prepareStatement(
                     "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                             + "VALUES (?, ?, ?, TRUE, ?, ?)");
             PreparedStatement updateRoleStmt = connection.prepareStatement(
                     "UPDATE roles SET description = ?, deleted_at = NULL, deleted_by = NULL, updated_at = ? "
                             + "WHERE id = ? AND (description IS DISTINCT FROM ? OR deleted_at IS NOT NULL)")) {

            Map<SystemRole, UUID> roleIds = new EnumMap<>(SystemRole.class);
            for (SystemRole role : SystemRole.values()) {
                UUID roleId = findRoleId(role.getCode(), selectRoleStmt);
                if (roleId == null) {
                    roleId = UUIDGenerator.generateUUIDV7();
                    insertRoleStmt.setObject(1, roleId);
                    insertRoleStmt.setString(2, role.getCode());
                    insertRoleStmt.setString(3, role.getDescription());
                    insertRoleStmt.setTimestamp(4, now);
                    insertRoleStmt.setTimestamp(5, now);
                    insertRoleStmt.execute();
                } else {
                    updateRoleStmt.setString(1, role.getDescription());
                    updateRoleStmt.setTimestamp(2, now);
                    updateRoleStmt.setObject(3, roleId);
                    updateRoleStmt.setString(4, role.getDescription());
                    updateRoleStmt.execute();
                }
                roleIds.put(role, roleId);
            }
            return roleIds;
        }
    }

    private Map<PermissionEnum, UUID> seedSystemPermissions(Connection connection, Timestamp now) throws Exception {
        try (PreparedStatement selectPermStmt = connection.prepareStatement(
                     "SELECT id FROM permissions WHERE code = ?");
             PreparedStatement insertPermStmt = connection.prepareStatement(
                     "INSERT INTO permissions (id, code, label, description, system_managed, created_at, updated_at) "
                             + "VALUES (?, ?, ?, ?, TRUE, ?, ?)");
             PreparedStatement updatePermStmt = connection.prepareStatement(
                     "UPDATE permissions SET label = ?, description = ?, deleted_at = NULL, deleted_by = NULL, "
                             + "updated_at = ? WHERE id = ? AND (label IS DISTINCT FROM ? "
                             + "OR description IS DISTINCT FROM ? OR deleted_at IS NOT NULL)")) {

            Map<PermissionEnum, UUID> permissionIds = new EnumMap<>(PermissionEnum.class);
            for (PermissionEnum permission : PermissionEnum.values()) {
                UUID permissionId = findPermissionId(permission, selectPermStmt);
                if (permissionId == null) {
                    permissionId = UUIDGenerator.generateUUIDV7();
                    insertPermStmt.setObject(1, permissionId);
                    insertPermStmt.setString(2, permission.getCode());
                    insertPermStmt.setString(3, permission.getLabel());
                    insertPermStmt.setString(4, permission.getDescription());
                    insertPermStmt.setTimestamp(5, now);
                    insertPermStmt.setTimestamp(6, now);
                    insertPermStmt.execute();
                } else {
                    updatePermStmt.setString(1, permission.getLabel());
                    updatePermStmt.setString(2, permission.getDescription());
                    updatePermStmt.setTimestamp(3, now);
                    updatePermStmt.setObject(4, permissionId);
                    updatePermStmt.setString(5, permission.getLabel());
                    updatePermStmt.setString(6, permission.getDescription());
                    updatePermStmt.execute();
                }
                permissionIds.put(permission, permissionId);
            }
            return permissionIds;
        }
    }

    private void seedSystemRolePermissions(Connection connection, Map<SystemRole, UUID> roleIds,
                                           Map<PermissionEnum, UUID> permissionIds, Timestamp now) throws Exception {
        String selectRolePermSql = "SELECT id, deleted_at FROM role_permissions "
                + "WHERE role_id = ? AND permission_id = ?";
        String restoreRolePermSql = "UPDATE role_permissions SET deleted_at = NULL, deleted_by = NULL WHERE id = ?";
        String insertRolePermSql = "INSERT INTO role_permissions (id, role_id, permission_id, created_at) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement selectRolePermStmt = connection.prepareStatement(selectRolePermSql);
             PreparedStatement restoreRolePermStmt = connection.prepareStatement(restoreRolePermSql);
             PreparedStatement insertRolePermStmt = connection.prepareStatement(insertRolePermSql)) {

            for (PermissionEnum permission : PermissionEnum.values()) {
                UUID permissionId = permissionIds.get(permission);

                for (SystemRole role : SystemRole.values()) {
                    if (role.includes(permission)) {
                        linkPermissionToRole(
                                roleIds.get(role),
                                permissionId,
                                now,
                                selectRolePermStmt,
                                restoreRolePermStmt,
                                insertRolePermStmt
                        );
                    }
                }
            }
        }
    }

    private UUID findRoleId(String roleName, PreparedStatement selectRoleStmt) throws Exception {
        selectRoleStmt.setString(1, roleName);
        try (ResultSet rs = selectRoleStmt.executeQuery()) {
            if (rs.next()) {
                return (UUID) rs.getObject("id");
            }
        }
        return null;
    }

    private UUID findPermissionId(PermissionEnum permission, PreparedStatement selectPermStmt) throws Exception {
        selectPermStmt.setString(1, permission.getCode());
        try (ResultSet rs = selectPermStmt.executeQuery()) {
            if (rs.next()) {
                return (UUID) rs.getObject("id");
            }
        }
        return null;
    }

    private void linkPermissionToRole(UUID roleId, UUID permissionId, Timestamp now,
                                      PreparedStatement selectStmt, PreparedStatement restoreStmt,
                                      PreparedStatement insertStmt) throws Exception {
        selectStmt.setObject(1, roleId);
        selectStmt.setObject(2, permissionId);
        try (ResultSet resultSet = selectStmt.executeQuery()) {
            if (resultSet.next()) {
                if (resultSet.getTimestamp("deleted_at") != null) {
                    restoreStmt.setObject(1, resultSet.getObject("id", UUID.class));
                    restoreStmt.execute();
                }
                return;
            }
        }
        insertStmt.setObject(1, UUIDGenerator.generateUUIDV7());
        insertStmt.setObject(2, roleId);
        insertStmt.setObject(3, permissionId);
        insertStmt.setTimestamp(4, now);
        insertStmt.execute();
    }

    private String registryDefinition() {
        List<String> entries = new ArrayList<>();
        for (SystemRole role : SystemRole.values()) {
            entries.add("role|" + role.getCode() + "|" + role.getDescription());
        }
        for (PermissionEnum permission : PermissionEnum.values()) {
            entries.add("permission|" + permission.getCode() + "|" + permission.getLabel()
                    + "|" + permission.getDescription());
        }
        for (SystemRole role : SystemRole.values()) {
            for (PermissionEnum permission : PermissionEnum.values()) {
                if (role.includes(permission)) {
                    entries.add("link|" + role.getCode() + "|" + permission.getCode());
                }
            }
        }
        return String.join("\n", entries);
    }
}
