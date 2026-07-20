package br.org.gam.api.member.application;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemberRoleProjection {
    private final AccountRoleRepository accountRoleRepo;
    private final RoleEntityLoader roleEntityLoader;
    private final AccountEntityLoader accountEntityLoader;

    public MemberRoleProjection(AccountRoleRepository accountRoleRepo, RoleEntityLoader roleEntityLoader,
                                AccountEntityLoader accountEntityLoader) {
        this.accountRoleRepo = accountRoleRepo;
        this.roleEntityLoader = roleEntityLoader;
        this.accountEntityLoader = accountEntityLoader;
    }

    public void assertPreMember(UUID accountId) {
        assertProjection(accountId, EnumSet.noneOf(SystemRole.class));
    }

    public void assertActiveNonCoordinator(UUID accountId) {
        assertProjection(accountId, EnumSet.of(SystemRole.MEMBER));
    }

    public void assertActiveCoordinator(UUID accountId) {
        assertProjection(accountId, EnumSet.of(SystemRole.MEMBER, SystemRole.COORD));
    }

    public void assertInactive(UUID accountId) {
        assertProjection(accountId, EnumSet.of(SystemRole.VISITOR));
    }

    public boolean isActiveCoordinator(UUID accountId) {
        return lifecycleRoles(accountId).equals(EnumSet.of(SystemRole.MEMBER, SystemRole.COORD));
    }

    public RoleChange synchronizeActive(UUID accountId) {
        AccountEntity account = accountEntityLoader.requiredById(accountId);
        UUID memberRoleId = add(account, SystemRole.MEMBER);
        UUID visitorRoleId = remove(account, SystemRole.VISITOR);
        UUID coordRoleId = remove(account, SystemRole.COORD);
        return new RoleChange(memberRoleId, visitorRoleId, coordRoleId);
    }

    public RoleChange synchronizeInactive(UUID accountId) {
        AccountEntity account = accountEntityLoader.requiredById(accountId);
        UUID visitorRoleId = add(account, SystemRole.VISITOR);
        UUID memberRoleId = remove(account, SystemRole.MEMBER);
        UUID coordRoleId = remove(account, SystemRole.COORD);
        return new RoleChange(visitorRoleId, memberRoleId, coordRoleId);
    }

    public UUID grantCoordinator(UUID accountId) {
        return add(accountEntityLoader.requiredById(accountId), SystemRole.COORD);
    }

    public UUID revokeCoordinator(UUID accountId) {
        return remove(accountEntityLoader.requiredById(accountId), SystemRole.COORD);
    }

    private void assertProjection(UUID accountId, Set<SystemRole> expected) {
        if (!lifecycleRoles(accountId).equals(expected)) {
            throw ConflictException.resource(
                    "Member", accountId, "The Account has an inconsistent Member lifecycle Role projection."
            );
        }
    }

    private EnumSet<SystemRole> lifecycleRoles(UUID accountId) {
        return accountRoleRepo.findAllByAccount_Id(accountId).stream()
                .map(AccountRoleEntity::getRole)
                .filter(role -> role != null && role.isSystemManaged())
                .map(role -> SystemRole.fromCode(role.getName()).orElse(null))
                .filter(role -> role == SystemRole.MEMBER || role == SystemRole.VISITOR || role == SystemRole.COORD)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SystemRole.class)));
    }

    private UUID add(AccountEntity account, SystemRole systemRole) {
        RoleEntity role = roleEntityLoader.requiredByName(systemRole.getCode());
        if (!accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())) {
            AccountRoleEntity assignment = new AccountRoleEntity();
            assignment.setId(UUIDGenerator.generateUUIDV7());
            assignment.setAccount(account);
            assignment.setRole(role);
            accountRoleRepo.save(assignment);
            return role.getId();
        }
        return null;
    }

    private UUID remove(AccountEntity account, SystemRole systemRole) {
        RoleEntity role = roleEntityLoader.requiredByName(systemRole.getCode());
        return accountRoleRepo.findByAccount_IdAndRole_Id(account.getId(), role.getId())
                .map(assignment -> {
                    accountRoleRepo.delete(assignment);
                    return role.getId();
                })
                .orElse(null);
    }

    public record RoleChange(UUID roleAddedId, UUID roleRemovedId, UUID additionallyRemovedRoleId) {
        public RoleChange(UUID roleAddedId, UUID roleRemovedId) {
            this(roleAddedId, roleRemovedId, null);
        }
    }
}
