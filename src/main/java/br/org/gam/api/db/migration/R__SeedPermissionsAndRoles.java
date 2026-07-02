package br.org.gam.api.db.migration;

import br.org.gam.api.rbac.Permission.domain.PermissionEnum;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import static br.org.gam.api.rbac.Permission.domain.PermissionEnum.*;

@Component
public class R__SeedPermissionsAndRoles extends BaseJavaMigration {
    private static final Set<PermissionEnum> COORD_PERMISSIONS = EnumSet.of(
            MEMBER_GET,
            MEMBER_SEARCH,
            MEMBER_ACTIVATION,
            MEMBER_GET_NON_ACTIVE,
            MEMBER_MANAGE,
            ACCOUNT_GET,
            ACCOUNT_SEARCH,
            EVENT_CREATE,
            EVENT_SEARCH,
            EVENT_GET_PRESENCES,
            EVENT_MANAGE,
            PRESENCES_SEARCH
    );
    private static final Set<PermissionEnum> MEMBER_PERMISSIONS = EnumSet.of(
            MEMBER_GET,
            ACCOUNT_GET,
            EVENT_SEARCH,
            EVENT_GET_PRESENCES
    );
    private static final Set<PermissionEnum> VISITOR_PERMISSIONS = EnumSet.noneOf(PermissionEnum.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());

        UUID sudoId = getRoleIdByName(connection, "SUDO");
        UUID coordId = getRoleIdByName(connection, "COORD");
        UUID memberId = getRoleIdByName(connection, "MEMBER");
        UUID visitorId = getRoleIdByName(connection, "VISITOR");

        String selectPermSql = "SELECT id FROM permissions WHERE name = ?";
        String insertPermSql = "INSERT INTO permissions (id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";

        String checkRolePermSql = "SELECT 1 FROM role_permissions WHERE role_id = ? AND permission_id = ?";
        String insertRolePermSql = "INSERT INTO role_permissions (id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?)";

        try (PreparedStatement selectPermStmt = connection.prepareStatement(selectPermSql);
             PreparedStatement insertPermStmt = connection.prepareStatement(insertPermSql);
             PreparedStatement checkRolePermStmt = connection.prepareStatement(checkRolePermSql);
             PreparedStatement insertRolePermStmt = connection.prepareStatement(insertRolePermSql)) {

            for (PermissionEnum permission : PermissionEnum.values()) {
                UUID permissionId;

                selectPermStmt.setString(1, permission.name());
                try (ResultSet rs = selectPermStmt.executeQuery()) {
                    if (rs.next()) {
                        permissionId = (UUID) rs.getObject("id");
                    } else {
                        permissionId = UUIDGenerator.generateUUIDV7();
                        insertPermStmt.setObject(1, permissionId);
                        insertPermStmt.setString(2, permission.name());
                        insertPermStmt.setString(3, permission.getDescription());
                        insertPermStmt.setTimestamp(4, now);
                        insertPermStmt.setTimestamp(5, now);
                        insertPermStmt.execute();
                    }
                }

                if (sudoId != null) {
                    linkPermissionToRole(sudoId, permissionId, now, checkRolePermStmt, insertRolePermStmt);
                }

                if (coordId != null && COORD_PERMISSIONS.contains(permission)) {
                    linkPermissionToRole(coordId, permissionId, now, checkRolePermStmt, insertRolePermStmt);
                }

                if (memberId != null && MEMBER_PERMISSIONS.contains(permission)) {
                    linkPermissionToRole(memberId, permissionId, now, checkRolePermStmt, insertRolePermStmt);
                }

                if (visitorId != null && MEMBER_PERMISSIONS.contains(permission)) {
                    linkPermissionToRole(visitorId, permissionId, now, checkRolePermStmt, insertRolePermStmt);
                }
            }
        }
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

    private UUID getRoleIdByName(Connection connection, String roleName) throws Exception {
        String sql = "SELECT id FROM roles WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roleName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
        }
        return null;
    }
}