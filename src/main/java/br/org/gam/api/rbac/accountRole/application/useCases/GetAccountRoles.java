package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.rbac.accountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleMapper;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetAccountRoles {
    private final AccountRoleRepository accountRoleRepo;
    private final RoleMapper roleMapper;
    private final AccountEntityLoader accountEntityLoader;

    public GetAccountRoles(AccountRoleRepository accountRoleRepo, RoleMapper roleMapper,
                           AccountEntityLoader accountEntityLoader) {
        this.accountRoleRepo = accountRoleRepo;
        this.roleMapper = roleMapper;
        this.accountEntityLoader = accountEntityLoader;
    }
    public AccountRolesRDTO get(UUID accountId) {
        if (accountEntityLoader != null) {
            accountEntityLoader.requiredById(accountId);
        }
        List<AccountRoleEntity> accountRolesEntities = accountRoleRepo.findAllByAccount_Id(accountId);

        List<RoleEntity> rolesEntities = accountRolesEntities
                .stream()
                .map(AccountRoleEntity::getRole).toList();

        List<RoleRDTO> dtosList = rolesEntities
                .stream()
                .map(roleMapper::entityToRDTO).toList();

        return new AccountRolesRDTO(dtosList);
    }
}
