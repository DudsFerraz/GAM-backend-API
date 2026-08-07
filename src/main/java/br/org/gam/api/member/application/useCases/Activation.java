package br.org.gam.api.member.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.member.application.CoordinatorSafetyPolicy;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRoleProjection;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.validation.RequiredReason;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class Activation {
    private final MemberRepository memberRepo;
    private final MemberEntityLoader memberEntityLoader;
    private final MemberMapper memberMapper;
    private final MemberRoleProjection roleProjection;
    private final AccountEntityLoader accountEntityLoader;
    private final CoordinatorSafetyPolicy coordinatorSafetyPolicy;
    private final ActivityEvents activityEvents;
    private final MembershipSolicitationRepository solicitationRepository;
    private final AccountMapper accountMapper;

    public Activation(MemberRepository memberRepo, MemberEntityLoader memberEntityLoader, MemberMapper memberMapper,
                      MemberRoleProjection roleProjection, AccountEntityLoader accountEntityLoader,
                      CoordinatorSafetyPolicy coordinatorSafetyPolicy, ActivityEvents activityEvents,
                      MembershipSolicitationRepository solicitationRepository, AccountMapper accountMapper) {
        this.memberRepo = memberRepo;
        this.memberEntityLoader = memberEntityLoader;
        this.memberMapper = memberMapper;
        this.roleProjection = roleProjection;
        this.accountEntityLoader = accountEntityLoader;
        this.coordinatorSafetyPolicy = coordinatorSafetyPolicy;
        this.activityEvents = activityEvents;
        this.solicitationRepository = solicitationRepository;
        this.accountMapper = accountMapper;
    }

    @Transactional
    public void linkAccount(UUID memberId, LinkMemberAccountDTO command) {
        String reason = RequiredReason.normalize(command.reason(), "Member Account linking requires an audit reason.");
        MemberEntity member = memberEntityLoader.requiredByIdForUpdate(memberId);
        if (member.getAccount() != null) {
            throw ConflictException.resource("Member", memberId, "This Member already has a linked Account.");
        }

        var account = accountEntityLoader.requiredByIdForUpdate(command.accountId());
        if (memberRepo.existsByAccountId(account.getId())) {
            throw ConflictException.resource("Account", account.getId(), "This Account already has a lifetime Member.");
        }
        if (solicitationRepository.existsByAccount_IdAndStatus(
                account.getId(), MembershipSolicitationStatus.PENDING
        )) {
            throw ConflictException.resource(
                    "MembershipSolicitation",
                    account.getId(),
                    "A pending membership solicitation must be decided before linking this Account."
            );
        }

        roleProjection.assertPreMember(account.getId());
        String linkedRole = projectLifecycleRole(member.getStatus(), account.getId());
        Member aggregate = memberMapper.entityToDomain(member);
        aggregate.linkAccount(accountMapper.entityToDomain(account));
        memberRepo.saveAndFlush(MemberMapper.attachLinkedAccount(member, account));
        activityEvents.memberAccountLinked(memberId, account.getId(), linkedRole, reason);
    }

    @Transactional
    public void activate(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Member activation requires an audit reason.");
        LockedMember locked = lockedMember(memberId, MemberStatus.INACTIVE);
        Member member = locked.domain();
        UUID accountId = accountId(member);
        if (accountId != null) {
            roleProjection.assertInactive(accountId);
        }
        member.activate();
        if (accountId != null) {
            roleProjection.synchronizeActive(accountId);
        }
        save(member, locked.entity());
        activityEvents.memberActivated(memberId, accountId, "INACTIVE", "ACTIVE",
                accountId == null ? null : SystemRole.MEMBER.getCode(),
                accountId == null ? null : SystemRole.VISITOR.getCode(), auditReason);
    }

    @Transactional
    public void deactivate(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Member deactivation requires an audit reason.");
        LockedMember locked = lockedMember(memberId, MemberStatus.ACTIVE);
        Member member = locked.domain();
        UUID accountId = accountId(member);
        if (accountId == null) {
            member.deactivate();
            save(member, locked.entity());
            activityEvents.memberDeactivated(
                    memberId, null, "ACTIVE", "INACTIVE", null, null, auditReason
            );
            return;
        }
        boolean coordinator = roleProjection.isActiveCoordinator(accountId);
        if (coordinator) {
            coordinatorSafetyPolicy.assertCanRemoveCoordinator(accountId);
        } else {
            roleProjection.assertActiveNonCoordinator(accountId);
        }
        member.deactivate();
        MemberRoleProjection.RoleChange roles = roleProjection.synchronizeInactive(accountId);
        save(member, locked.entity());
        if (coordinator) {
            activityEvents.memberDeactivated(memberId, accountId, "ACTIVE", "INACTIVE",
                    SystemRole.VISITOR.getCode(), SystemRole.MEMBER.getCode(),
                    roles.additionallyRemovedRoleId(), roles.oratorioCoordinatorRemovedRoleId(), auditReason);
        } else if (roles.oratorioCoordinatorRemovedRoleId() != null) {
            activityEvents.memberDeactivated(memberId, accountId, "ACTIVE", "INACTIVE",
                    SystemRole.VISITOR.getCode(), SystemRole.MEMBER.getCode(),
                    roles.oratorioCoordinatorRemovedRoleId(), auditReason);
        } else {
            activityEvents.memberDeactivated(memberId, accountId, "ACTIVE", "INACTIVE",
                    SystemRole.VISITOR.getCode(), SystemRole.MEMBER.getCode(), auditReason);
        }
    }

    @Transactional
    public void grantCoordinator(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Coordinator transition requires an audit reason.");
        MemberEntity member = lockedActiveMemberEntity(memberId);
        UUID accountId = requiredLinkedAccountId(member, memberId);
        roleProjection.assertActiveNonCoordinator(accountId);
        UUID coordRoleId = roleProjection.grantCoordinator(accountId);
        activityEvents.coordinatorGranted(memberId, accountId, coordRoleId, auditReason);
    }

    @Transactional
    public void revokeCoordinator(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Coordinator transition requires an audit reason.");
        MemberEntity member = lockedActiveMemberEntity(memberId);
        UUID accountId = member.getAccount().getId();
        roleProjection.assertActiveCoordinator(accountId);
        coordinatorSafetyPolicy.assertCanRemoveCoordinator(accountId);
        UUID coordRoleId = roleProjection.revokeCoordinator(accountId);
        activityEvents.coordinatorRevoked(memberId, accountId, coordRoleId, auditReason);
    }

    @Transactional
    public void grantOratorioCoordinator(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(
                reason,
                "Oratorio Coordinator transition requires an audit reason."
        );
        MemberEntity member = lockedActiveMemberEntity(memberId);
        UUID accountId = requiredLinkedAccountId(member, memberId);
        roleProjection.assertActiveWithoutOratorioCoordinator(accountId);
        UUID roleId = roleProjection.grantOratorioCoordinator(accountId);
        activityEvents.oratorioCoordinatorGranted(memberId, accountId, roleId, auditReason);
    }

    @Transactional
    public void revokeOratorioCoordinator(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(
                reason,
                "Oratorio Coordinator transition requires an audit reason."
        );
        MemberEntity member = lockedActiveMemberEntity(memberId);
        UUID accountId = member.getAccount().getId();
        roleProjection.assertActiveOratorioCoordinator(accountId);
        UUID roleId = roleProjection.revokeOratorioCoordinator(accountId);
        activityEvents.oratorioCoordinatorRevoked(memberId, accountId, roleId, auditReason);
    }

    private LockedMember lockedMember(UUID memberId, MemberStatus requiredStatus) {
        MemberEntity entity = lockedMemberEntity(memberId);
        Member member = memberMapper.entityToDomain(entity);
        if (member.getStatus() != requiredStatus) {
            throw ConflictException.resource("Member", memberId, "Member is already in the requested status.");
        }
        return new LockedMember(member, entity);
    }

    private MemberEntity lockedActiveMemberEntity(UUID memberId) {
        MemberEntity entity = lockedMemberEntity(memberId);
        if (entity.getStatus() != MemberStatus.ACTIVE) {
            throw ConflictException.resource("Member", memberId, "Coordinator designation requires an active Member.");
        }
        return entity;
    }

    private MemberEntity lockedMemberEntity(UUID memberId) {
        MemberEntity entity = memberEntityLoader.requiredByIdForUpdate(memberId);
        if (entity.getAccount() != null) {
            accountEntityLoader.requiredByIdForUpdate(entity.getAccount().getId());
        }
        return entity;
    }

    private UUID accountId(Member member) {
        return member.getAccount() == null ? null : member.getAccount().getId();
    }

    private UUID requiredLinkedAccountId(MemberEntity member, UUID memberId) {
        if (member.getAccount() == null) {
            throw ConflictException.resource(
                    "Member", memberId, "Responsibility designation requires a linked Account."
            );
        }
        return member.getAccount().getId();
    }

    private String projectLifecycleRole(MemberStatus status, UUID accountId) {
        if (status == MemberStatus.ACTIVE) {
            roleProjection.synchronizeActive(accountId);
            return SystemRole.MEMBER.getCode();
        }
        if (status == MemberStatus.INACTIVE) {
            roleProjection.synchronizeInactive(accountId);
            return SystemRole.VISITOR.getCode();
        }
        throw ConflictException.resource(
                "Member", accountId, "The Member has an unsupported lifecycle status for Account linking."
        );
    }

    private void save(Member member, MemberEntity original) {
        MemberEntity mapped = memberMapper.domainToEntity(member);
        mapped.setImportBatchId(original.getImportBatchId());
        memberRepo.save(mapped);
    }

    private record LockedMember(Member domain, MemberEntity entity) {}
}
