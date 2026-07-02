package br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole;

import br.org.gam.api.account.application.useCases.GetAccountInstance.GetAccountInstance;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.AccountRole.application.AccountAlreadyHasRoleException;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance.GetRoleInstance;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AddAccountRole {
    private final AccountRoleRepository accountRoleRepo;
    private final GetAccountInstance getAccountInstance;
    private final GetRoleInstance getRoleInstance;
    private final AccountRoleMapper accountRoleMapper;

    public AddAccountRole(AccountRoleRepository accountRoleRepo, GetAccountInstance getAccountInstance, GetRoleInstance getRoleInstance, AccountRoleMapper accountRoleMapper) {
        this.accountRoleRepo = accountRoleRepo;
        this.getAccountInstance = getAccountInstance;
        this.getRoleInstance = getRoleInstance;
        this.accountRoleMapper = accountRoleMapper;
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto) {

        AccountEntity account = getAccountInstance.entityById(dto.accountId());
        RoleEntity role = getRoleInstance.entityById(dto.roleId());

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
        UUID roleId = getRoleInstance.entityByName(roleName).getId();

        return byDTO(new AccountRoleDTO(accountId, roleId));
    }
}
