package br.org.gam.api.db.migration;

import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class R__SeedPermissionsAndRoles extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());

        Map<SystemRole, UUID> roleIds = seedSystemRoles(connection, now);
        Map<PermissionEnum, UUID> permissionIds = seedSystemPermissions(connection, now);
        seedSystemRolePermissions(connection, roleIds, permissionIds, now);
    }

    private Map<SystemRole, UUID> seedSystemRoles(Connection connection, Timestamp now) throws Exception {
        try (PreparedStatement selectRoleStmt = connection.prepareStatement(
                     "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL");
             PreparedStatement insertRoleStmt = connection.prepareStatement(
                     "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                             + "VALUES (?, ?, ?, TRUE, ?, ?)");
             PreparedStatement updateRoleStmt = connection.prepareStatement(
                     "UPDATE roles SET description = ?, system_managed = TRUE, updated_at = ? WHERE id = ?")) {

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
                    updateRoleStmt.execute();
                }
                roleIds.put(role, roleId);
            }
            return roleIds;
        }
    }

    private Map<PermissionEnum, UUID> seedSystemPermissions(Connection connection, Timestamp now) throws Exception {
            try (PreparedStatement selectPermStmt = connection.prepareStatement(
                     "SELECT id FROM permissions WHERE code = ? AND deleted_at IS NULL");
             PreparedStatement insertPermStmt = connection.prepareStatement(
                     "INSERT INTO permissions (id, code, label, description, system_managed, created_at, updated_at) "
                             + "VALUES (?, ?, ?, ?, TRUE, ?, ?)");
             PreparedStatement updatePermStmt = connection.prepareStatement(
                     "UPDATE permissions SET label = ?, description = ?, system_managed = TRUE, updated_at = ? "
                             + "WHERE id = ?")) {

            java.util.HashMap<PermissionEnum, UUID> permissionIds = new java.util.HashMap<>();
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
                    updatePermStmt.execute();
                }
                permissionIds.put(permission, permissionId);
            }
            return permissionIds;
        }
    }

    private void seedSystemRolePermissions(Connection connection, Map<SystemRole, UUID> roleIds,
                                           Map<PermissionEnum, UUID> permissionIds, Timestamp now) throws Exception {
        String checkRolePermSql = "SELECT 1 FROM role_permissions "
                + "WHERE role_id = ? AND permission_id = ? AND deleted_at IS NULL";
        String insertRolePermSql = "INSERT INTO role_permissions (id, role_id, permission_id, created_at) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement checkRolePermStmt = connection.prepareStatement(checkRolePermSql);
             PreparedStatement insertRolePermStmt = connection.prepareStatement(insertRolePermSql)) {

            for (PermissionEnum permission : PermissionEnum.values()) {
                UUID permissionId = permissionIds.get(permission);

                for (SystemRole role : SystemRole.values()) {
                    if (role.includes(permission)) {
                        linkPermissionToRole(
                                roleIds.get(role),
                                permissionId,
                                now,
                                checkRolePermStmt,
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
                                      PreparedStatement checkStmt, PreparedStatement insertStmt) throws Exception {
        checkStmt.setObject(1, roleId);
        checkStmt.setObject(2, permissionId);
        try (ResultSet rs = checkStmt.executeQuery()) {
            if (!rs.next()) {
                insertStmt.setObject(1, UUIDGenerator.generateUUIDV7());
                insertStmt.setObject(2, roleId);
                insertStmt.setObject(3, permissionId);
                insertStmt.setTimestamp(4, now);
                insertStmt.execute();
            }
        }
    }
}
