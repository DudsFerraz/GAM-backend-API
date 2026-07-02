package br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoleInstance.GetAccountRoleInstance;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance.GetRoleInstance;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DropAccountRole {
    private final GetAccountRoleInstance getAccountRoleInstance;
    private final AccountRoleRepository accountRoleRepo;
    private final GetRoleInstance getRoleInstance;

    public DropAccountRole(GetAccountRoleInstance getAccountRoleInstance, AccountRoleRepository accountRoleRepo, GetRoleInstance getRoleInstance) {
        this.getAccountRoleInstance = getAccountRoleInstance;
        this.accountRoleRepo = accountRoleRepo;
        this.getRoleInstance = getRoleInstance;
    }

    @Transactional
    public void byDTO(AccountRoleDTO dto) {
        AccountRoleEntity accountRoleEntity = getAccountRoleInstance.entityByDTO(dto);

        accountRoleRepo.delete(accountRoleEntity);
    }

    @Transactional
    public void byRoleName(String roleName, UUID accountId) {
        UUID roleId = getRoleInstance.entityByName(roleName).getId();

        byDTO(new AccountRoleDTO(accountId, roleId));
    }
}
