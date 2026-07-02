package br.org.gam.api.member.application.useCases.Activation;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole.AddAccountRole;
import br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole.DropAccountRole;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import jakarta.transaction.Transactional;
import java.util.function.Consumer;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringActivation implements Activation {

    private final MemberRepository memberRepo;
    private final GetMemberInstance getMemberInstance;
    private final MemberMapper memberMapper;
    private final AddAccountRole addAccountRole;
    private final DropAccountRole dropAccountRole;
    public SpringActivation(MemberRepository memberRepo, GetMemberInstance getMemberInstance, MemberMapper memberMapper, AddAccountRole addAccountRole, DropAccountRole dropAccountRole) {
        this.memberRepo = memberRepo;
        this.getMemberInstance = getMemberInstance;
        this.memberMapper = memberMapper;
        this.addAccountRole = addAccountRole;
        this.dropAccountRole = dropAccountRole;
    }

    @Transactional
    @Override
    public void activate(UUID memberId) {
        changeStatus(memberId, Member::activate, "MEMBER", "VISITOR");
    }

    @Transactional
    @Override
    public void deactivate(UUID memberId) {
        changeStatus(memberId, Member::deactivate, "VISITOR", "MEMBER");
    }


    private void changeStatus(UUID memberId, Consumer<Member> memberConsumer, String roleToAdd, String roleToRemove) {
        Member member = getMemberInstance.domainById(memberId);
        memberConsumer.accept(member);

        UUID accountId = member.getAccount().getId();

        addAccountRole.byRoleName(roleToAdd, accountId);
        dropAccountRole.byRoleName(roleToRemove, accountId);

        MemberEntity memberEntity = memberMapper.domainToEntity(member);
        memberRepo.save(memberEntity);
    }

}
