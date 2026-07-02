package br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoles;

import br.org.gam.api.rbac.AccountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.rbac.Role.application.RoleRDTO;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetAccountRoles {
    private final AccountRoleRepository accountRoleRepo;
    private final RoleMapper roleMapper;

    public GetAccountRoles(AccountRoleRepository accountRoleRepo, RoleMapper roleMapper) {
        this.accountRoleRepo = accountRoleRepo;
        this.roleMapper = roleMapper;
    }
    public AccountRolesRDTO get(UUID accountId) {
        List<AccountRoleEntity> accountRolesEntities = accountRoleRepo.findAllByAccount_Id(accountId);

        List<RoleEntity> rolesEntities = accountRolesEntities
                .stream()
                .map(AccountRoleEntity::getRole).toList();

        List<RoleRDTO> dtosList = rolesEntities
                .stream()
                .map(roleMapper::entityToRoleRDTO).toList();

        return new AccountRolesRDTO(dtosList);
    }
}
