package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DropAccountRole {
    private final AccountRoleEntityLoader getAccountRoleInstance;
    private final AccountRoleRepository accountRoleRepo;
    private final RoleEntityLoader getRoleInstance;

    public DropAccountRole(AccountRoleEntityLoader getAccountRoleInstance, AccountRoleRepository accountRoleRepo, RoleEntityLoader getRoleInstance) {
        this.getAccountRoleInstance = getAccountRoleInstance;
        this.accountRoleRepo = accountRoleRepo;
        this.getRoleInstance = getRoleInstance;
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto) {
        AccountRoleEntity accountRoleEntity = getAccountRoleInstance.requiredByDTO(dto);

        accountRoleRepo.delete(accountRoleEntity);
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId));
    }
}
