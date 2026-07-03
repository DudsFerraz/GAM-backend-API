package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole;
import br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityLogger;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import jakarta.transaction.Transactional;
import java.util.Map;
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
    private final ActivityLogger activityLogger;

    public Activation(MemberRepository memberRepo, MemberDomainLoader getMemberInstance, MemberMapper memberMapper,
                      AddAccountRole addAccountRole, DropAccountRole dropAccountRole, ActivityLogger activityLogger) {
        this.memberRepo = memberRepo;
        this.getMemberInstance = getMemberInstance;
        this.memberMapper = memberMapper;
        this.addAccountRole = addAccountRole;
        this.dropAccountRole = dropAccountRole;
        this.activityLogger = activityLogger;
    }

    @Transactional
    public void activate(UUID memberId) {
        changeStatus(memberId, Member::activate, "MEMBER", "VISITOR", ActivityAction.MEMBER_ACTIVATED);
    }

    @Transactional
    public void deactivate(UUID memberId) {
        changeStatus(memberId, Member::deactivate, "VISITOR", "MEMBER", ActivityAction.MEMBER_DEACTIVATED);
    }


    private void changeStatus(UUID memberId, Consumer<Member> memberConsumer, String roleToAdd, String roleToRemove,
                              ActivityAction action) {
        Member member = getMemberInstance.requiredById(memberId);
        String previousStatus = member.getStatus().name();
        memberConsumer.accept(member);

        UUID accountId = member.getAccount().getId();

        addAccountRole.byRoleName(roleToAdd, accountId, false);
        dropAccountRole.byRoleName(roleToRemove, accountId, false);

        MemberEntity memberEntity = memberMapper.domainToEntity(member);
        memberRepo.save(memberEntity);

        activityLogger.log(
                action,
                ActivityTargetType.MEMBER,
                member.getId(),
                null,
                "Member status changed from " + previousStatus + " to " + member.getStatus().name(),
                Map.of(
                        "memberId", member.getId(),
                        "accountId", accountId,
                        "previousStatus", previousStatus,
                        "newStatus", member.getStatus().name(),
                        "roleAdded", roleToAdd,
                        "roleRemoved", roleToRemove
                )
        );
    }

}
