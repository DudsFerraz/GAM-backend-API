package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
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
    private final ActivityEvents activityEvents;

    public AddAccountRole(AccountRoleRepository accountRoleRepo, AccountEntityLoader getAccountInstance,
                          RoleEntityLoader getRoleInstance, AccountRoleMapper accountRoleMapper,
                          ActivityEvents activityEvents) {
        this.accountRoleRepo = accountRoleRepo;
        this.getAccountInstance = getAccountInstance;
        this.getRoleInstance = getRoleInstance;
        this.accountRoleMapper = accountRoleMapper;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto) {
        return byDTO(dto, true);
    }

    @Transactional
    public AccountRoleRDTO byDTO(AccountRoleDTO dto, boolean audit) {
        String reason = audit ? requiredAuditReason(dto.reason()) : null;

        AccountEntity account = getAccountInstance.requiredById(dto.accountId());
        RoleEntity role = getRoleInstance.requiredById(dto.roleId());

        if (accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())) {
            throw ConflictException.resource(
                    "AccountRole",
                    "%s:%s".formatted(account.getId(), role.getId()),
                    String.format("Account: %s already has role: %s", account.getEmail(), role.getName())
            );
        }

        AccountRoleEntity newAccountRoleEntity = new AccountRoleEntity();
        newAccountRoleEntity.setId(UUIDGenerator.generateUUIDV7());
        newAccountRoleEntity.setAccount(account);
        newAccountRoleEntity.setRole(role);

        AccountRoleEntity savedAccountRoleEntity = accountRoleRepo.save(newAccountRoleEntity);

        if (audit) {
            activityEvents.accountRoleAdded(
                    savedAccountRoleEntity.getId(),
                    account.getId(),
                    role.getId(),
                    role.getName(),
                    reason
            );
        }

        return accountRoleMapper.entityToRDTO(savedAccountRoleEntity);
    }

    @Transactional
    public AccountRoleRDTO byRoleName(String roleName, UUID accountId, String reason) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        return byDTO(new AccountRoleDTO(accountId, roleId, reason), true);
    }

    @Transactional
    public AccountRoleRDTO byRoleName(String roleName, UUID accountId, boolean audit) {
        UUID roleId = getRoleInstance.requiredByName(roleName).getId();

        return byDTO(new AccountRoleDTO(accountId, roleId, null), audit);
    }

    private String requiredAuditReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw InvalidCommandException.reason("Account role changes require an audit reason.");
        }
        return reason.trim();
    }
}
