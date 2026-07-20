package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.application.RbacSafetyPolicy;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.InvalidCommandException;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DropAccountRole {
    private final AccountRoleEntityLoader getAccountRoleInstance;
    private final AccountRoleRepository accountRoleRepo;
    private final RoleEntityLoader getRoleInstance;
    private final ActivityEvents activityEvents;
    private final RbacSafetyPolicy rbacSafetyPolicy;

    public DropAccountRole(AccountRoleEntityLoader getAccountRoleInstance, AccountRoleRepository accountRoleRepo,
                           RoleEntityLoader getRoleInstance, ActivityEvents activityEvents,
                           RbacSafetyPolicy rbacSafetyPolicy) {
        this.getAccountRoleInstance = getAccountRoleInstance;
        this.accountRoleRepo = accountRoleRepo;
        this.getRoleInstance = getRoleInstance;
        this.activityEvents = activityEvents;
        this.rbacSafetyPolicy = rbacSafetyPolicy;
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto) {
        String reason = requiredAuditReason(dto.reason());
        rbacSafetyPolicy.assertCanRemoveRoleThroughAdmin(getRoleInstance.requiredById(dto.roleId()));
        AccountRoleEntity accountRoleEntity = getAccountRoleInstance.requiredByDTO(dto);

        accountRoleRepo.delete(accountRoleEntity);

        String roleName = accountRoleEntity.getRole() == null ? null : accountRoleEntity.getRole().getName();
        activityEvents.accountRoleRemoved(
                accountRoleEntity.getId(),
                dto.accountId(),
                dto.roleId(),
                roleName,
                reason
        );
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId, String reason) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId, reason));
    }

    private String requiredAuditReason(String reason) {
        if (reason == null) {
            throw InvalidCommandException.reason("Account role changes require an audit reason.");
        }

        String normalizedReason = reason.strip();
        if (normalizedReason.isEmpty()
                || normalizedReason.codePointCount(0, normalizedReason.length()) > 2_000) {
            throw InvalidCommandException.reason("Account role changes require an audit reason.");
        }
        return normalizedReason;
    }
}
