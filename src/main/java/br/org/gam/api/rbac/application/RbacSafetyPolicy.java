package br.org.gam.api.rbac.application;

import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.shared.exception.ForbiddenOperationException;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RbacSafetyPolicy {
    private final AccountRoleRepository accountRoleRepo;

    public RbacSafetyPolicy(AccountRoleRepository accountRoleRepo) {
        this.accountRoleRepo = accountRoleRepo;
    }

    public void assertCanAssignRoleThroughAdmin(RoleEntity role) {
        if (role != null && role.isSystemManaged()) {
            throw ForbiddenOperationException.reason("System roles are managed by their owning lifecycle or maintenance workflow.");
        }
    }

    public void assertCanRemoveRoleThroughAdmin(AccountRoleEntity accountRole) {
        assertCanRemoveRoleThroughAdmin(accountRole.getRole());
    }

    public void assertCanRemoveRoleThroughAdmin(RoleEntity role) {
        if (role != null && role.isSystemManaged()) {
            throw ForbiddenOperationException.reason("System roles are managed by their owning lifecycle or maintenance workflow.");
        }
    }

    public void assertCanRemoveSudoThroughInternalService(AccountRoleEntity accountRole) {
        if (!isSudo(accountRole.getRole())) {
            throw ForbiddenOperationException.reason("Only SUDO role removal is allowed through this internal service.");
        }

        List<AccountRoleEntity> activeSudoRoles =
                accountRoleRepo.lockActiveAccountRolesByRoleName(SystemRole.SUDO.getCode());
        if (activeSudoRoles == null) {
            activeSudoRoles = List.of();
        }
        UUID targetAccountId = accountRole.getAccount().getId();
        UUID sudoRoleId = accountRole.getRole().getId();
        boolean targetIsActiveSudo = activeSudoRoles.stream()
                .map(AccountRoleEntity::getAccount)
                .filter(Objects::nonNull)
                .anyMatch(account -> targetAccountId.equals(account.getId()));

        if (!targetIsActiveSudo) {
            throw NotFoundException.resource(
                    "AccountRole",
                    "%s:%s".formatted(targetAccountId, sudoRoleId)
            );
        }

        if (activeSudoRoles.size() <= 1) {
            throw ForbiddenOperationException.reason("Cannot remove the last active SUDO account.");
        }
    }

    public void assertRoleCanBeManaged(RoleEntity role) {
        if (role.isSystemManaged()) {
            throw ForbiddenOperationException.reason("System-managed roles cannot be edited, deleted, or disabled.");
        }
    }

    public void assertPermissionCanBeManaged(PermissionEntity permission) {
        if (permission.isSystemManaged()) {
            throw ForbiddenOperationException.reason("System-managed permissions cannot be edited, deleted, or disabled.");
        }
    }

    public void assertRolePermissionCanBeManaged(RolePermissionEntity rolePermission) {
        RoleEntity role = rolePermission.getRole();
        if (role != null && role.isSystemManaged()) {
            throw ForbiddenOperationException.reason("System-managed role-permission links cannot be edited.");
        }
    }

    private boolean isSudo(RoleEntity role) {
        return role != null && SystemRole.SUDO.getCode().equals(role.getName());
    }

}
