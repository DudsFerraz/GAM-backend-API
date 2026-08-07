package br.org.gam.api.rbac.role.domain;

import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

import static br.org.gam.api.rbac.permission.domain.PermissionEnum.*;

@Getter
public enum SystemRole {
    SUDO(
            "SUDO",
            "Developer-controlled unrestricted system access",
            EnumSet.allOf(PermissionEnum.class)
    ),
    COORD(
            "COORD",
            "Coordinator access to GAM operational administration",
            EnumSet.of(
                    MEMBER_GET,
                    MEMBER_SEARCH,
                    MEMBER_ACTIVATION,
                    MEMBER_GET_NON_ACTIVE,
                    MEMBER_MANAGE,
                    MEMBER_ACCOUNT_LINK,
                    MEMBER_INFORMATION_GET,
                    COORDINATOR_MANAGE,
                    ACCOUNT_GET,
                    ACCOUNT_SEARCH,
                    ACCOUNT_ROLE_MANAGE,
                    EVENT_CREATE,
                    EVENT_SEARCH,
                    EVENT_GET_PRESENCES,
                    EVENT_GET_MEMBER,
                    EVENT_GET_COORD,
                    EVENT_MANAGE,
                    GAM_LOCATION_GET,
                    GAM_LOCATION_CREATE,
                    GAM_LOCATION_MANAGE,
                    PRESENCES_SEARCH,
                    PRESENCE_REGISTER,
                    PRESENCE_EDIT,
                    PRESENCE_REMOVE,
                    ROLE_GET,
                    PERMISSION_GET
            ),
            true
    ),
    ORATORIO_COORD(
            "ORATORIO_COORD",
            "Oratorio operational responsibility for an active Member",
            EnumSet.noneOf(PermissionEnum.class),
            false
    ),
    MEMBER(
            "MEMBER",
            "Standard authenticated member access",
            withOratorioRead(
                    EnumSet.of(
                            MEMBER_GET,
                            ACCOUNT_GET,
                            EVENT_SEARCH,
                            EVENT_GET_PRESENCES,
                            EVENT_GET_MEMBER,
                            GAM_LOCATION_GET
                    )
            )
    ),
    VISITOR(
            "VISITOR",
            "No baseline permission; public visibility is represented by a null event requiredPermissionId",
            EnumSet.noneOf(PermissionEnum.class)
    );

    private final String code;
    private final String description;
    private final Set<PermissionEnum> permissions;

    SystemRole(String code, String description, Set<PermissionEnum> permissions) {
        this.code = code;
        this.description = description;
        this.permissions = Set.copyOf(permissions);
    }

    SystemRole(String code, String description, EnumSet<PermissionEnum> permissions, boolean manageCoordinators) {
        this(
                code,
                description,
                withOratorioOperations(permissions, manageCoordinators)
        );
    }

    private static EnumSet<PermissionEnum> withOratorioRead(EnumSet<PermissionEnum> permissions) {
        permissions.add(ORATORIO_GET);
        return permissions;
    }

    private static EnumSet<PermissionEnum> withOratorioOperations(
            EnumSet<PermissionEnum> permissions,
            boolean manageCoordinators
    ) {
        permissions.addAll(EnumSet.of(
                ORATORIO_GET,
                ORATORIO_CREATE,
                ORATORIO_MANAGE,
                ORATORIO_ATTENDANCE_GET,
                ORATORIO_ATTENDANCE_MANAGE,
                ORATORIANO_GET,
                ORATORIANO_REGISTER,
                ORATORIANO_MANAGE,
                ORATORIANO_FORM_GET,
                ORATORIANO_FORM_MANAGE,
                ORATORIANO_FORM_PDF_GENERATE,
                ORATORIANO_FORM_ATTACHMENT_GET
        ));
        if (manageCoordinators) {
            permissions.add(ORATORIO_COORD_MANAGE);
        }
        return permissions;
    }

    public boolean includes(PermissionEnum permission) {
        return permissions.contains(permission);
    }

    public static Optional<SystemRole> fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst();
    }
}
