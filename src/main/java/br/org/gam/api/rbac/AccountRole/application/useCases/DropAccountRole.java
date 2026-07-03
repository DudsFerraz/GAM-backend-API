package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityLogger;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DropAccountRole {
    private final AccountRoleEntityLoader getAccountRoleInstance;
    private final AccountRoleRepository accountRoleRepo;
    private final RoleEntityLoader getRoleInstance;
    private final ActivityLogger activityLogger;

    public DropAccountRole(AccountRoleEntityLoader getAccountRoleInstance, AccountRoleRepository accountRoleRepo,
                           RoleEntityLoader getRoleInstance, ActivityLogger activityLogger) {
        this.getAccountRoleInstance = getAccountRoleInstance;
        this.accountRoleRepo = accountRoleRepo;
        this.getRoleInstance = getRoleInstance;
        this.activityLogger = activityLogger;
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto) {
        byDTO(dto, true);
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto, boolean audit) {
        AccountRoleEntity accountRoleEntity = getAccountRoleInstance.requiredByDTO(dto);

        accountRoleRepo.delete(accountRoleEntity);

        if (audit) {
            String roleName = accountRoleEntity.getRole() == null ? null : accountRoleEntity.getRole().getName();
            activityLogger.log(
                    ActivityAction.ACCOUNT_ROLE_REMOVED,
                    ActivityTargetType.ACCOUNT_ROLE,
                    accountRoleEntity.getId(),
                    null,
                    "Role " + dto.roleId() + " removed from account " + dto.accountId(),
                    Map.of(
                            "accountId", dto.accountId(),
                            "roleId", dto.roleId(),
                            "roleName", roleName == null ? "" : roleName
                    )
            );
        }
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId) {
        byRoleName(roleName, accountId, true);
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId, boolean audit) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId), audit);
    }
}
