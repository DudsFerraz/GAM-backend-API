package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.AccountRole.application.AccountAlreadyHasRoleException;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AddAccountRole {
    private final AccountRoleRepository accountRoleRepo;
    private final AccountEntityLoader getAccountInstance;
    private final RoleEntityLoader getRoleInstance;
    private final AccountRoleMapper accountRoleMapper;

    public AddAccountRole(AccountRoleRepository accountRoleRepo, AccountEntityLoader getAccountInstance, RoleEntityLoader getRoleInstance, AccountRoleMapper accountRoleMapper) {
        this.accountRoleRepo = accountRoleRepo;
        this.getAccountInstance = getAccountInstance;
        this.getRoleInstance = getRoleInstance;
        this.accountRoleMapper = accountRoleMapper;
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto) {

        AccountEntity account = getAccountInstance.requiredById(dto.accountId());
        RoleEntity role = getRoleInstance.requiredById(dto.roleId());

        if (accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())) {
            throw new AccountAlreadyHasRoleException(
                    String.format("Account: %s already has role: %s", account.getEmail(), role.getName()));
        }

        AccountRoleEntity newAccountRoleEntity = new AccountRoleEntity();
        newAccountRoleEntity.setId(UUIDGenerator.generateUUIDV7());
        newAccountRoleEntity.setAccount(account);
        newAccountRoleEntity.setRole(role);

        AccountRoleEntity savedAccountRoleEntity = accountRoleRepo.save(newAccountRoleEntity);

        return accountRoleMapper.entityToAccountRoleRDTO(savedAccountRoleEntity);
    }

    @Transactional
    public AccountRoleRDTO byRoleName(String roleName, UUID accountId) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        return byDTO(new AccountRoleDTO(accountId, roleId));
    }
}
