package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.accountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.application.RbacSafetyPolicy;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("maintenance")
public class ManageSudoRole {
    private final AccountEntityLoader accountEntityLoader;
    private final RoleEntityLoader roleEntityLoader;
    private final AccountRoleEntityLoader accountRoleEntityLoader;
    private final AccountRoleRepository accountRoleRepo;
    private final AccountRoleMapper accountRoleMapper;
    private final ActivityEvents activityEvents;
    private final RbacSafetyPolicy rbacSafetyPolicy;

    public ManageSudoRole(AccountEntityLoader accountEntityLoader, RoleEntityLoader roleEntityLoader,
                          AccountRoleEntityLoader accountRoleEntityLoader, AccountRoleRepository accountRoleRepo,
                          AccountRoleMapper accountRoleMapper, ActivityEvents activityEvents,
                          RbacSafetyPolicy rbacSafetyPolicy) {
        this.accountEntityLoader = accountEntityLoader;
        this.roleEntityLoader = roleEntityLoader;
        this.accountRoleEntityLoader = accountRoleEntityLoader;
        this.accountRoleRepo = accountRoleRepo;
        this.accountRoleMapper = accountRoleMapper;
        this.activityEvents = activityEvents;
        this.rbacSafetyPolicy = rbacSafetyPolicy;
    }

    @Transactional
    public AccountRoleRDTO assignSudo(UUID accountId, String reason) {
        String auditReason = requiredAuditReason(reason);
        AccountEntity account = accountEntityLoader.requiredById(accountId);
        RoleEntity sudoRole = roleEntityLoader.requiredByName(SystemRole.SUDO.getCode());

        if (accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), sudoRole.getId())) {
            throw ConflictException.resource(
                    "AccountRole",
                    "%s:%s".formatted(account.getId(), sudoRole.getId()),
                    String.format("Account: %s already has role: %s", account.getEmail(), sudoRole.getName())
            );
        }

        AccountRoleEntity accountRole = new AccountRoleEntity();
        accountRole.setId(UUIDGenerator.generateUUIDV7());
        accountRole.setAccount(account);
        accountRole.setRole(sudoRole);

        AccountRoleEntity savedAccountRole = accountRoleRepo.save(accountRole);
        activityEvents.accountRoleAdded(
                savedAccountRole.getId(),
                account.getId(),
                sudoRole.getId(),
                sudoRole.getName(),
                auditReason
        );

        return accountRoleMapper.entityToRDTO(savedAccountRole);
    }

    @Transactional
    public void removeSudo(UUID accountId, String reason) {
        String auditReason = requiredAuditReason(reason);
        RoleEntity sudoRole = roleEntityLoader.requiredByName(SystemRole.SUDO.getCode());
        AccountRoleDTO dto = new AccountRoleDTO(accountId, sudoRole.getId(), auditReason);
        AccountRoleEntity accountRole = accountRoleEntityLoader.requiredByDTO(dto);

        rbacSafetyPolicy.assertCanRemoveSudoThroughInternalService(accountRole);
        accountRoleRepo.delete(accountRole);
        activityEvents.accountRoleRemoved(
                accountRole.getId(),
                accountId,
                sudoRole.getId(),
                sudoRole.getName(),
                auditReason
        );
    }

    private String requiredAuditReason(String reason) {
        if (reason == null) {
            throw InvalidCommandException.reason("SUDO role changes require an audit reason.");
        }

        String normalizedReason = reason.strip();
        if (normalizedReason.isEmpty()
                || normalizedReason.codePointCount(0, normalizedReason.length()) > 2_000) {
            throw InvalidCommandException.reason("SUDO role changes require an audit reason.");
        }
        return normalizedReason;
    }
}
