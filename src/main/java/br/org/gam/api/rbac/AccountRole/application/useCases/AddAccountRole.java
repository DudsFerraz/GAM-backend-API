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
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityLogger;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AddAccountRole {
    private final AccountRoleRepository accountRoleRepo;
    private final AccountEntityLoader getAccountInstance;
    private final RoleEntityLoader getRoleInstance;
    private final AccountRoleMapper accountRoleMapper;
    private final ActivityLogger activityLogger;

    public AddAccountRole(AccountRoleRepository accountRoleRepo, AccountEntityLoader getAccountInstance,
                          RoleEntityLoader getRoleInstance, AccountRoleMapper accountRoleMapper,
                          ActivityLogger activityLogger) {
        this.accountRoleRepo = accountRoleRepo;
        this.getAccountInstance = getAccountInstance;
        this.getRoleInstance = getRoleInstance;
        this.accountRoleMapper = accountRoleMapper;
        this.activityLogger = activityLogger;
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto) {
        return byDTO(dto, true);
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto, boolean audit) {

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

        if (audit) {
            activityLogger.log(
                    ActivityAction.ACCOUNT_ROLE_ADDED,
                    ActivityTargetType.ACCOUNT_ROLE,
                    savedAccountRoleEntity.getId(),
                    null,
                    "Role " + role.getName() + " added to account " + account.getId(),
                    Map.of(
                            "accountId", account.getId(),
                            "roleId", role.getId(),
                            "roleName", role.getName()
                    )
            );
        }

        return accountRoleMapper.entityToRDTO(savedAccountRoleEntity);
    }

    @Transactional
    public AccountRoleRDTO byRoleName(String roleName, UUID accountId) {
        return byRoleName(roleName, accountId, true);
    }

    @Transactional
    public AccountRoleRDTO byRoleName(String roleName, UUID accountId, boolean audit) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        return byDTO(new AccountRoleDTO(accountId, roleId), audit);
    }
}
