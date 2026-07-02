package br.org.gam.api.member.application.useCases.GetMember;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.MemberSecurity;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.persistence.MemberEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMember {
    private final GetMemberInstance getMemberInstance;
    private final MemberMapper memberMapper;
    private final MemberSecurity memberSecurity;

    public GetMember(GetMemberInstance getMemberInstance, MemberMapper memberMapper, MemberSecurity memberSecurity) {
        this.getMemberInstance = getMemberInstance;
        this.memberMapper = memberMapper;
        this.memberSecurity = memberSecurity;
    }
    public MemberRDTO byId(UUID id) {
        MemberEntity memberEntity = getMemberInstance.entityById(id);
        if(!memberSecurity.canGetMember(memberEntity)) throw new MemberNotFoundException("Could not find member with id " + id);

        return memberMapper.entityToMemberRDTO(memberEntity);
    }

}
