package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.shared.exception.ForbiddenOperationException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

@Service
public class CoordinatorSafetyPolicy {
    private final AccountRoleRepository accountRoleRepository;
    private final MemberRepository memberRepository;
    private final MemberRoleProjection roleProjection;
    private final RoleEntityLoader roleEntityLoader;
    private final AuditorAware<UUID> auditorAware;

    public CoordinatorSafetyPolicy(AccountRoleRepository accountRoleRepository, MemberRepository memberRepository,
                                   MemberRoleProjection roleProjection, RoleEntityLoader roleEntityLoader,
                                   AuditorAware<UUID> auditorAware) {
        this.accountRoleRepository = accountRoleRepository;
        this.memberRepository = memberRepository;
        this.roleProjection = roleProjection;
        this.roleEntityLoader = roleEntityLoader;
        this.auditorAware = auditorAware;
    }

    public void assertCanRemoveCoordinator(UUID targetAccountId) {
        roleEntityLoader.requiredByNameForUpdate(SystemRole.COORD.getCode());
        var coordinators = accountRoleRepository.findActiveAccountRolesByRoleName(SystemRole.COORD.getCode()).stream()
                .map(AccountRoleEntity::getAccount)
                .filter(Objects::nonNull)
                .map(account -> account.getId())
                .distinct()
                .filter(this::isCurrentCoordinator)
                .toList();
        if (coordinators.size() > 1 || isActorSudo()) return;
        if (coordinators.size() == 1 && coordinators.getFirst().equals(targetAccountId)) {
            throw ForbiddenOperationException.reason("Cannot remove the final active Coordinator.");
        }
    }

    private boolean isCurrentCoordinator(UUID accountId) {
        return memberRepository.findByAccount_Id(accountId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .isPresent() && roleProjection.isActiveCoordinator(accountId);
    }

    private boolean isActorSudo() {
        return auditorAware.getCurrentAuditor()
                .map(actorId -> accountRoleRepository.existsActiveByAccountIdAndRoleName(
                        actorId, SystemRole.SUDO.getCode()))
                .orElse(false);
    }
}
