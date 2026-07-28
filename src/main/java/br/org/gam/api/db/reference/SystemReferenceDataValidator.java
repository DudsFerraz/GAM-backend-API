package br.org.gam.api.db.reference;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamType;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormOrigin;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintMode;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.domain.SystemRole;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class SystemReferenceDataValidator implements InitializingBean {
    private static final Map<String, Supplier<? extends Enum<?>[]>> ENUM_MIRRORS = Map.of(
            "member_status_enum", MemberStatus::values,
            "event_type_enum", EventType::values,
            "event_status_enum", EventStatus::values,
            "membership_solicitation_status_enum", MembershipSolicitationStatus::values,
            "oratorio_team_type_enum", TeamType::values,
            "oratoriano_form_status_enum", FormStatus::values,
            "oratoriano_form_origin_enum", FormOrigin::values,
            "oratoriano_form_print_mode_enum", PrintMode::values
    );

    private final JdbcTemplate jdbcTemplate;

    public SystemReferenceDataValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        validateRoles();
        validatePermissions();
        validateRolePermissionLinks();
        validateEnumMirrors();
    }

    private void validateRoles() {
        for (SystemRole role : SystemRole.values()) {
            long persistedMatches = count(
                    "SELECT COUNT(*) FROM roles WHERE name = ?",
                    role.getCode()
            );
            long currentMatches = count(
                    "SELECT COUNT(*) FROM roles WHERE name = ? AND system_managed = TRUE "
                            + "AND deleted_at IS NULL AND description = ?",
                    role.getCode(),
                    role.getDescription()
            );
            requireSinglePersistedCurrentMatch(
                    "system Role " + role.getCode(),
                    persistedMatches,
                    currentMatches
            );
        }
    }

    private void validatePermissions() {
        for (PermissionEnum permission : PermissionEnum.values()) {
            long persistedMatches = count(
                    "SELECT COUNT(*) FROM permissions WHERE code = ?",
                    permission.getCode()
            );
            long currentMatches = count(
                    "SELECT COUNT(*) FROM permissions WHERE code = ? AND system_managed = TRUE "
                            + "AND deleted_at IS NULL AND label = ? AND description = ?",
                    permission.getCode(),
                    permission.getLabel(),
                    permission.getDescription()
            );
            requireSinglePersistedCurrentMatch(
                    "system Permission " + permission.getCode(),
                    persistedMatches,
                    currentMatches
            );
        }
    }

    private void validateRolePermissionLinks() {
        for (SystemRole role : SystemRole.values()) {
            for (PermissionEnum permission : PermissionEnum.values()) {
                if (!role.includes(permission)) {
                    continue;
                }
                long persistedMatches = count(
                        "SELECT COUNT(*) FROM role_permissions rp "
                                + "JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.name = ? AND p.code = ?",
                        role.getCode(),
                        permission.getCode()
                );
                long currentMatches = count(
                        "SELECT COUNT(*) FROM role_permissions rp "
                                + "JOIN roles r ON r.id = rp.role_id "
                                + "JOIN permissions p ON p.id = rp.permission_id "
                                + "WHERE r.name = ? AND r.system_managed = TRUE AND r.deleted_at IS NULL "
                                + "AND p.code = ? AND p.system_managed = TRUE AND p.deleted_at IS NULL "
                                + "AND rp.deleted_at IS NULL",
                        role.getCode(),
                        permission.getCode()
                );
                requireSinglePersistedCurrentMatch(
                        "system Role-Permission link " + role.getCode() + ":" + permission.getCode(),
                        persistedMatches,
                        currentMatches
                );
            }
        }
    }

    private void validateEnumMirrors() {
        ENUM_MIRRORS.forEach((databaseType, applicationValues) -> {
            Set<String> expected = Arrays.stream(applicationValues.get())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> actual = Set.copyOf(jdbcTemplate.query(
                    "SELECT enum_label.enumlabel FROM pg_type enum_type "
                            + "JOIN pg_enum enum_label ON enum_label.enumtypid = enum_type.oid "
                            + "JOIN pg_namespace enum_namespace ON enum_namespace.oid = enum_type.typnamespace "
                            + "WHERE enum_type.typname = ? AND enum_namespace.nspname = current_schema()",
                    (resultSet, rowNumber) -> resultSet.getString("enumlabel"),
                    databaseType
            ));
            if (!actual.equals(expected)) {
                throw new IllegalStateException(
                        "Database enum mirror drift for " + databaseType
                                + ": expected " + expected + " but found " + actual
                );
            }
        });
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }

    private void requireSinglePersistedCurrentMatch(
            String catalogEntry,
            long persistedMatches,
            long currentMatches
    ) {
        if (persistedMatches != 1 || currentMatches != 1) {
            throw new IllegalStateException(
                    "Mandatory reference-data drift for " + catalogEntry
                            + ": expected 1 persisted current match but found "
                            + persistedMatches + " persisted and " + currentMatches + " current"
            );
        }
    }
}
