package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
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

    public DropAccountRole(AccountRoleEntityLoader getAccountRoleInstance, AccountRoleRepository accountRoleRepo,
                           RoleEntityLoader getRoleInstance, ActivityEvents activityEvents) {
        this.getAccountRoleInstance = getAccountRoleInstance;
        this.accountRoleRepo = accountRoleRepo;
        this.getRoleInstance = getRoleInstance;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto) {
        byDTO(dto, true);
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto, boolean audit) {
        String reason = audit ? requiredAuditReason(dto.reason()) : null;
        AccountRoleEntity accountRoleEntity = getAccountRoleInstance.requiredByDTO(dto);

        accountRoleRepo.delete(accountRoleEntity);

        if (audit) {
            String roleName = accountRoleEntity.getRole() == null ? null : accountRoleEntity.getRole().getName();
            activityEvents.accountRoleRemoved(
                    accountRoleEntity.getId(),
                    dto.accountId(),
                    dto.roleId(),
                    roleName,
                    reason
            );
        }
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId, String reason) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId, reason), true);
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId, boolean audit) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId, null), audit);
    }

    private String requiredAuditReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw InvalidCommandException.reason("Account role changes require an audit reason.");
        }
        return reason.trim();
    }
}
