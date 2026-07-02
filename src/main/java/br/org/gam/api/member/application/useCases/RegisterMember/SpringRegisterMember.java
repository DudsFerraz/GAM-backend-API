package br.org.gam.api.member.application.useCases.RegisterMember;

import br.org.gam.api.account.application.useCases.GetAccountInstance.GetAccountInstance;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.member.application.MemberAccountConflictException;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.domain.Name;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class SpringRegisterMember implements RegisterMember {

    private final MemberRepository memberRepo;

    private final MemberMapper memberMapper;
    private final GetAccountInstance getAccountInstance;

    public SpringRegisterMember(MemberRepository memberRepo, MemberMapper memberMapper, GetAccountInstance getAccountInstance) {
        this.memberRepo = memberRepo;
        this.memberMapper = memberMapper;
        this.getAccountInstance = getAccountInstance;
    }

    @Transactional
    @Override
    public RegisterMemberRDTO register(RegisterMemberDTO dto) {
        if (memberRepo.existsByAccountId(dto.accountId())){
            throw new MemberAccountConflictException("A member is already linked to this account.");
        }

        Account relatedAccount = getAccountInstance.domainById(dto.accountId());

        Name name = new Name(dto.firstName(), dto.surname());

        Member newMember = Member.register(relatedAccount, name, dto.birthDate(), dto.phoneNumber());

        MemberEntity newMemberEntity = memberMapper.domainToEntity(newMember);
        MemberEntity savedMemberEntity = memberRepo.save(newMemberEntity);

        return memberMapper.entityToRegisterMemberRDTO(savedMemberEntity);
    }
}
