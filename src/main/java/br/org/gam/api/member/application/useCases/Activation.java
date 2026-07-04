package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole;
import br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.InvalidCommandException;
import jakarta.transaction.Transactional;
import java.util.function.Consumer;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class Activation {

    private final MemberRepository memberRepo;
    private final MemberDomainLoader getMemberInstance;
    private final MemberMapper memberMapper;
    private final AddAccountRole addAccountRole;
    private final DropAccountRole dropAccountRole;
    private final ActivityEvents activityEvents;

    public Activation(MemberRepository memberRepo, MemberDomainLoader getMemberInstance, MemberMapper memberMapper,
                      AddAccountRole addAccountRole, DropAccountRole dropAccountRole, ActivityEvents activityEvents) {
        this.memberRepo = memberRepo;
        this.getMemberInstance = getMemberInstance;
        this.memberMapper = memberMapper;
        this.addAccountRole = addAccountRole;
        this.dropAccountRole = dropAccountRole;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public void activate(UUID memberId) {
        MemberStatusChange change = changeStatus(memberId, Member::activate, "MEMBER", "VISITOR");
        activityEvents.memberActivated(
                change.memberId(),
                change.accountId(),
                change.previousStatus(),
                change.newStatus(),
                change.roleAdded(),
                change.roleRemoved()
        );
    }

    @Transactional
    public void deactivate(UUID memberId, String reason) {
        String auditReason = requiredAuditReason(reason);
        MemberStatusChange change = changeStatus(memberId, Member::deactivate, "VISITOR", "MEMBER");
        activityEvents.memberDeactivated(
                change.memberId(),
                change.accountId(),
                change.previousStatus(),
                change.newStatus(),
                change.roleAdded(),
                change.roleRemoved(),
                auditReason
        );
    }


    private MemberStatusChange changeStatus(UUID memberId, Consumer<Member> memberConsumer, String roleToAdd,
                                            String roleToRemove) {
        Member member = getMemberInstance.requiredById(memberId);
        String previousStatus = member.getStatus().name();
        memberConsumer.accept(member);

        UUID accountId = member.getAccount().getId();

        addAccountRole.byRoleName(roleToAdd, accountId, false);
        dropAccountRole.byRoleName(roleToRemove, accountId, false);

        MemberEntity memberEntity = memberMapper.domainToEntity(member);
        memberRepo.save(memberEntity);

        return new MemberStatusChange(
                member.getId(),
                accountId,
                previousStatus,
                member.getStatus().name(),
                roleToAdd,
                roleToRemove
        );
    }

    private record MemberStatusChange(
            UUID memberId,
            UUID accountId,
            String previousStatus,
            String newStatus,
            String roleAdded,
            String roleRemoved
    ) {
    }

    private String requiredAuditReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw InvalidCommandException.reason("Member deactivation requires an audit reason.");
        }
        return reason.trim();
    }

}
