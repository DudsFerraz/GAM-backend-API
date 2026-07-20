package br.org.gam.api.member.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.member.application.CoordinatorSafetyPolicy;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRoleProjection;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
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

    public Activation(MemberRepository memberRepo, MemberEntityLoader memberEntityLoader, MemberMapper memberMapper,
                      MemberRoleProjection roleProjection, AccountEntityLoader accountEntityLoader,
                      CoordinatorSafetyPolicy coordinatorSafetyPolicy, ActivityEvents activityEvents) {
        this.memberRepo = memberRepo;
        this.memberEntityLoader = memberEntityLoader;
        this.memberMapper = memberMapper;
        this.roleProjection = roleProjection;
        this.accountEntityLoader = accountEntityLoader;
        this.coordinatorSafetyPolicy = coordinatorSafetyPolicy;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public void activate(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Member activation requires an audit reason.");
        Member member = lockedMember(memberId, MemberStatus.INACTIVE);
        UUID accountId = member.getAccount().getId();
        roleProjection.assertInactive(accountId);
        member.activate();
        roleProjection.synchronizeActive(accountId);
        save(member);
        activityEvents.memberActivated(memberId, accountId, "INACTIVE", "ACTIVE",
                SystemRole.MEMBER.getCode(), SystemRole.VISITOR.getCode(), auditReason);
    }

    @Transactional
    public void deactivate(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Member deactivation requires an audit reason.");
        Member member = lockedMember(memberId, MemberStatus.ACTIVE);
        UUID accountId = member.getAccount().getId();
        boolean coordinator = roleProjection.isActiveCoordinator(accountId);
        if (coordinator) {
            coordinatorSafetyPolicy.assertCanRemoveCoordinator(accountId);
        } else {
            roleProjection.assertActiveNonCoordinator(accountId);
        }
        member.deactivate();
        MemberRoleProjection.RoleChange roles = roleProjection.synchronizeInactive(accountId);
        save(member);
        if (coordinator) {
            activityEvents.memberDeactivated(memberId, accountId, "ACTIVE", "INACTIVE",
                    SystemRole.VISITOR.getCode(), SystemRole.MEMBER.getCode(),
                    roles.additionallyRemovedRoleId(), auditReason);
        } else {
            activityEvents.memberDeactivated(memberId, accountId, "ACTIVE", "INACTIVE",
                    SystemRole.VISITOR.getCode(), SystemRole.MEMBER.getCode(), auditReason);
        }
    }

    @Transactional
    public void grantCoordinator(UUID memberId, String reason) {
        String auditReason = RequiredReason.normalize(reason, "Coordinator transition requires an audit reason.");
        MemberEntity member = lockedActiveMemberEntity(memberId);
        UUID accountId = member.getAccount().getId();
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

    private Member lockedMember(UUID memberId, MemberStatus requiredStatus) {
        MemberEntity entity = lockedMemberEntity(memberId);
        Member member = memberMapper.entityToDomain(entity);
        if (member.getStatus() != requiredStatus) {
            throw ConflictException.resource("Member", memberId, "Member is already in the requested status.");
        }
        return member;
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
        accountEntityLoader.requiredByIdForUpdate(entity.getAccount().getId());
        return entity;
    }

    private void save(Member member) {
        memberRepo.save(memberMapper.domainToEntity(member));
    }
}
